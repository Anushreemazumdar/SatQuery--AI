package com.satquery.service;

import com.satquery.dto.ProjectRequest;
import com.satquery.dto.ProjectResponse;
import com.satquery.entity.Analysis;
import com.satquery.entity.Project;
import com.satquery.entity.SatelliteImage;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.mapper.ProjectMapper;
import com.satquery.repository.AnalysisRepository;
import com.satquery.repository.ProjectRepository;
import com.satquery.repository.SatelliteImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock SatelliteImageRepository imageRepository;
    @Mock AnalysisRepository analysisRepository;
    @Mock ImageService imageService;
    @Mock AnalysisService analysisService;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository, imageRepository, analysisRepository,
                imageService, analysisService, new ProjectMapper());
    }

    @Test
    void createProjectPersistsTrimmedName() {
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        ProjectResponse response = service.createProject(new ProjectRequest("  Delhi Study  ", "desc"));

        assertEquals(1L, response.id());
        assertEquals("Delhi Study", response.name());
        assertEquals("desc", response.description());
    }

    @Test
    void getProjectThrowsWhenMissing() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getProject(99L));
    }

    @Test
    void getProjectIncludesCounts() {
        Project project = Project.builder().id(1L).name("P").build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(imageRepository.countByProjectId(1L)).thenReturn(3L);
        when(analysisRepository.countByProjectId(1L)).thenReturn(2L);

        ProjectResponse response = service.getProject(1L);

        assertEquals(3L, response.imageCount());
        assertEquals(2L, response.analysisCount());
    }

    @Test
    void updateProjectChangesFields() {
        Project project = Project.builder().id(1L).name("Old").build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = service.updateProject(1L, new ProjectRequest("New", "d"));

        assertEquals("New", response.name());
        assertEquals("d", response.description());
    }

    @Test
    void deleteProjectRemovesAnalysesImagesThenProject() {
        Project project = Project.builder().id(1L).name("P").build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(analysisRepository.findByProjectId(1L)).thenReturn(List.of(Analysis.builder().id(5L).build()));
        when(imageRepository.findByProjectIdOrderByUploadedAtDesc(1L))
                .thenReturn(List.of(SatelliteImage.builder().id(10L).build()));

        service.deleteProject(1L);

        verify(analysisService).delete(5L);
        verify(imageService).delete(10L);
        verify(projectRepository).delete(project);
    }

    @Test
    void deleteMissingProjectThrows() {
        when(projectRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteProject(2L));
        verify(projectRepository, never()).delete(any());
    }
}
