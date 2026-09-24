package com.satquery.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.satquery.entity.EvidenceType;

public record EvidenceResponse(
        Long id,
        EvidenceType type,
        String description,
        Double confidence,
        Double x,
        Double y,
        Double width,
        Double height,
        JsonNode geometry,
        String evidenceImageUrl) {
}
