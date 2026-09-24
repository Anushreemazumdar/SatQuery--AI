package com.satquery.service;

import com.satquery.entity.AnalysisType;
import com.satquery.entity.ImageType;
import com.satquery.entity.SatelliteImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Simple keyword based routing for AnalysisType.AUTO. */
@Component
@Slf4j
public class AnalysisRouter implements AnalysisRoutingStrategy {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE;

    private static final Pattern CHANGE = Pattern.compile(
            "\\b(chang(?:e|ed|es|ing)|before and after|growth|grew|develop(?:ment|ments|ed|ing)?|differences?"
                    + "|compar(?:e|ed|ison)|expansion|expanded|temporal|over time)\\b", FLAGS);

    private static final Pattern GROUNDING = Pattern.compile(
            "\\b(where|locate|location of|show me|highlight|point out|bounding box)\\b", FLAGS);

    private static final Pattern CAPTION = Pattern.compile(
            "\\b(describe|description|caption|summari[sz]e|summary|overview)\\b", FLAGS);

    private static final Pattern VQA = Pattern.compile(
            "\\b(what is visible|what objects|what can you see|how many|is there|are there)\\b", FLAGS);

    @Override
    public AnalysisType route(String question, List<SatelliteImage> images) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        int imageCount = images == null ? 0 : images.size();

        AnalysisType result;
        if (imageCount == 2) {
            result = routePair(q, images);
        } else {
            result = routeSingle(q);
        }
        log.info("AUTO routing: question='{}' images={} -> {}", question, imageCount, result);
        return result;
    }

    private AnalysisType routePair(String q, List<SatelliteImage> images) {
        boolean hasSar = images.stream().anyMatch(i -> i.getImageType() == ImageType.SAR);
        boolean hasOptical = images.stream()
                .anyMatch(i -> i.getImageType() == ImageType.OPTICAL || i.getImageType() == ImageType.MULTISPECTRAL);
        if (hasSar && hasOptical) {
            return AnalysisType.OPTICAL_SAR;
        }
        if (CHANGE.matcher(q).find()) {
            return AnalysisType.CHANGE_DETECTION;
        }
        boolean explicitSingleImageIntent = GROUNDING.matcher(q).find() || CAPTION.matcher(q).find() || VQA.matcher(q).find();
        if (!explicitSingleImageIntent) {
            // Two images of the same modality and no other clear intent: prefer temporal / change analysis.
            return AnalysisType.CHANGE_DETECTION;
        }
        return routeSingle(q);
    }

    private AnalysisType routeSingle(String q) {
        if (GROUNDING.matcher(q).find()) {
            return AnalysisType.GROUNDING;
        }
        if (CAPTION.matcher(q).find()) {
            return AnalysisType.CAPTION;
        }
        return AnalysisType.VQA;
    }
}
