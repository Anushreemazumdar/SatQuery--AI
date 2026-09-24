package com.satquery.dto;

import com.satquery.entity.AnalysisStatus;

import java.time.LocalDateTime;

public record AnalysisStatusResponse(
        Long id,
        AnalysisStatus status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt) {
}
