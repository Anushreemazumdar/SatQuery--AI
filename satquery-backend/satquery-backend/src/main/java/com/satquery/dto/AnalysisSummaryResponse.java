package com.satquery.dto;

import com.satquery.entity.AnalysisStatus;
import com.satquery.entity.AnalysisType;

import java.time.LocalDateTime;
import java.util.List;

/** Compact representation used in analysis history lists. */
public record AnalysisSummaryResponse(
        Long id,
        AnalysisStatus status,
        AnalysisType analysisType,
        String question,
        String answer,
        Double confidence,
        String modelUsed,
        String modelVersion,
        Long processingTimeMs,
        List<Long> imageIds,
        LocalDateTime createdAt,
        LocalDateTime completedAt) {
}
