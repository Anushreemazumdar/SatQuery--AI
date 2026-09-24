package com.satquery.dto;

import com.satquery.entity.ImageType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ImageResponse(
        Long id,
        Long projectId,
        String fileName,
        String fileUrl,
        ImageType imageType,
        String satellite,
        LocalDate acquisitionDate,
        Double latitude,
        Double longitude,
        Double resolution,
        LocalDateTime uploadedAt) {
}
