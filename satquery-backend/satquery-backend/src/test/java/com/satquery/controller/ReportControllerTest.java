package com.satquery.controller;

import com.satquery.dto.ReportResponse;
import com.satquery.entity.ReportStatus;
import com.satquery.exception.InvalidAnalysisRequestException;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock ReportService reportService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ControllerTestSupport.mockMvc(new ReportController(reportService));
    }

    private ReportResponse report() {
        return new ReportResponse(3L, 5L, "Report", LocalDateTime.now(),
                "http://localhost:8080/files/reports/r.pdf", ReportStatus.COMPLETED);
    }

    @Test
    void generateReturnsReport() throws Exception {
        when(reportService.generateReport(5L)).thenReturn(report());

        mvc.perform(post("/api/reports/5"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.fileUrl").value("http://localhost:8080/files/reports/r.pdf"))
                .andExpect(jsonPath("$.reportStatus").value("COMPLETED"));
    }

    @Test
    void generateForIncompleteAnalysisReturns400() throws Exception {
        when(reportService.generateReport(5L)).thenThrow(new InvalidAnalysisRequestException("not completed"));

        mvc.perform(post("/api/reports/5")).andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownReportReturns404() throws Exception {
        when(reportService.getReport(8L)).thenThrow(new ResourceNotFoundException("Report not found with id 8"));

        mvc.perform(get("/api/reports/8")).andExpect(status().isNotFound());
    }
}
