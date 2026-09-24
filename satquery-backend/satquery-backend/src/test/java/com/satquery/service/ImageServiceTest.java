package com.satquery.service;

import com.satquery.dto.ImageCreateRequest;
import com.satquery.dto.ImageResponse;
import com.satquery.entity.ImageType;
import com.satquery.entity.Project;
import com.satquery.entity.SatelliteImage;
import com.satquery.exception.InvalidImageException;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.mapper.ImageMapper;
import com.satquery.repository.AnalysisRepository;
import com.satquery.repository.ProjectRepository;
import com.satquery.repository.SatelliteImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageServiceTest {

    @Mock SatelliteImageRepository imageRepository;
    @Mock ProjectRepository projectRepository;
    @Mock AnalysisRepository analysisRepository;
    @Mock StorageService storageService;

    private ImageService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ImageService(imageRepository, projectRepository, analysisRepository, storageService, new ImageMapper());
        project = Project.builder().id(1L).name("P").build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(imageRepository.save(any(SatelliteImage.class))).thenAnswer(inv -> {
            SatelliteImage i = inv.getArgument(0);
            i.setId(10L);
            return i;
        });
    }

    @Test
    void uploadStoresFileAndPersistsMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "delhi_2020.tif", "image/tiff", new byte[]{1, 2, 3});
        when(storageService.store(any(MultipartFile.class), eq("images"))).thenReturn("images/abc.tif");
        when(storageService.getUrl("images/abc.tif")).thenReturn("http://localhost:8080/files/images/abc.tif");

        ImageResponse response = service.upload(file, 1L, ImageType.OPTICAL, "Sentinel-2",
                LocalDate.of(2020, 3, 15), 28.6, 77.2, 10.0);

        assertEquals(10L, response.id());
        assertEquals(1L, response.projectId());
        assertEquals("delhi_2020.tif", response.fileName());
        assertEquals("http://localhost:8080/files/images/abc.tif", response.fileUrl());
        assertEquals(ImageType.OPTICAL, response.imageType());
    }

    @Test
    void uploadRejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{1});
        assertThrows(InvalidImageException.class,
                () -> service.upload(file, 1L, ImageType.OPTICAL, null, null, null, null, null));
        verify(storageService, never()).store(any(MultipartFile.class), any());
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThrows(InvalidImageException.class,
                () -> service.upload(file, 1L, ImageType.OPTICAL, null, null, null, null, null));
    }

    @Test
    void uploadRejectsInvalidLatitude() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        assertThrows(InvalidImageException.class,
                () -> service.upload(file, 1L, ImageType.OPTICAL, null, null, 123.0, null, null));
    }

    @Test
    void uploadCleansUpStoredFileWhenPersistenceFails() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        when(storageService.store(any(MultipartFile.class), eq("images"))).thenReturn("images/x.png");
        when(imageRepository.save(any(SatelliteImage.class))).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class,
                () -> service.upload(file, 1L, ImageType.OPTICAL, null, null, null, null, null));
        verify(storageService).delete("images/x.png");
    }

    @Test
    void uploadToUnknownProjectFails() {
        when(projectRepository.findById(5L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        assertThrows(ResourceNotFoundException.class,
                () -> service.upload(file, 5L, ImageType.OPTICAL, null, null, null, null, null));
    }

    @Test
    void createMetadataPersistsExternalImage() {
        ImageResponse response = service.createMetadata(new ImageCreateRequest(1L, "x.tif",
                "https://cdn.example.com/x.tif", ImageType.SAR, "Sentinel-1", null, null, null, null));
        assertEquals("https://cdn.example.com/x.tif", response.fileUrl());
        assertEquals(ImageType.SAR, response.imageType());
    }

    @Test
    void deleteRemovesMetadataAndStoredFile() {
        SatelliteImage image = SatelliteImage.builder().id(10L).project(project).storageKey("images/a.png").build();
        when(imageRepository.findById(10L)).thenReturn(Optional.of(image));
        when(analysisRepository.existsByInputImagesId(10L)).thenReturn(false);

        service.delete(10L);

        verify(imageRepository).delete(image);
        verify(storageService).delete("images/a.png");
    }

    @Test
    void deleteFailsWhenImageIsUsedByAnalysis() {
        SatelliteImage image = SatelliteImage.builder().id(10L).project(project).build();
        when(imageRepository.findById(10L)).thenReturn(Optional.of(image));
        when(analysisRepository.existsByInputImagesId(10L)).thenReturn(true);

        assertThrows(InvalidImageException.class, () -> service.delete(10L));
        verify(imageRepository, never()).delete(any());
    }
}
