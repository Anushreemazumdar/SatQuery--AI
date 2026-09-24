package com.satquery.entity;

public enum ModelTask {
    VQA, CAPTION, GROUNDING, CHANGE_DETECTION, OPTICAL_SAR;

    public static ModelTask from(AnalysisType type) {
        return switch (type) {
            case VQA -> VQA;
            case CAPTION -> CAPTION;
            case GROUNDING -> GROUNDING;
            case CHANGE_DETECTION -> CHANGE_DETECTION;
            case OPTICAL_SAR -> OPTICAL_SAR;
            case AUTO -> throw new IllegalArgumentException("AUTO has no model task; it must be resolved first");
        };
    }
}
