package com.satquery.controller;

import com.satquery.dto.AIModelRequest;
import com.satquery.dto.AIModelResponse;
import com.satquery.service.AIModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
@Tag(name = "AI Models", description = "Registry of AI models per task")
public class ModelController {

    private final AIModelService modelService;

    public ModelController(AIModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    @Operation(summary = "List registered AI models")
    @ApiResponse(responseCode = "200", description = "Registered models")
    public List<AIModelResponse> list() {
        return modelService.listModels();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an AI model",
            description = "The newest active model of a task may override the endpoint used for that task.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Model registered"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public AIModelResponse register(@Valid @RequestBody AIModelRequest request) {
        return modelService.registerModel(request);
    }

    @PutMapping("/{modelId}")
    @Operation(summary = "Update an AI model (including activate / deactivate)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model updated"),
            @ApiResponse(responseCode = "404", description = "Model not found")
    })
    public AIModelResponse update(@PathVariable Long modelId, @Valid @RequestBody AIModelRequest request) {
        return modelService.updateModel(modelId, request);
    }
}
