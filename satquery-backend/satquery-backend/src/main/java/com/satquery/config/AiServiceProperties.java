package com.satquery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration of the external Python AI service. All endpoint paths live here (and only here). */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai.service")
public class AiServiceProperties {

    private String baseUrl = "http://localhost:8000";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 120000;
    private final Endpoints endpoints = new Endpoints();

    @Getter
    @Setter
    public static class Endpoints {
//        private String vqa = "/ai/vqa";
//        private String caption = "/ai/caption";
//        private String grounding = "/ai/grounding";
//        private String change = "/ai/change";
//        private String opticalSar = "/ai/optical-sar";
//        private String health = "/health";
          private String analyze = "/analyze";
          private String health = "/";
    }
}
