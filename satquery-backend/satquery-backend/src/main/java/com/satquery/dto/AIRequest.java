package com.satquery.dto;

import com.satquery.entity.AnalysisType;

import java.util.List;

/** Normalised request sent from Spring Boot to the Python AI service. */
public record AIRequest(
        Long analysisId,
        String question,
        List<String> imageUrls,
        AnalysisType analysisType) {
}
