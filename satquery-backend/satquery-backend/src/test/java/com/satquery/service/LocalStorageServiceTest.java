package com.satquery.service;

import com.satquery.config.StorageProperties;
import com.satquery.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.setUploadDir(tempDir.toString());
        props.setPublicBaseUrl("http://localhost:8080/");
        storage = new LocalStorageService(props);
    }

    @Test
    void storesMultipartFileWithGeneratedNameAndKeepsExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "scene.TIF", "image/tiff", new byte[]{1, 2, 3});

        String key = storage.store(file, StorageService.IMAGES_DIR);

        assertTrue(key.startsWith("images/"));
        assertTrue(key.endsWith(".tif"));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(tempDir.resolve(key)));
    }

    @Test
    void storesRawBytesAndSanitisesFileName() throws Exception {
        String key = storage.store("%PDF".getBytes(), StorageService.REPORTS_DIR, "my report (1).pdf");

        assertEquals("reports/my_report__1_.pdf", key);
        assertTrue(Files.exists(tempDir.resolve(key)));
    }

    @Test
    void rejectsEmptyContent() {
        assertThrows(StorageException.class, () -> storage.store(new byte[0], StorageService.REPORTS_DIR, "a.pdf"));
        assertThrows(StorageException.class,
                () -> storage.store(new MockMultipartFile("file", "a.png", "image/png", new byte[0]), StorageService.IMAGES_DIR));
    }

    @Test
    void rejectsInvalidDirectory() {
        assertThrows(StorageException.class, () -> storage.store(new byte[]{1}, "../evil", "a.pdf"));
    }

    @Test
    void deleteRemovesFileAndIgnoresUnknownOrBlankKeys() {
        String key = storage.store(new byte[]{1}, StorageService.REPORTS_DIR, "x.pdf");
        storage.delete(key);
        assertFalse(Files.exists(tempDir.resolve(key)));

        assertDoesNotThrow(() -> storage.delete(key));
        assertDoesNotThrow(() -> storage.delete(null));
        assertDoesNotThrow(() -> storage.delete("  "));
    }

    @Test
    void deleteBlocksPathTraversal() {
        assertThrows(StorageException.class, () -> storage.delete("../../etc/passwd"));
    }

    @Test
    void buildsPublicUrlWithoutDoubleSlash() {
        assertEquals("http://localhost:8080/files/images/a.png", storage.getUrl("images/a.png"));
    }
}
