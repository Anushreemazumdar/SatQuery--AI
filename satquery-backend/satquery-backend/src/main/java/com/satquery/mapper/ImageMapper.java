package com.satquery.mapper;

import com.satquery.dto.ImageResponse;
import com.satquery.entity.SatelliteImage;
import org.springframework.stereotype.Component;

@Component
public class ImageMapper {

    public ImageResponse toResponse(SatelliteImage image) {
        return new ImageResponse(
                image.getId(),
                image.getProject().getId(),
                image.getFileName(),
                image.getFileUrl(),
                image.getImageType(),
                image.getSatellite(),
                image.getAcquisitionDate(),
                image.getLatitude(),
                image.getLongitude(),
                image.getResolution(),
                image.getUploadedAt());
    }
}
