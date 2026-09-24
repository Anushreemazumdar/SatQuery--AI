package com.satquery.service;

import com.satquery.entity.AnalysisType;
import com.satquery.entity.ImageType;
import com.satquery.entity.SatelliteImage;
import com.satquery.exception.InvalidAnalysisRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisValidatorTest {

    private final AnalysisValidator validator = new AnalysisValidator();

    private SatelliteImage image(long id, ImageType type) {
        return SatelliteImage.builder().id(id).imageType(type).build();
    }

    @Test
    void changeDetectionRequiresExactlyTwoImages() {
        assertThrows(InvalidAnalysisRequestException.class,
                () -> validator.validate(AnalysisType.CHANGE_DETECTION, List.of(image(1, ImageType.OPTICAL))));
        assertThrows(InvalidAnalysisRequestException.class,
                () -> validator.validate(AnalysisType.CHANGE_DETECTION,
                        List.of(image(1, ImageType.OPTICAL), image(2, ImageType.OPTICAL), image(3, ImageType.OPTICAL))));
        assertDoesNotThrow(() -> validator.validate(AnalysisType.CHANGE_DETECTION,
                List.of(image(1, ImageType.OPTICAL), image(2, ImageType.OPTICAL))));
    }

    @Test
    void singleImageTasksNeedAtLeastOneImage() {
        for (AnalysisType type : List.of(AnalysisType.VQA, AnalysisType.CAPTION, AnalysisType.GROUNDING)) {
            assertThrows(InvalidAnalysisRequestException.class, () -> validator.validate(type, List.of()));
            assertDoesNotThrow(() -> validator.validate(type, List.of(image(1, ImageType.OPTICAL))));
        }
    }

    @Test
    void opticalSarRequiresOneOpticalAndOneSar() {
        assertThrows(InvalidAnalysisRequestException.class,
                () -> validator.validate(AnalysisType.OPTICAL_SAR,
                        List.of(image(1, ImageType.OPTICAL), image(2, ImageType.OPTICAL))));
        assertThrows(InvalidAnalysisRequestException.class,
                () -> validator.validate(AnalysisType.OPTICAL_SAR,
                        List.of(image(1, ImageType.SAR), image(2, ImageType.SAR))));
        assertDoesNotThrow(() -> validator.validate(AnalysisType.OPTICAL_SAR,
                List.of(image(1, ImageType.SAR), image(2, ImageType.MULTISPECTRAL))));
    }

    @Test
    void autoMustBeResolvedFirst() {
        assertThrows(InvalidAnalysisRequestException.class,
                () -> validator.validate(AnalysisType.AUTO, List.of(image(1, ImageType.OPTICAL))));
    }

    @Test
    void opticalImageIsOrderedFirst() {
        List<SatelliteImage> ordered = validator.orderOpticalFirst(
                List.of(image(1, ImageType.SAR), image(2, ImageType.OPTICAL)));
        assertEquals(2L, ordered.get(0).getId());
        assertEquals(1L, ordered.get(1).getId());
    }
}
