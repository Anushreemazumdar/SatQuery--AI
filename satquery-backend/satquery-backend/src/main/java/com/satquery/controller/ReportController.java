package com.satquery.controller;

import com.satquery.dto.ReportResponse;
import com.satquery.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "PDF analysis reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/{analysisId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a PDF report",
            description = "Generates a structured PDF report for a COMPLETED analysis. The returned fileUrl can be opened directly.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Report generated"),
            @ApiResponse(responseCode = "400", description = "Analysis is not completed"),
            @ApiResponse(responseCode = "404", description = "Analysis not found"),
            @ApiResponse(responseCode = "500", description = "Report generation failed")
    })
    public ReportResponse generate(@PathVariable Long analysisId) {
        return reportService.generateReport(analysisId);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Get report metadata")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report found"),
            @ApiResponse(responseCode = "404", description = "Report not found")
    })
    public ReportResponse get(@PathVariable Long reportId) {
        return reportService.getReport(reportId);
    }
}
