package com.satquery.mapper;

import com.satquery.dto.ProjectResponse;
import com.satquery.entity.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public ProjectResponse toResponse(Project project, long imageCount, long analysisCount) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                imageCount,
                analysisCount,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
