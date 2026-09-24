package com.satquery.controller;

import com.satquery.client.AIServiceClient;
import com.satquery.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Backend and AI service health checks")
public class HealthController {

    private final AIServiceClient aiServiceClient;

    public HealthController(AIServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @GetMapping
    @Operation(summary = "Backend health")
    @ApiResponse(responseCode = "200", description = "Backend is up")
    public HealthResponse health() {
        return new HealthResponse("UP", "SatQuery Backend");
    }

    @GetMapping("/ai")
    @Operation(summary = "AI service health", description = "Calls the health endpoint of the configured Python AI service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI service reachable"),
            @ApiResponse(responseCode = "503", description = "AI service unreachable")
    })
    public ResponseEntity<HealthResponse> aiHealth() {
        boolean up = aiServiceClient.isHealthy();
        HealthResponse body = new HealthResponse(up ? "UP" : "DOWN", "SatQuery AI Service");
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
