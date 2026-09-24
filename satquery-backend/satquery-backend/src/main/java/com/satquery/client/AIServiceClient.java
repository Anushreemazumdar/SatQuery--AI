package com.satquery.client;

import com.satquery.config.AiServiceProperties;
import com.satquery.dto.AIRequest;
import com.satquery.dto.AIResponse;
import com.satquery.exception.AIServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class AIServiceClient {

    private final WebClient webClient;
    private final AiServiceProperties properties;

    public AIServiceClient(WebClient aiWebClient, AiServiceProperties properties) {
        this.webClient = aiWebClient;
        this.properties = properties;
    }

    public AIResponse runVQA(AIRequest request, String endpointOverride) {
        return post("VQA", request);
    }

    public AIResponse runCaptioning(AIRequest request, String endpointOverride) {
        return post("CAPTION", request);
    }

    public AIResponse runGrounding(AIRequest request, String endpointOverride) {
        return post("GROUNDING", request);
    }

    public AIResponse runChangeDetection(AIRequest request, String endpointOverride) {
        return post("CHANGE_DETECTION", request);
    }

    public AIResponse runOpticalSAR(AIRequest request, String endpointOverride) {
        return post("OPTICAL_SAR", request);
    }

    public boolean isHealthy() {
        URI uri = resolve(properties.getEndpoints().getHealth());

        try {
            Boolean up = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(3))
                    .map(response -> response.getStatusCode().is2xxSuccessful())
                    .block();

            return Boolean.TRUE.equals(up);

        } catch (Exception ex) {
            log.warn("AI service health check failed at {}: {}", uri, ex.getMessage());
            return false;
        }
    }

    public String getBaseUrl() {
        return properties.getBaseUrl();
    }

    /**
     * Sends the normalised Spring request to the single FastAPI /analyze endpoint.
     *
     * FastAPI expects:
     *   query  -> text
     *   image1 -> required file
     *   image2 -> optional file
     */
    private AIResponse post(String task, AIRequest request) {

        URI uri = resolve(properties.getEndpoints().getAnalyze());

        log.info(
                "AI request: task={} analysisId={} images={} url={}",
                task,
                request.analysisId(),
                request.imageUrls() == null ? 0 : request.imageUrls().size(),
                uri
        );

        long start = System.currentTimeMillis();

        try {
            List<String> imageUrls = request.imageUrls();

            if (imageUrls == null || imageUrls.isEmpty()) {
                throw new AIServiceException(
                        "No satellite image was provided for AI analysis."
                );
            }

            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            builder.part("query", request.question());

            byte[] image1 = downloadImage(imageUrls.get(0));

            ByteArrayResource image1Resource = new ByteArrayResource(image1) {
                @Override
                public String getFilename() {
                    return filenameFromUrl(imageUrls.get(0), "image1.jpg");
                }
            };

            builder.part("image1", image1Resource)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);


            if (imageUrls.size() >= 2 && StringUtils.hasText(imageUrls.get(1))) {

                byte[] image2 = downloadImage(imageUrls.get(1));

                ByteArrayResource image2Resource = new ByteArrayResource(image2) {
                    @Override
                    public String getFilename() {
                        return filenameFromUrl(imageUrls.get(1), "image2.jpg");
                    }
                };

                builder.part("image2", image2Resource)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM);
            }


            AIResponse response = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(builder.build())
                    .retrieve()
                    .bodyToMono(AIResponse.class)
                    .block();

            if (response == null || !StringUtils.hasText(response.answer())) {
                throw new AIServiceException(
                        "AI service returned an empty or invalid response."
                );
            }

            log.info(
                    "AI response received: task={} analysisId={} model={} in {} ms",
                    task,
                    request.analysisId(),
                    response.model(),
                    System.currentTimeMillis() - start
            );

            return response;

        } catch (AIServiceException ex) {

            log.error(
                    "AI response failure: task={} analysisId={} reason={}",
                    task,
                    request.analysisId(),
                    ex.getMessage()
            );

            throw ex;

        } catch (Exception ex) {

            log.error(
                    "AI request failed: task={} analysisId={} reason={}",
                    task,
                    request.analysisId(),
                    ex.getMessage()
            );

            throw new AIServiceException(
                    "AI service request failed for " + task + ": " + ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * Downloads an image from the URL stored by the Spring backend.
     */
//
    private byte[] downloadImage(String imageUrl) {

        try {

            if (imageUrl.startsWith("file:///")) {
                java.nio.file.Path path =
                        java.nio.file.Paths.get(
                                java.net.URI.create(imageUrl)
                        );

                return java.nio.file.Files.readAllBytes(path);
            }

            log.debug("Downloading image for AI analysis: {}", imageUrl);

            return webClient.get()
                    .uri(URI.create(imageUrl))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

        } catch (Exception ex) {

            throw new AIServiceException(
                    "Could not load satellite image: " + imageUrl,
                    ex
            );
        }
    }

    private String filenameFromUrl(String url, String fallback) {

        try {

            String path = URI.create(url).getPath();

            if (path == null || path.isBlank()) {
                return fallback;
            }

            String filename = path.substring(path.lastIndexOf('/') + 1);

            filename = URLDecoder.decode(
                    filename,
                    StandardCharsets.UTF_8
            );

            return filename.isBlank() ? fallback : filename;

        } catch (Exception ex) {
            return fallback;
        }
    }

    private URI resolve(String path) {

        if (path.startsWith("http://") || path.startsWith("https://")) {
            return URI.create(path);
        }

        String base = properties.getBaseUrl();

        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return URI.create(
                base + (path.startsWith("/") ? path : "/" + path)
        );
    }
}