package com.satquery.service;

import com.satquery.config.StorageProperties;
import com.satquery.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;
    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve(IMAGES_DIR));
            Files.createDirectories(root.resolve(REPORTS_DIR));
        } catch (IOException e) {
            throw new StorageException("Could not initialise upload directory " + root, e);
        }
        log.info("Local storage initialised at {}", root);
    }

    @Override
    public String store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Cannot store an empty file");
        }
        String key = checkedDirectory(directory) + "/" + UUID.randomUUID() + "." + extensionOf(file.getOriginalFilename());
        Path target = resolveSafe(key);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to store file " + key, e);
        }
        log.info("Stored file {} ({} bytes)", key, file.getSize());
        return key;
    }

    @Override
    public String store(byte[] content, String directory, String fileName) {
        if (content == null || content.length == 0) {
            throw new StorageException("Cannot store empty content");
        }
        String safeName = (fileName == null ? "file" : fileName).replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = checkedDirectory(directory) + "/" + safeName;
        Path target = resolveSafe(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new StorageException("Failed to store file " + key, e);
        }
        log.info("Stored file {} ({} bytes)", key, content.length);
        return key;
    }

    @Override
    public void delete(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(resolveSafe(key));
            log.info("Deleted stored file {} (existed={})", key, deleted);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file " + key, e);
        }
    }

    @Override
    public String getUrl(String key) {
        String base = properties.getPublicBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/files/" + key;
    }

    private Path resolveSafe(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new StorageException("Invalid storage path");
        }
        return target;
    }

    private String checkedDirectory(String directory) {
        if (directory == null || !directory.matches("[a-zA-Z0-9_-]+")) {
            throw new StorageException("Invalid storage directory");
        }
        return directory;
    }

    private String extensionOf(String originalFilename) {
        String ext = StringUtils.getFilenameExtension(originalFilename);
        if (ext == null) {
            return "bin";
        }
        String cleaned = ext.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (cleaned.isEmpty()) {
            return "bin";
        }
        return cleaned.length() > 10 ? cleaned.substring(0, 10) : cleaned;
    }
}
