package com.satquery.service;

import com.satquery.dto.AIModelRequest;
import com.satquery.dto.AIModelResponse;
import com.satquery.entity.AIModel;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.repository.AIModelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Registry of AI models per task; lets operators change models/endpoints without code changes. */
@Service
@Slf4j
public class AIModelService {

    private final AIModelRepository modelRepository;

    public AIModelService(AIModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    @Transactional(readOnly = true)
    public List<AIModelResponse> listModels() {
        return modelRepository.findAllByOrderByTaskAscCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AIModelResponse registerModel(AIModelRequest request) {
        AIModel model = AIModel.builder()
                .name(request.name())
                .task(request.task())
                .version(request.version())
                .endpoint(blankToNull(request.endpoint()))
                .description(request.description())
                .active(request.active() == null || request.active())
                .build();
        model = modelRepository.save(model);
        log.info("Registered AI model {} v{} for task {}", model.getName(), model.getVersion(), model.getTask());
        return toResponse(model);
    }

    @Transactional
    public AIModelResponse updateModel(Long id, AIModelRequest request) {
        AIModel model = modelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI model not found with id " + id));
        model.setName(request.name());
        model.setTask(request.task());
        model.setVersion(request.version());
        model.setEndpoint(blankToNull(request.endpoint()));
        model.setDescription(request.description());
        if (request.active() != null) {
            model.setActive(request.active());
        }
        return toResponse(modelRepository.save(model));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AIModelResponse toResponse(AIModel m) {
        return new AIModelResponse(m.getId(), m.getName(), m.getTask(), m.getVersion(), m.getEndpoint(),
                m.getDescription(), m.isActive(), m.getCreatedAt());
    }
}
