package com.satquery.dto;

import com.satquery.entity.ModelTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AIModelRequest(
        @Schema(example = "ChangeFormer") @NotBlank(message = "name is required") @Size(max = 150) String name,
        @Schema(example = "CHANGE_DETECTION") @NotNull(message = "task is required") ModelTask task,
        @Schema(example = "1.0") @NotBlank(message = "version is required") @Size(max = 50) String version,
        @Schema(example = "/ai/change", description = "Optional endpoint override: absolute URL or path relative to the AI base URL")
        @Size(max = 500) String endpoint,
        @Size(max = 1000) String description,
        @Schema(example = "true") Boolean active) {
}
