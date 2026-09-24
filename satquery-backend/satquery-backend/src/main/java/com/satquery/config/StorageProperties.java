package com.satquery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** Root directory for uploaded images and generated reports. */
    private String uploadDir = "uploads";

    /** Public base URL of this backend, used to build file URLs. */
    private String publicBaseUrl = "http://localhost:8080";
}
