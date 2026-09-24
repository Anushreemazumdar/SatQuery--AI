package com.satquery.controller;

import com.satquery.dto.AnalysisSummaryResponse;
import com.satquery.dto.ImageResponse;
import com.satquery.dto.ProjectRequest;
import com.satquery.dto.ProjectResponse;
import com.satquery.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Create and manage analysis projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a project", description = "Creates a new analysis project.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Project created"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
        return projectService.createProject(request);
    }

    @GetMapping
    @Operation(summary = "List projects", description = "Returns all projects, newest first, including image and analysis counts.")
    @ApiResponse(responseCode = "200", description = "List of projects")
    public List<ProjectResponse> getAll() {
        return projectService.getAllProjects();
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get a project")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project found"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public ProjectResponse get(@PathVariable Long projectId) {
        return projectService.getProject(projectId);
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update a project")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project updated"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public ProjectResponse update(@PathVariable Long projectId, @Valid @RequestBody ProjectRequest request) {
        return projectService.updateProject(projectId, request);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a project",
            description = "Deletes the project together with its images (metadata and stored files), analyses, evidence and reports.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Project deleted"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public void delete(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
    }

    @GetMapping("/{projectId}/images")
    @Operation(summary = "List project images")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Images of the project"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public List<ImageResponse> getImages(@PathVariable Long projectId) {
        return projectService.getProjectImages(projectId);
    }

    @GetMapping("/{projectId}/analysis")
    @Operation(summary = "Get project analysis history", description = "Returns all analyses of the project, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis history"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public List<AnalysisSummaryResponse> getAnalysisHistory(@PathVariable Long projectId) {
        return projectService.getProjectAnalysisHistory(projectId);
    }
}
