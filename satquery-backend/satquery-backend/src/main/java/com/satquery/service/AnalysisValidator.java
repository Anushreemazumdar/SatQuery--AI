package com.satquery.service;

import com.satquery.entity.AnalysisType;
import com.satquery.entity.ImageType;
import com.satquery.entity.SatelliteImage;
import com.satquery.exception.InvalidAnalysisRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Validates the image selection against the (already resolved) analysis type. */
@Component
public class AnalysisValidator {

    public void validate(AnalysisType type, List<SatelliteImage> images) {
        int count = images == null ? 0 : images.size();
        switch (type) {
            case CHANGE_DETECTION -> {
                if (count != 2) {
                    throw new InvalidAnalysisRequestException(
                            "CHANGE_DETECTION requires exactly 2 images (before, after) but " + count + " were provided");
                }
            }
            case OPTICAL_SAR -> {
                if (count != 2 || !hasOneOpticalAndOneSar(images)) {
                    throw new InvalidAnalysisRequestException(
                            "OPTICAL_SAR requires exactly 2 images: one OPTICAL (or MULTISPECTRAL) image and one SAR image");
                }
            }
            case VQA, CAPTION, GROUNDING -> {
                if (count < 1) {
                    throw new InvalidAnalysisRequestException(type + " requires at least 1 image");
                }
            }
            case AUTO -> throw new InvalidAnalysisRequestException("AUTO must be resolved to a concrete analysis type first");
        }
    }

    /** Orders an OPTICAL_SAR pair as [optical, sar] so the AI service always receives a stable order. */
    public List<SatelliteImage> orderOpticalFirst(List<SatelliteImage> images) {
        List<SatelliteImage> ordered = new ArrayList<>(images);
        ordered.sort((a, b) -> Boolean.compare(a.getImageType() == ImageType.SAR, b.getImageType() == ImageType.SAR));
        return ordered;
    }

    private boolean hasOneOpticalAndOneSar(List<SatelliteImage> images) {
        long sar = images.stream().filter(i -> i.getImageType() == ImageType.SAR).count();
        long optical = images.stream()
                .filter(i -> i.getImageType() == ImageType.OPTICAL || i.getImageType() == ImageType.MULTISPECTRAL).count();
        return sar == 1 && optical == 1;
    }
}
