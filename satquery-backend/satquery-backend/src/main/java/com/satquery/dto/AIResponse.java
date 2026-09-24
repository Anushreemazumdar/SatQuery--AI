package com.satquery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Normalised response returned by the Python AI service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AIResponse(
        String answer,
        String summary,
        Double confidence,
        String model,
        String modelVersion,
        Long processingTimeMs,
        List<AIEvidence> evidence,
        String changeMapUrl,
        List<String> findings) {
}
