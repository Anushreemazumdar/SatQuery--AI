package com.satquery.controller;

import com.satquery.dto.AnalysisRequest;
import com.satquery.dto.AnalysisResponse;
import com.satquery.dto.AnalysisStatusResponse;
import com.satquery.dto.EvidenceResponse;
import com.satquery.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@Tag(name = "Analysis", description = "Run and inspect satellite image analyses")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    @Operation(summary = "Create and execute an analysis",
            description = "Validates the request, calls the Python AI service for the selected (or auto-routed) task and "
                    + "returns the persisted result. Use analysisType AUTO to let the backend pick the task.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "VQA", value = "{\"projectId\":1,\"imageIds\":[10],\"analysisType\":\"VQA\","
                            + "\"question\":\"What objects are visible in this area?\"}"),
                    @ExampleObject(name = "Change detection", value = "{\"projectId\":1,\"imageIds\":[10,11],"
                            + "\"analysisType\":\"CHANGE_DETECTION\",\"question\":\"What changed between these two images?\"}"),
                    @ExampleObject(name = "Auto routing", value = "{\"projectId\":1,\"imageIds\":[10],\"analysisType\":\"AUTO\","
                            + "\"question\":\"Where are the buildings?\"}")
            })))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Analysis completed"),
            @ApiResponse(responseCode = "400", description = "Invalid analysis request"),
            @ApiResponse(responseCode = "404", description = "Project or image not found"),
            @ApiResponse(responseCode = "502", description = "AI service failed (analysis stored with status FAILED)")
    })
    public ResponseEntity<AnalysisResponse> create(@Valid @RequestBody AnalysisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(analysisService.createAndExecute(request));
    }

    @GetMapping("/{analysisId}")
    @Operation(summary = "Get a complete analysis result",
            description = "Returns answer, confidence, model, evidence (bounding boxes), change map URL and processing info.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis found"),
            @ApiResponse(responseCode = "404", description = "Analysis not found")
    })
    public AnalysisResponse get(@PathVariable Long analysisId) {
        return analysisService.getAnalysis(analysisId);
    }

    @GetMapping("/{analysisId}/evidence")
    @Operation(summary = "Get analysis evidence", description = "Returns highlighted regions / evidence items of the analysis.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evidence list"),
            @ApiResponse(responseCode = "404", description = "Analysis not found")
    })
    public List<EvidenceResponse> getEvidence(@PathVariable Long analysisId) {
        return analysisService.getEvidence(analysisId);
    }

    @GetMapping("/{analysisId}/status")
    @Operation(summary = "Get analysis status", description = "PENDING, PROCESSING, COMPLETED or FAILED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status"),
            @ApiResponse(responseCode = "404", description = "Analysis not found")
    })
    public AnalysisStatusResponse getStatus(@PathVariable Long analysisId) {
        return analysisService.getStatus(analysisId);
    }

    @DeleteMapping("/{analysisId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an analysis", description = "Deletes the analysis with its result, evidence and reports.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Analysis deleted"),
            @ApiResponse(responseCode = "404", description = "Analysis not found")
    })
    public void delete(@PathVariable Long analysisId) {
        analysisService.delete(analysisId);
    }
}
