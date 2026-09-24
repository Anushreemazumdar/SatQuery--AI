package com.satquery.controller;

import com.satquery.dto.ProjectRequest;
import com.satquery.dto.ProjectResponse;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.service.ProjectService;
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
class ProjectControllerTest {

    @Mock ProjectService projectService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ControllerTestSupport.mockMvc(new ProjectController(projectService));
    }

    private ProjectResponse response() {
        return new ProjectResponse(1L, "Delhi", "desc", 0, 0, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void createReturns201() throws Exception {
        when(projectService.createProject(any(ProjectRequest.class))).thenReturn(response());

        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delhi\",\"description\":\"desc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Delhi"));
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(projectService);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsProjects() throws Exception {
        when(projectService.getAllProjects()).thenReturn(List.of(response()));

        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Delhi"));
    }

    @Test
    void getUnknownProjectReturns404() throws Exception {
        when(projectService.getProject(9L)).thenThrow(new ResourceNotFoundException("Project not found with id 9"));

        mvc.perform(get("/api/projects/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource Not Found"))
                .andExpect(jsonPath("$.path").value("/api/projects/9"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mvc.perform(get("/api/projects/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/projects/1")).andExpect(status().isNoContent());
        verify(projectService).deleteProject(1L);
    }
}
