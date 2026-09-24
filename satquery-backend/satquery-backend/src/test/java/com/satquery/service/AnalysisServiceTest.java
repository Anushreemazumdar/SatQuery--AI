package com.satquery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.satquery.client.AIServiceClient;
import com.satquery.dto.*;
import com.satquery.entity.*;
import com.satquery.exception.AIServiceException;
import com.satquery.exception.InvalidAnalysisRequestException;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.mapper.AnalysisMapper;
import com.satquery.mapper.ImageMapper;
import com.satquery.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock SatelliteImageRepository imageRepository;
    @Mock AnalysisRepository analysisRepository;
    @Mock AnalysisResultRepository resultRepository;
    @Mock EvidenceRepository evidenceRepository;
    @Mock ReportRepository reportRepository;
    @Mock AIModelRepository modelRepository;
    @Mock AIServiceClient aiClient;
    @Mock StorageService storageService;
    @Mock PlatformTransactionManager txManager;

    private AnalysisService service;
    private Project project;
    private Project otherProject;

    private Analysis storedAnalysis;
    private AnalysisResult storedResult;
    private final List<Evidence> storedEvidence = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        AnalysisMapper mapper = new AnalysisMapper(new ObjectMapper(), new ImageMapper());
        service = new AnalysisService(projectRepository, imageRepository, analysisRepository, resultRepository,
                evidenceRepository, reportRepository, modelRepository, aiClient, new AnalysisRouter(),
                new AnalysisValidator(), mapper, storageService, txManager);

        project = Project.builder().id(1L).name("Demo").build();
        otherProject = Project.builder().id(2L).name("Other").build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        when(analysisRepository.save(any(Analysis.class))).thenAnswer(inv -> {
            Analysis a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(101L);
            }
            storedAnalysis = a;
            return a;
        });
        when(analysisRepository.findWithDetailsById(101L)).thenAnswer(inv -> Optional.ofNullable(storedAnalysis));
        when(analysisRepository.findById(101L)).thenAnswer(inv -> Optional.ofNullable(storedAnalysis));
        when(modelRepository.findFirstByTaskAndActiveTrueOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        when(resultRepository.save(any(AnalysisResult.class))).thenAnswer(inv -> {
            storedResult = inv.getArgument(0);
            return storedResult;
        });
        when(resultRepository.findByAnalysisId(101L)).thenAnswer(inv -> Optional.ofNullable(storedResult));
        doAnswer(inv -> {
            Iterable<Evidence> items = inv.getArgument(0);
            items.forEach(storedEvidence::add);
            return storedEvidence;
        }).when(evidenceRepository).saveAll(any());
        when(evidenceRepository.findByAnalysisIdOrderByIdAsc(101L)).thenAnswer(inv -> new ArrayList<>(storedEvidence));
    }

    private SatelliteImage image(long id, Project owner, ImageType type) {
        return SatelliteImage.builder().id(id).project(owner).imageType(type)
                .fileName("img" + id + ".tif").fileUrl("http://localhost:8080/files/images/img" + id + ".tif").build();
    }

    private void givenImages(SatelliteImage... images) {
        when(imageRepository.findAllById(any())).thenReturn(List.of(images));
    }

    private AIResponse changeResponse() {
        return new AIResponse("Significant urban expansion detected.", "New structures identified.", 0.91,
                "ChangeFormer", "1.0", 4200L,
                List.of(new AIEvidence("CHANGED_REGION", "New construction", 0.94, 420.0, 180.0, 210.0, 160.0, null, null)),
                "http://maps/change.png", List.of("New road network"));
    }

    @Test
    void changeDetectionCompletesAndPersistsEverything() {
        givenImages(image(10, project, ImageType.OPTICAL), image(11, project, ImageType.OPTICAL));
        when(aiClient.runChangeDetection(any(AIRequest.class), isNull())).thenReturn(changeResponse());

        AnalysisResponse response = service.createAndExecute(new AnalysisRequest(1L, List.of(10L, 11L),
                AnalysisType.CHANGE_DETECTION, "What changed between these two images?"));

        assertEquals(101L, response.id());
        assertEquals(AnalysisStatus.COMPLETED, response.status());
        assertEquals(AnalysisType.CHANGE_DETECTION, response.analysisType());
        assertEquals("Significant urban expansion detected.", response.answer());
        assertEquals(0.91, response.confidence());
        assertEquals("ChangeFormer", response.model().name());
        assertEquals("1.0", response.model().version());
        assertEquals(4200L, response.processingTimeMs());
        assertEquals("http://maps/change.png", response.changeMapUrl());
        assertEquals(1, response.evidence().size());
        assertEquals(EvidenceType.CHANGED_REGION, response.evidence().get(0).type());
        assertEquals(420.0, response.evidence().get(0).x());
        assertEquals(List.of("New road network"), response.findings());
        assertEquals(2, response.images().size());

        ArgumentCaptor<AIRequest> captor = ArgumentCaptor.forClass(AIRequest.class);
        verify(aiClient).runChangeDetection(captor.capture(), isNull());
        assertEquals(101L, captor.getValue().analysisId());
        assertEquals(2, captor.getValue().imageUrls().size());
        assertTrue(captor.getValue().imageUrls().get(0).endsWith("img10.tif"));
        assertEquals(AnalysisType.CHANGE_DETECTION, captor.getValue().analysisType());
    }

    @Test
    void vqaUsesSingleImageEndpoint() {
        givenImages(image(10, project, ImageType.OPTICAL));
        when(aiClient.runVQA(any(AIRequest.class), any())).thenReturn(
                new AIResponse("Buildings and roads.", null, 0.8, "VQA-Model", "2", null, List.of(), null, null));

        AnalysisResponse response = service.createAndExecute(
                new AnalysisRequest(1L, List.of(10L), AnalysisType.VQA, "What objects are visible in this area?"));

        assertEquals(AnalysisStatus.COMPLETED, response.status());
        assertNotNull(response.processingTimeMs(), "falls back to measured time when the AI service omits it");
        verify(aiClient).runVQA(any(AIRequest.class), any());
        verify(aiClient, never()).runChangeDetection(any(), any());
    }

    @Test
    void autoRoutesTwoOpticalImagesToChangeDetection() {
        givenImages(image(10, project, ImageType.OPTICAL), image(11, project, ImageType.OPTICAL));
        when(aiClient.runChangeDetection(any(AIRequest.class), any())).thenReturn(changeResponse());

        AnalysisResponse response = service.createAndExecute(
                new AnalysisRequest(1L, List.of(10L, 11L), AnalysisType.AUTO, "What changed here?"));

        assertEquals(AnalysisType.CHANGE_DETECTION, response.analysisType());
        assertEquals(AnalysisType.AUTO, response.requestedAnalysisType());
    }

    @Test
    void aiFailureMarksAnalysisFailedAndDoesNotStoreResults() {
        givenImages(image(10, project, ImageType.OPTICAL));
        when(aiClient.runVQA(any(AIRequest.class), any())).thenThrow(new AIServiceException("AI service unreachable"));

        AIServiceException ex = assertThrows(AIServiceException.class, () -> service.createAndExecute(
                new AnalysisRequest(1L, List.of(10L), AnalysisType.VQA, "What is visible?")));

        assertTrue(ex.getMessage().contains("unreachable"));
        assertEquals(AnalysisStatus.FAILED, storedAnalysis.getStatus());
        assertTrue(storedAnalysis.getErrorMessage().contains("unreachable"));
        assertNotNull(storedAnalysis.getCompletedAt());
        verify(resultRepository, never()).save(any());
        verify(evidenceRepository, never()).saveAll(any());
    }

    @Test
    void unexpectedRuntimeErrorFromClientIsAlsoRecordedAsFailure() {
        givenImages(image(10, project, ImageType.OPTICAL));
        when(aiClient.runCaptioning(any(AIRequest.class), any())).thenThrow(new IllegalStateException("boom"));

        assertThrows(AIServiceException.class, () -> service.createAndExecute(
                new AnalysisRequest(1L, List.of(10L), AnalysisType.CAPTION, "Describe this")));
        assertEquals(AnalysisStatus.FAILED, storedAnalysis.getStatus());
    }

    @Test
    void unknownProjectIsRejected() {
        when(projectRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createAndExecute(
                new AnalysisRequest(9L, List.of(10L), AnalysisType.VQA, "q")));
        verifyNoInteractions(aiClient);
    }

    @Test
    void unknownImageIsRejected() {
        givenImages();
        assertThrows(ResourceNotFoundException.class, () -> service.createAndExecute(
                new AnalysisRequest(1L, List.of(10L), AnalysisType.VQA, "q")));
        verifyNoInteractions(aiClient);
    }

    @Test
    void imageFromAnotherProjectIsRejected() {
        givenImages(image(10, otherProject, ImageType.OPTICAL));
        InvalidAnalysisRequestException ex = assertThrows(InvalidAnalysisRequestException.class, () ->
                service.createAndExecute(new AnalysisRequest(1L, List.of(10L), AnalysisType.VQA, "q")));
        assertTrue(ex.getMessage().contains("does not belong"));
        verifyNoInteractions(aiClient);
    }

    @Test
    void changeDetectionWithOneImageIsRejected() {
        givenImages(image(10, project, ImageType.OPTICAL));
        assertThrows(InvalidAnalysisRequestException.class, () -> service.createAndExecute(
                new AnalysisRequest(1L, List.of(10L), AnalysisType.CHANGE_DETECTION, "What changed?")));
        verifyNoInteractions(aiClient);
    }

    @Test
    void opticalSarWithTwoOpticalImagesIsRejected() {
        givenImages(image(10, project, ImageType.OPTICAL), image(11, project, ImageType.OPTICAL));
        assertThrows(InvalidAnalysisRequestException.class, () -> service.createAndExecute(
                new AnalysisRequest(1L, List.of(10L, 11L), AnalysisType.OPTICAL_SAR, "Fuse these")));
    }

    @Test
    void opticalSarSendsOpticalImageFirst() {
        givenImages(image(10, project, ImageType.SAR), image(11, project, ImageType.OPTICAL));
        when(aiClient.runOpticalSAR(any(AIRequest.class), any())).thenReturn(
                new AIResponse("Flooded area.", null, 0.7, "Fusion", "1", 10L, List.of(), null, null));

        service.createAndExecute(new AnalysisRequest(1L, List.of(10L, 11L), AnalysisType.OPTICAL_SAR, "Flood extent?"));

        ArgumentCaptor<AIRequest> captor = ArgumentCaptor.forClass(AIRequest.class);
        verify(aiClient).runOpticalSAR(captor.capture(), any());
        assertTrue(captor.getValue().imageUrls().get(0).endsWith("img11.tif"));
    }

    @Test
    void duplicateImageIdsAreRejected() {
        assertThrows(InvalidAnalysisRequestException.class, () -> service.createAndExecute(
                new AnalysisRequest(1L, List.of(10L, 10L), AnalysisType.CHANGE_DETECTION, "q")));
    }

    @Test
    void registeredModelEndpointOverrideIsUsed() {
        givenImages(image(10, project, ImageType.OPTICAL));
        when(modelRepository.findFirstByTaskAndActiveTrueOrderByCreatedAtDesc(ModelTask.VQA))
                .thenReturn(Optional.of(AIModel.builder().name("Custom").task(ModelTask.VQA).version("3")
                        .endpoint("/custom/vqa").active(true).build()));
        when(aiClient.runVQA(any(AIRequest.class), any())).thenReturn(
                new AIResponse("ok", null, 0.5, "Custom", "3", 1L, null, null, null));

        service.createAndExecute(new AnalysisRequest(1L, List.of(10L), AnalysisType.VQA, "q"));

        verify(aiClient).runVQA(any(AIRequest.class), org.mockito.ArgumentMatchers.eq("/custom/vqa"));
    }

    @Test
    void getStatusOfMissingAnalysisThrows() {
        when(analysisRepository.findById(555L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getStatus(555L));
    }

    @Test
    void deleteRemovesDependentRowsAndReportFiles() {
        Analysis analysis = Analysis.builder().id(7L).project(project).build();
        Report report = Report.builder().id(3L).storageKey("reports/r.pdf").build();
        when(analysisRepository.findById(7L)).thenReturn(Optional.of(analysis));
        when(reportRepository.findByAnalysisId(7L)).thenReturn(List.of(report));

        service.delete(7L);

        verify(storageService).delete("reports/r.pdf");
        verify(reportRepository).deleteAll(List.of(report));
        verify(evidenceRepository).deleteByAnalysisId(7L);
        verify(resultRepository).deleteByAnalysisId(7L);
        verify(analysisRepository).delete(analysis);
    }
}
