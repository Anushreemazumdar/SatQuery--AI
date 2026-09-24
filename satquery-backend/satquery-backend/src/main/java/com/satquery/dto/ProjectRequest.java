package com.satquery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @Schema(example = "Delhi Urban Growth Study")
        @NotBlank(message = "Project name is required")
        @Size(max = 150, message = "Project name must be at most 150 characters")
        String name,

        @Schema(example = "Monitoring urban expansion around Delhi NCR")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description) {
}
