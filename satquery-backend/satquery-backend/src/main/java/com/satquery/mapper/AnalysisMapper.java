package com.satquery.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.satquery.dto.AIEvidence;
import com.satquery.dto.AnalysisResponse;
import com.satquery.dto.AnalysisSummaryResponse;
import com.satquery.dto.EvidenceResponse;
import com.satquery.entity.Analysis;
import com.satquery.entity.AnalysisResult;
import com.satquery.entity.Evidence;
import com.satquery.entity.EvidenceType;
import com.satquery.entity.SatelliteImage;
import com.satquery.util.TextUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnalysisMapper {

    private final ObjectMapper objectMapper;
    private final ImageMapper imageMapper;

    public AnalysisMapper(ObjectMapper objectMapper, ImageMapper imageMapper) {
        this.objectMapper = objectMapper;
        this.imageMapper = imageMapper;
    }

    public AnalysisResponse toResponse(Analysis analysis, AnalysisResult result, List<Evidence> evidence) {
        AnalysisResponse.ModelInfo model = (analysis.getModelUsed() == null && analysis.getModelVersion() == null)
                ? null
                : new AnalysisResponse.ModelInfo(analysis.getModelUsed(), analysis.getModelVersion());

        return new AnalysisResponse(
                analysis.getId(),
                analysis.getProject().getId(),
                analysis.getProject().getName(),
                analysis.getStatus(),
                analysis.getAnalysisType(),
                analysis.getRequestedAnalysisType(),
                analysis.getQuestion(),
                result != null ? result.getAnswer() : null,
                result != null ? result.getSummary() : null,
                result != null ? parseFindings(result.getFindings()) : List.of(),
                analysis.getConfidence(),
                model,
                evidence.stream().map(this::toEvidenceResponse).toList(),
                result != null ? result.getChangeMapUrl() : null,
                analysis.getInputImages().stream().map(imageMapper::toResponse).toList(),
                analysis.getProcessingTimeMs(),
                analysis.getCreatedAt(),
                analysis.getCompletedAt(),
                analysis.getErrorMessage());
    }

    public AnalysisSummaryResponse toSummary(Analysis analysis, AnalysisResult result) {
        return new AnalysisSummaryResponse(
                analysis.getId(),
                analysis.getStatus(),
                analysis.getAnalysisType(),
                analysis.getQuestion(),
                result != null ? result.getAnswer() : null,
                analysis.getConfidence(),
                analysis.getModelUsed(),
                analysis.getModelVersion(),
                analysis.getProcessingTimeMs(),
                analysis.getInputImages().stream().map(SatelliteImage::getId).toList(),
                analysis.getCreatedAt(),
                analysis.getCompletedAt());
    }

    public EvidenceResponse toEvidenceResponse(Evidence e) {
        return new EvidenceResponse(
                e.getId(),
                e.getType(),
                e.getDescription(),
                e.getConfidence(),
                e.getX(),
                e.getY(),
                e.getWidth(),
                e.getHeight(),
                parseGeometry(e.getGeometry()),
                e.getEvidenceImageUrl());
    }

    public Evidence toEvidence(AIEvidence source, Analysis analysis) {
        String geometry = (source.geometry() == null || source.geometry().isNull()) ? null : source.geometry().toString();
        return Evidence.builder()
                .analysis(analysis)
                .type(EvidenceType.fromString(source.type()))
                .description(TextUtils.truncate(source.description(), 2000))
                .confidence(source.confidence())
                .x(source.x())
                .y(source.y())
                .width(source.width())
                .height(source.height())
                .geometry(geometry)
                .evidenceImageUrl(TextUtils.truncate(source.evidenceImageUrl(), 1000))
                .build();
    }

    public String serializeFindings(List<String> findings) {
        if (findings == null || findings.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException e) {
            return String.join("\n", findings);
        }
    }

    private List<String> parseFindings(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(stored, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of(stored);
        }
    }

    private JsonNode parseGeometry(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(stored);
        } catch (JsonProcessingException e) {
            return TextNode.valueOf(stored);
        }
    }
}
