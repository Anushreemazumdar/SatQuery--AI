package com.satquery.controller;

import com.satquery.client.AIServiceClient;
import com.satquery.dto.AIRequest;
import com.satquery.dto.AIResponse;
import com.satquery.entity.AnalysisType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-test")
public class AITestController {

    private final AIServiceClient aiServiceClient;

    public AITestController(AIServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping
    public ResponseEntity<AIResponse> test() {

        AIRequest request = new AIRequest(
                null,
                "What objects are visible in this satellite image?",
                List.of("file:///D:/test.jpg"),
                AnalysisType.VQA
        );

        return ResponseEntity.ok(
                aiServiceClient.runVQA(request, null)
        );
    }
}
