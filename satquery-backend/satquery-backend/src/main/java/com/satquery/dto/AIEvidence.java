package com.satquery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AIEvidence(

        @JsonProperty("label")
        String description,

        Double x,
        Double y,
        Double width,
        Double height,

        String type,
        Double confidence,
        JsonNode geometry,
        String evidenceImageUrl
) {
}