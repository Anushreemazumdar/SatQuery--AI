package com.satquery.service;

import com.satquery.dto.AnalysisSummaryResponse;
import com.satquery.dto.ImageResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final SatelliteImageRepository imageRepository;
    private final AnalysisRepository analysisRepository;
    private final ImageService imageService;
    private final AnalysisService analysisService;
    private final ProjectMapper projectMapper;

    public ProjectService(ProjectRepository projectRepository,
                          SatelliteImageRepository imageRepository,
                          AnalysisRepository analysisRepository,
                          ImageService imageService,
                          AnalysisService analysisService,
                          ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.imageRepository = imageRepository;
        this.analysisRepository = analysisRepository;
        this.imageService = imageService;
        this.analysisService = analysisService;
        this.projectMapper = projectMapper;
    }

    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        Project project = Project.builder()
                .name(request.name().trim())
                .description(request.description())
                .build();
        project = projectRepository.save(project);
        log.info("Created project {} ('{}')", project.getId(), project.getName());
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long projectId) {
        return toResponse(getEntity(projectId));
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectRequest request) {
        Project project = getEntity(projectId);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project = projectRepository.save(project);
        log.info("Updated project {}", projectId);
        return toResponse(project);
    }

    /** Deletes the project together with its analyses (incl. evidence/reports) and image metadata/files. */
    @Transactional
    public void deleteProject(Long projectId) {
        Project project = getEntity(projectId);
        for (Analysis analysis : analysisRepository.findByProjectId(projectId)) {
            analysisService.delete(analysis.getId());
        }
        for (SatelliteImage image : imageRepository.findByProjectIdOrderByUploadedAtDesc(projectId)) {
            imageService.delete(image.getId());
        }
        projectRepository.delete(project);
        log.info("Deleted project {}", projectId);
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> getProjectImages(Long projectId) {
        return imageService.listByProject(projectId);
    }

    @Transactional(readOnly = true)
    public List<AnalysisSummaryResponse> getProjectAnalysisHistory(Long projectId) {
        return analysisService.getHistory(projectId);
    }

    private Project getEntity(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + projectId));
    }

    private ProjectResponse toResponse(Project project) {
        return projectMapper.toResponse(project,
                imageRepository.countByProjectId(project.getId()),
                analysisRepository.countByProjectId(project.getId()));
    }
}
