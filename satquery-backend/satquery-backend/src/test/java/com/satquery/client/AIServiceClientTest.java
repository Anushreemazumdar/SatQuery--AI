package com.satquery.client;

import com.satquery.config.AiServiceProperties;
import com.satquery.dto.AIRequest;
import com.satquery.dto.AIResponse;
import com.satquery.entity.AnalysisType;
import com.satquery.exception.AIServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AIServiceClientTest {

    private final AiServiceProperties properties = new AiServiceProperties();

    private AIRequest request(AnalysisType type) {
        return new AIRequest(1L, "What changed?", List.of("http://a/1.tif", "http://a/2.tif"), type);
    }

    private AIServiceClient clientReturning(HttpStatus status, String body, AtomicReference<URI> capturedUri) {
        properties.setBaseUrl("http://ai-service:8000/");
        WebClient webClient = WebClient.builder().exchangeFunction(req -> {
            if (capturedUri != null) {
                capturedUri.set(req.url());
            }
            return Mono.just(ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body).build());
        }).build();
        return new AIServiceClient(webClient, properties);
    }

    @Test
    void mapsSuccessfulResponseAndCallsConfiguredEndpoint() {
        AtomicReference<URI> uri = new AtomicReference<>();
        String json = "{\"answer\":\"Expansion\",\"summary\":\"s\",\"confidence\":0.91,\"model\":\"ChangeFormer\","
                + "\"modelVersion\":\"1.0\",\"processingTimeMs\":4200,\"unknownField\":true,"
                + "\"evidence\":[{\"type\":\"CHANGED_REGION\",\"description\":\"d\",\"confidence\":0.9,"
                + "\"x\":1,\"y\":2,\"width\":3,\"height\":4}],\"changeMapUrl\":\"http://m\"}";
        AIServiceClient client = clientReturning(HttpStatus.OK, json, uri);

        AIResponse response = client.runChangeDetection(request(AnalysisType.CHANGE_DETECTION), null);

        assertEquals("http://ai-service:8000/ai/change", uri.get().toString());
        assertEquals("Expansion", response.answer());
        assertEquals(0.91, response.confidence());
        assertEquals("ChangeFormer", response.model());
        assertEquals(1, response.evidence().size());
        assertEquals(3.0, response.evidence().get(0).width());
        assertEquals("http://m", response.changeMapUrl());
    }

    @Test
    void routesEachTaskToItsOwnEndpoint() {
        AtomicReference<URI> uri = new AtomicReference<>();
        AIServiceClient client = clientReturning(HttpStatus.OK, "{\"answer\":\"ok\"}", uri);

        client.runVQA(request(AnalysisType.VQA), null);
        assertTrue(uri.get().toString().endsWith("/ai/vqa"));
        client.runCaptioning(request(AnalysisType.CAPTION), null);
        assertTrue(uri.get().toString().endsWith("/ai/caption"));
        client.runGrounding(request(AnalysisType.GROUNDING), null);
        assertTrue(uri.get().toString().endsWith("/ai/grounding"));
        client.runOpticalSAR(request(AnalysisType.OPTICAL_SAR), null);
        assertTrue(uri.get().toString().endsWith("/ai/optical-sar"));
    }

    @Test
    void endpointOverrideIsHonoured() {
        AtomicReference<URI> uri = new AtomicReference<>();
        AIServiceClient client = clientReturning(HttpStatus.OK, "{\"answer\":\"ok\"}", uri);

        client.runVQA(request(AnalysisType.VQA), "/v2/vqa");
        assertEquals("http://ai-service:8000/v2/vqa", uri.get().toString());

        client.runVQA(request(AnalysisType.VQA), "http://other-host:9000/vqa");
        assertEquals("http://other-host:9000/vqa", uri.get().toString());
    }

    @Test
    void httpErrorBecomesAiServiceException() {
        AIServiceClient client = clientReturning(HttpStatus.INTERNAL_SERVER_ERROR, "{\"detail\":\"model crashed\"}", null);

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> client.runVQA(request(AnalysisType.VQA), null));
        assertTrue(ex.getMessage().contains("500"));
        assertTrue(ex.getMessage().contains("model crashed"));
    }

    @Test
    void connectionProblemBecomesAiServiceException() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(req -> Mono.error(new ConnectException("Connection refused"))).build();
        AIServiceClient client = new AIServiceClient(webClient, properties);

        AIServiceException ex = assertThrows(AIServiceException.class,
                () -> client.runVQA(request(AnalysisType.VQA), null));
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    void responseWithoutAnswerIsRejectedInsteadOfFabricated() {
        AIServiceClient client = clientReturning(HttpStatus.OK, "{\"confidence\":0.5}", null);
        assertThrows(AIServiceException.class, () -> client.runVQA(request(AnalysisType.VQA), null));
    }

    @Test
    void healthCheckReflectsAvailability() {
        assertTrue(clientReturning(HttpStatus.OK, "{}", null).isHealthy());
        assertFalse(clientReturning(HttpStatus.SERVICE_UNAVAILABLE, "{}", null).isHealthy());
        WebClient broken = WebClient.builder().exchangeFunction(req -> Mono.error(new ConnectException("down"))).build();
        assertFalse(new AIServiceClient(broken, properties).isHealthy());
    }
}
