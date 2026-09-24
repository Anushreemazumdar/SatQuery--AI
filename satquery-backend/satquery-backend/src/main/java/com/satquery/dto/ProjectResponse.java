package com.satquery.dto;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        long imageCount,
        long analysisCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
