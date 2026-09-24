package com.satquery.service;

import com.satquery.entity.AnalysisType;
import com.satquery.entity.ImageType;
import com.satquery.entity.SatelliteImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisRouterTest {

    private final AnalysisRouter router = new AnalysisRouter();

    private SatelliteImage image(ImageType type) {
        return SatelliteImage.builder().id(1L).imageType(type).build();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "What objects are visible in this area?|VQA",
            "What is visible near the river?|VQA",
            "Describe this scene|CAPTION",
            "Where are the buildings?|GROUNDING",
            "Locate the airport runway|GROUNDING",
            "Show me the roads|GROUNDING",
            "Is there construction in the north?|VQA"
    })
    void singleImageRouting(String question, AnalysisType expected) {
        assertEquals(expected, router.route(question, List.of(image(ImageType.OPTICAL))));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "What changed between these images?|CHANGE_DETECTION",
            "Show the urban growth before and after|CHANGE_DETECTION",
            "Has there been any development?|CHANGE_DETECTION",
            "Tell me something|CHANGE_DETECTION"
    })
    void twoOpticalImagesPreferChangeDetection(String question, AnalysisType expected) {
        assertEquals(expected, router.route(question,
                List.of(image(ImageType.OPTICAL), image(ImageType.OPTICAL))));
    }

    @Test
    void singleImageIgnoresChangeKeywords() {
        assertEquals(AnalysisType.VQA, router.route("What changed here?", List.of(image(ImageType.OPTICAL))));
    }

    @Test
    void opticalPlusSarRoutesToOpticalSar() {
        assertEquals(AnalysisType.OPTICAL_SAR,
                router.route("Analyse these", List.of(image(ImageType.OPTICAL), image(ImageType.SAR))));
    }

    @Test
    void twoImagesWithExplicitDescribeIntentUseCaption() {
        assertEquals(AnalysisType.CAPTION,
                router.route("Describe the scene", List.of(image(ImageType.OPTICAL), image(ImageType.OPTICAL))));
    }
}
