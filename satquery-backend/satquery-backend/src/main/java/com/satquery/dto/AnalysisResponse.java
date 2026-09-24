package com.satquery.dto;

import com.satquery.entity.AnalysisStatus;
import com.satquery.entity.AnalysisType;

import java.time.LocalDateTime;
import java.util.List;

public record AnalysisResponse(
        Long id,
        Long projectId,
        String projectName,
        AnalysisStatus status,
        AnalysisType analysisType,
        AnalysisType requestedAnalysisType,
        String question,
        String answer,
        String summary,
        List<String> findings,
        Double confidence,
        ModelInfo model,
        List<EvidenceResponse> evidence,
        String changeMapUrl,
        List<ImageResponse> images,
        Long processingTimeMs,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        String errorMessage) {

    public record ModelInfo(String name, String version) {
    }
}
