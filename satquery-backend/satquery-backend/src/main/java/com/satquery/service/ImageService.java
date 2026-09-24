package com.satquery.service;

import com.satquery.dto.ImageCreateRequest;
import com.satquery.dto.ImageResponse;
import com.satquery.entity.ImageType;
import com.satquery.entity.Project;
import com.satquery.entity.SatelliteImage;
import com.satquery.exception.InvalidImageException;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.exception.StorageException;
import com.satquery.mapper.ImageMapper;
import com.satquery.repository.AnalysisRepository;
import com.satquery.repository.ProjectRepository;
import com.satquery.repository.SatelliteImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class ImageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "tif", "tiff", "webp", "jp2");

    private final SatelliteImageRepository imageRepository;
    private final ProjectRepository projectRepository;
    private final AnalysisRepository analysisRepository;
    private final StorageService storageService;
    private final ImageMapper imageMapper;

    public ImageService(SatelliteImageRepository imageRepository,
                        ProjectRepository projectRepository,
                        AnalysisRepository analysisRepository,
                        StorageService storageService,
                        ImageMapper imageMapper) {
        this.imageRepository = imageRepository;
        this.projectRepository = projectRepository;
        this.analysisRepository = analysisRepository;
        this.storageService = storageService;
        this.imageMapper = imageMapper;
    }

    @Transactional
    public ImageResponse upload(MultipartFile file, Long projectId, ImageType imageType, String satellite,
                                LocalDate acquisitionDate, Double latitude, Double longitude, Double resolution) {
        Project project = findProject(projectId);
        validateFile(file);
        validateGeo(latitude, longitude, resolution);

        String key = storageService.store(file, StorageService.IMAGES_DIR);
        try {
            SatelliteImage image = SatelliteImage.builder()
                    .project(project)
                    .fileName(cleanFileName(file.getOriginalFilename()))
                    .fileUrl(storageService.getUrl(key))
                    .storageKey(key)
                    .imageType(imageType)
                    .satellite(satellite)
                    .acquisitionDate(acquisitionDate)
                    .latitude(latitude)
                    .longitude(longitude)
                    .resolution(resolution)
                    .build();
            image = imageRepository.save(image);
            log.info("Uploaded image {} ('{}') to project {}", image.getId(), image.getFileName(), projectId);
            return imageMapper.toResponse(image);
        } catch (RuntimeException ex) {
            safeDelete(key);
            throw ex;
        }
    }

    @Transactional
    public ImageResponse createMetadata(ImageCreateRequest request) {
        Project project = findProject(request.projectId());
        validateGeo(request.latitude(), request.longitude(), request.resolution());
        SatelliteImage image = SatelliteImage.builder()
                .project(project)
                .fileName(request.fileName())
                .fileUrl(request.fileUrl())
                .imageType(request.imageType())
                .satellite(request.satellite())
                .acquisitionDate(request.acquisitionDate())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .resolution(request.resolution())
                .build();
        image = imageRepository.save(image);
        log.info("Registered image metadata {} for project {}", image.getId(), request.projectId());
        return imageMapper.toResponse(image);
    }

    @Transactional(readOnly = true)
    public ImageResponse getImage(Long imageId) {
        return imageMapper.toResponse(findImage(imageId));
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> listByProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id " + projectId);
        }
        return imageRepository.findByProjectIdOrderByUploadedAtDesc(projectId).stream()
                .map(imageMapper::toResponse).toList();
    }

    @Transactional
    public void delete(Long imageId) {
        SatelliteImage image = findImage(imageId);
        if (analysisRepository.existsByInputImagesId(imageId)) {
            throw new InvalidImageException("Image " + imageId + " is used by existing analyses. Delete those analyses first.");
        }
        imageRepository.delete(image);
        if (image.getStorageKey() != null) {
            safeDelete(image.getStorageKey());
        }
        log.info("Deleted image {}", imageId);
    }

    // ------------------------------------------------------------------ helpers

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + projectId));
    }

    private SatelliteImage findImage(Long imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with id " + imageId));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("Uploaded file is empty");
        }
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new InvalidImageException("Unsupported image format. Allowed extensions: " + ALLOWED_EXTENSIONS);
        }
    }

    private void validateGeo(Double latitude, Double longitude, Double resolution) {
        if (latitude != null && (latitude < -90 || latitude > 90)) {
            throw new InvalidImageException("latitude must be between -90 and 90");
        }
        if (longitude != null && (longitude < -180 || longitude > 180)) {
            throw new InvalidImageException("longitude must be between -180 and 180");
        }
        if (resolution != null && resolution <= 0) {
            throw new InvalidImageException("resolution must be positive");
        }
    }

    private String cleanFileName(String original) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(original == null ? "image" : original));
        if (!StringUtils.hasText(name)) {
            name = "image";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private void safeDelete(String key) {
        try {
            storageService.delete(key);
        } catch (StorageException ex) {
            log.warn("Could not delete stored file {}: {}", key, ex.getMessage());
        }
    }
}
