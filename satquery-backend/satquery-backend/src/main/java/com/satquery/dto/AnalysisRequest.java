package com.satquery.dto;

import com.satquery.entity.AnalysisType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AnalysisRequest(
        @Schema(example = "1") @NotNull(message = "projectId is required") Long projectId,

        @Schema(example = "[10, 11]", description = "One image, or two images (before, after) for temporal / multi-sensor analysis")
        @NotEmpty(message = "imageIds must contain at least one image id")
        List<@NotNull(message = "imageIds must not contain null") Long> imageIds,

        @Schema(example = "CHANGE_DETECTION") @NotNull(message = "analysisType is required") AnalysisType analysisType,

        @Schema(example = "What changed between these two images?")
        @NotBlank(message = "question is required")
        @Size(max = 2000, message = "question must be at most 2000 characters")
        String question) {
}
