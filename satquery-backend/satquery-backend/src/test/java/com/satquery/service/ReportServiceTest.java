package com.satquery.service;

import com.satquery.dto.AnalysisResponse;
import com.satquery.dto.ProjectResponse;
import com.satquery.dto.ReportResponse;
import com.satquery.entity.Analysis;
import com.satquery.entity.AnalysisStatus;
import com.satquery.entity.AnalysisType;
import com.satquery.entity.Report;
import com.satquery.entity.ReportStatus;
import com.satquery.exception.InvalidAnalysisRequestException;
import com.satquery.exception.ReportGenerationException;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.repository.AnalysisRepository;
import com.satquery.repository.ReportRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock AnalysisService analysisService;
    @Mock ProjectService projectService;
    @Mock ReportGenerator reportGenerator;
    @Mock ReportRepository reportRepository;
    @Mock AnalysisRepository analysisRepository;
    @Mock StorageService storageService;
    @Mock PlatformTransactionManager txManager;

    private ReportService service;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ReportService(analysisService, projectService, reportGenerator, reportRepository,
                analysisRepository, storageService, txManager);

        Analysis ref = new Analysis();
        when(analysisRepository.getReferenceById(5L)).thenReturn(ref);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(99L);
            }
            return r;
        });
    }

    private AnalysisResponse analysis(AnalysisStatus status) {
        return new AnalysisResponse(5L, 1L, "P", status, AnalysisType.VQA, AnalysisType.VQA, "Q", "A", null, null,
                0.9, null, List.of(), null, List.of(), 10L, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    private ProjectResponse project() {
        return new ProjectResponse(1L, "P", "d", 1, 1, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void generatesReportForCompletedAnalysis() {
        when(analysisService.getAnalysis(5L)).thenReturn(analysis(AnalysisStatus.COMPLETED));
        when(projectService.getProject(1L)).thenReturn(project());
        when(reportGenerator.generate(any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(storageService.store(any(byte[].class), eq(StorageService.REPORTS_DIR), anyString()))
                .thenReturn("reports/r.pdf");
        when(storageService.getUrl("reports/r.pdf")).thenReturn("http://localhost:8080/files/reports/r.pdf");
        Report stored = Report.builder().id(99L).title("t").generatedAt(LocalDateTime.now())
                .reportStatus(ReportStatus.GENERATING).build();
        when(reportRepository.findById(99L)).thenReturn(Optional.of(stored));

        ReportResponse response = service.generateReport(5L);

        assertEquals(99L, response.id());
        assertEquals(5L, response.analysisId());
        assertEquals(ReportStatus.COMPLETED, response.reportStatus());
        assertEquals("http://localhost:8080/files/reports/r.pdf", response.fileUrl());
        assertEquals("reports/r.pdf", stored.getStorageKey());
    }

    @Test
    void rejectsAnalysisThatIsNotCompleted() {
        when(analysisService.getAnalysis(5L)).thenReturn(analysis(AnalysisStatus.FAILED));

        assertThrows(InvalidAnalysisRequestException.class, () -> service.generateReport(5L));

        verifyNoInteractions(reportGenerator, storageService);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void marksReportFailedWhenGenerationThrows() {
        when(analysisService.getAnalysis(5L)).thenReturn(analysis(AnalysisStatus.COMPLETED));
        when(projectService.getProject(1L)).thenReturn(project());
        when(reportGenerator.generate(any(), any())).thenThrow(new ReportGenerationException("boom", null));
        Report stored = Report.builder().id(99L).title("t").generatedAt(LocalDateTime.now())
                .reportStatus(ReportStatus.GENERATING).build();
        when(reportRepository.findById(99L)).thenReturn(Optional.of(stored));

        assertThrows(ReportGenerationException.class, () -> service.generateReport(5L));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, atLeastOnce()).save(captor.capture());
        assertEquals(ReportStatus.FAILED, captor.getValue().getReportStatus());
        verifyNoInteractions(storageService);
    }

    @Test
    void wrapsUnexpectedStorageErrors() {
        when(analysisService.getAnalysis(5L)).thenReturn(analysis(AnalysisStatus.COMPLETED));
        when(projectService.getProject(1L)).thenReturn(project());
        when(reportGenerator.generate(any(), any())).thenReturn(new byte[]{1});
        when(storageService.store(any(byte[].class), anyString(), anyString())).thenThrow(new RuntimeException("disk full"));
        when(reportRepository.findById(99L)).thenReturn(Optional.of(Report.builder().id(99L).build()));

        ReportGenerationException ex = assertThrows(ReportGenerationException.class, () -> service.generateReport(5L));
        assertTrue(ex.getMessage().contains("disk full"));
    }

    @Test
    void getReportNotFound() {
        when(reportRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getReport(1L));
    }
}
