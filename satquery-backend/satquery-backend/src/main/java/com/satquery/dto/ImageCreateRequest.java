package com.satquery.dto;

import com.satquery.entity.ImageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record ImageCreateRequest(
        @Schema(example = "1") @NotNull(message = "projectId is required") Long projectId,
        @Schema(example = "delhi_2020.tif") @NotBlank(message = "fileName is required") @Size(max = 255) String fileName,
        @Schema(example = "https://storage.example.com/delhi_2020.tif") @NotBlank(message = "fileUrl is required") @Size(max = 1000) String fileUrl,
        @Schema(example = "OPTICAL") @NotNull(message = "imageType is required") ImageType imageType,
        @Schema(example = "Sentinel-2") @Size(max = 100) String satellite,
        @Schema(example = "2020-03-15") LocalDate acquisitionDate,
        @Schema(example = "28.6139") @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @Schema(example = "77.2090") @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Schema(example = "10.0", description = "Ground resolution in metres per pixel") @Positive Double resolution) {
}
