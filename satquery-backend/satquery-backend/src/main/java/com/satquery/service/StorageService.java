package com.satquery.service;

import org.springframework.web.multipart.MultipartFile;

/** Storage abstraction. LocalStorageService is the default; an S3StorageService can replace it later. */
public interface StorageService {

    String IMAGES_DIR = "images";
    String REPORTS_DIR = "reports";

    /** Stores an uploaded file below the given directory and returns its storage key (e.g. images/uuid.tif). */
    String store(MultipartFile file, String directory);

    /** Stores raw bytes (e.g. a generated PDF) and returns the storage key. */
    String store(byte[] content, String directory, String fileName);

    /** Deletes the stored object. Unknown / blank keys are ignored. */
    void delete(String key);

    /** Returns the public URL under which the stored object can be downloaded. */
    String getUrl(String key);
}
