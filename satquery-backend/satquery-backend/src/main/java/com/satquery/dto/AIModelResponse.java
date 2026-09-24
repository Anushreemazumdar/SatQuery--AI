package com.satquery.dto;

import com.satquery.entity.ModelTask;

import java.time.LocalDateTime;

public record AIModelResponse(
        Long id,
        String name,
        ModelTask task,
        String version,
        String endpoint,
        String description,
        boolean active,
        LocalDateTime createdAt) {
}
