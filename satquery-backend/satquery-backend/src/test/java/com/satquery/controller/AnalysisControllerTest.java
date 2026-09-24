package com.satquery.controller;

import com.satquery.dto.AnalysisRequest;
import com.satquery.dto.AnalysisResponse;
import com.satquery.dto.AnalysisStatusResponse;
import com.satquery.entity.AnalysisStatus;
import com.satquery.entity.AnalysisType;
import com.satquery.exception.AIServiceException;
import com.satquery.exception.InvalidAnalysisRequestException;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.service.AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AnalysisControllerTest {

    private static final String VALID_BODY = """
            {"projectId":1,"imageIds":[10,11],"analysisType":"CHANGE_DETECTION","question":"What changed?"}
            """;

    @Mock AnalysisService analysisService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ControllerTestSupport.mockMvc(new AnalysisController(analysisService));
    }

    private AnalysisResponse completed() {
        return new AnalysisResponse(5L, 1L, "P", AnalysisStatus.COMPLETED, AnalysisType.CHANGE_DETECTION,
                AnalysisType.CHANGE_DETECTION, "What changed?", "New roads", "sum", List.of("f1"), 0.9,
                new AnalysisResponse.ModelInfo("m", "v1"), List.of(), null, List.of(), 100L,
                LocalDateTime.now(), LocalDateTime.now(), null);
    }

    @Test
    void createReturns201WithAnalysis() throws Exception {
        when(analysisService.createAndExecute(any(AnalysisRequest.class))).thenReturn(completed());

        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.answer").value("New roads"))
                .andExpect(jsonPath("$.model.name").value("m"));
    }

    @Test
    void createRejectsMissingFields() throws Exception {
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":1,\"imageIds\":[],\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(analysisService);
    }

    @Test
    void createRejectsUnknownAnalysisType() throws Exception {
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":1,\"imageIds\":[1],\"analysisType\":\"MAGIC\",\"question\":\"q\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRequestMapsTo400() throws Exception {
        when(analysisService.createAndExecute(any())).thenThrow(new InvalidAnalysisRequestException("needs 2 images"));

        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("needs 2 images"));
    }

    @Test
    void aiFailureMapsTo502() throws Exception {
        when(analysisService.createAndExecute(any())).thenThrow(new AIServiceException("AI service unreachable"));

        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI Service Error"));
    }

    @Test
    void getUnknownAnalysisReturns404() throws Exception {
        when(analysisService.getAnalysis(7L)).thenThrow(new ResourceNotFoundException("Analysis not found with id 7"));

        mvc.perform(get("/api/analysis/7")).andExpect(status().isNotFound());
    }

    @Test
    void statusEndpointReturnsStatus() throws Exception {
        when(analysisService.getStatus(5L)).thenReturn(
                new AnalysisStatusResponse(5L, AnalysisStatus.PROCESSING, null, LocalDateTime.now(), null));

        mvc.perform(get("/api/analysis/5/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void evidenceEndpointReturnsList() throws Exception {
        when(analysisService.getEvidence(5L)).thenReturn(List.of());

        mvc.perform(get("/api/analysis/5/evidence"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/analysis/5")).andExpect(status().isNoContent());
        verify(analysisService).delete(5L);
    }
}
