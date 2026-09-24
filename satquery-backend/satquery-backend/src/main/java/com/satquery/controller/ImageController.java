package com.satquery.controller;

import com.satquery.dto.ImageCreateRequest;
import com.satquery.dto.ImageResponse;
import com.satquery.entity.ImageType;
import com.satquery.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/images")
@Tag(name = "Images", description = "Upload or register satellite images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a satellite image",
            description = "multipart/form-data upload. The file is stored via the StorageService (local disk by default) "
                    + "and its metadata is persisted. Allowed extensions: png, jpg, jpeg, tif, tiff, webp, jp2.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Image uploaded"),
            @ApiResponse(responseCode = "400", description = "Invalid file or metadata"),
            @ApiResponse(responseCode = "404", description = "Project not found"),
            @ApiResponse(responseCode = "413", description = "File too large")
    })
    public ImageResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("projectId") Long projectId,
            @RequestParam("imageType") ImageType imageType,
            @RequestParam(value = "satellite", required = false) String satellite,
            @RequestParam(value = "acquisitionDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate acquisitionDate,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "resolution", required = false) Double resolution) {
        return imageService.upload(file, projectId, imageType, satellite, acquisitionDate, latitude, longitude, resolution);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register image metadata",
            description = "Registers an image that is already hosted somewhere (fileUrl) without uploading a file.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Image metadata created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public ImageResponse create(@Valid @RequestBody ImageCreateRequest request) {
        return imageService.createMetadata(request);
    }

    @GetMapping("/{imageId}")
    @Operation(summary = "Get image information")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image found"),
            @ApiResponse(responseCode = "404", description = "Image not found")
    })
    public ImageResponse get(@PathVariable Long imageId) {
        return imageService.getImage(imageId);
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete image", description = "Deletes the image metadata and the stored file. Images used by analyses cannot be deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Image deleted"),
            @ApiResponse(responseCode = "400", description = "Image is used by analyses"),
            @ApiResponse(responseCode = "404", description = "Image not found")
    })
    public void delete(@PathVariable Long imageId) {
        imageService.delete(imageId);
    }
}
