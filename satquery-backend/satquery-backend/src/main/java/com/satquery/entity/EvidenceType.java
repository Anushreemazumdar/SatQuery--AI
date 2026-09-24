package com.satquery.entity;

import java.util.Locale;

public enum EvidenceType {
    CHANGED_REGION, GROUNDING_BOX, TEMPORAL_COMPARISON, MODEL_EVIDENCE, OTHER;

    /** Lenient parsing of the evidence type reported by the AI service. Unknown values become OTHER. */
    public static EvidenceType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        try {
            return EvidenceType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
