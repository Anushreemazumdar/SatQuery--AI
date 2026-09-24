package com.satquery.service;

import com.satquery.client.AIServiceClient;
import com.satquery.dto.*;
import com.satquery.entity.*;
import com.satquery.exception.AIServiceException;
import com.satquery.exception.InvalidAnalysisRequestException;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.exception.StorageException;
import com.satquery.mapper.AnalysisMapper;
import com.satquery.repository.*;
import com.satquery.util.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates an analysis: validate -> PENDING -> PROCESSING -> AI call -> persist -> COMPLETED / FAILED.
 * The AI call is executed outside of any database transaction, and {@link #execute(Long)} only needs the analysis id,
 * so it can later be moved to an asynchronous worker without changing the flow.
 */
@Service
@Slf4j
public class AnalysisService {

    private static final int MAX_ERROR_LENGTH = 1900;

    private final ProjectRepository projectRepository;
    private final SatelliteImageRepository imageRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisResultRepository resultRepository;
    private final EvidenceRepository evidenceRepository;
    private final ReportRepository reportRepository;
    private final AIModelRepository modelRepository;
    private final AIServiceClient aiClient;
    private final AnalysisRoutingStrategy router;
    private final AnalysisValidator validator;
    private final AnalysisMapper mapper;
    private final StorageService storageService;
    private final TransactionTemplate tx;

    public AnalysisService(ProjectRepository projectRepository,
                           SatelliteImageRepository imageRepository,
                           AnalysisRepository analysisRepository,
                           AnalysisResultRepository resultRepository,
                           EvidenceRepository evidenceRepository,
                           ReportRepository reportRepository,
                           AIModelRepository modelRepository,
                           AIServiceClient aiClient,
                           AnalysisRoutingStrategy router,
                           AnalysisValidator validator,
                           AnalysisMapper mapper,
                           StorageService storageService,
                           PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.imageRepository = imageRepository;
        this.analysisRepository = analysisRepository;
        this.resultRepository = resultRepository;
        this.evidenceRepository = evidenceRepository;
        this.reportRepository = reportRepository;
        this.modelRepository = modelRepository;
        this.aiClient = aiClient;
        this.router = router;
        this.validator = validator;
        this.mapper = mapper;
        this.storageService = storageService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    private record ExecutionPlan(Long analysisId, AnalysisType type, AIRequest request, String endpointOverride) {
    }

    // ------------------------------------------------------------------ create + run

    /** Creates and synchronously executes an analysis. Throws AIServiceException if the AI call fails (status FAILED is persisted). */
    public AnalysisResponse createAndExecute(AnalysisRequest request) {
        Long analysisId = createPending(request);
        execute(analysisId);
        return getAnalysis(analysisId);
    }

    /** Validates the request and persists a PENDING analysis. Returns its id. */
    public Long createPending(AnalysisRequest request) {
        return tx.execute(status -> {
            Project project = projectRepository.findById(request.projectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + request.projectId()));

            List<Long> imageIds = request.imageIds();
            if (new HashSet<>(imageIds).size() != imageIds.size()) {
                throw new InvalidAnalysisRequestException("imageIds must not contain duplicates");
            }

            Map<Long, SatelliteImage> byId = imageRepository.findAllById(imageIds).stream()
                    .collect(Collectors.toMap(SatelliteImage::getId, Function.identity()));
            List<SatelliteImage> images = new ArrayList<>();
            for (Long imageId : imageIds) {
                SatelliteImage image = byId.get(imageId);
                if (image == null) {
                    throw new ResourceNotFoundException("Image not found with id " + imageId);
                }
                if (!image.getProject().getId().equals(project.getId())) {
                    throw new InvalidAnalysisRequestException(
                            "Image " + imageId + " does not belong to project " + project.getId());
                }
                images.add(image);
            }

            AnalysisType resolvedType = request.analysisType() == AnalysisType.AUTO
                    ? router.route(request.question(), images)
                    : request.analysisType();
            validator.validate(resolvedType, images);
            List<SatelliteImage> orderedImages = resolvedType == AnalysisType.OPTICAL_SAR
                    ? validator.orderOpticalFirst(images)
                    : images;

            Analysis analysis = Analysis.builder()
                    .project(project)
                    .inputImages(new ArrayList<>(orderedImages))
                    .question(request.question().trim())
                    .analysisType(resolvedType)
                    .requestedAnalysisType(request.analysisType())
                    .status(AnalysisStatus.PENDING)
                    .build();
            analysis = analysisRepository.save(analysis);
            log.info("Analysis {} created: project={} requestedType={} resolvedType={} images={}",
                    analysis.getId(), project.getId(), request.analysisType(), resolvedType, imageIds);
            return analysis.getId();
        });
    }

    /** Runs the analysis through the AI service and persists the outcome. */
    public void execute(Long analysisId) {
        ExecutionPlan plan = tx.execute(status -> startProcessing(analysisId));
        long startNanos = System.nanoTime();

        AIResponse response;
        try {
            response = callAi(plan);
        } catch (RuntimeException ex) {
            String message = ex instanceof AIServiceException
                    ? ex.getMessage()
                    : "Unexpected error while calling the AI service: " + ex.getMessage();
            markFailed(analysisId, message);
            log.error("Analysis {} failed: {}", analysisId, message);
            if (ex instanceof AIServiceException aiEx) {
                throw new AIServiceException("Analysis " + analysisId + " failed: " + aiEx.getMessage(), aiEx);
            }
            throw new AIServiceException("Analysis " + analysisId + " failed: " + message, ex);
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        try {
            tx.executeWithoutResult(status -> persistResult(analysisId, response, elapsedMs));
        } catch (RuntimeException ex) {
            markFailed(analysisId, "Failed to persist AI result: " + ex.getMessage());
            log.error("Analysis {} result could not be persisted", analysisId, ex);
            throw ex;
        }
        log.info("Analysis {} completed (model={}, confidence={})", analysisId, response.model(), response.confidence());
    }

    private ExecutionPlan startProcessing(Long analysisId) {
        Analysis analysis = analysisRepository.findWithDetailsById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found with id " + analysisId));
        analysis.setStatus(AnalysisStatus.PROCESSING);
        analysisRepository.save(analysis);

        List<String> urls = analysis.getInputImages().stream().map(SatelliteImage::getFileUrl).toList();
        AIRequest request = new AIRequest(analysis.getId(), analysis.getQuestion(), urls, analysis.getAnalysisType());
        String endpoint = modelRepository
                .findFirstByTaskAndActiveTrueOrderByCreatedAtDesc(ModelTask.from(analysis.getAnalysisType()))
                .map(AIModel::getEndpoint)
                .orElse(null);
        return new ExecutionPlan(analysis.getId(), analysis.getAnalysisType(), request, endpoint);
    }

    private AIResponse callAi(ExecutionPlan plan) {
        return switch (plan.type()) {
            case VQA -> aiClient.runVQA(plan.request(), plan.endpointOverride());
            case CAPTION -> aiClient.runCaptioning(plan.request(), plan.endpointOverride());
            case GROUNDING -> aiClient.runGrounding(plan.request(), plan.endpointOverride());
            case CHANGE_DETECTION -> aiClient.runChangeDetection(plan.request(), plan.endpointOverride());
            case OPTICAL_SAR -> aiClient.runOpticalSAR(plan.request(), plan.endpointOverride());
            case AUTO -> throw new InvalidAnalysisRequestException("AUTO must be resolved before execution");
        };
    }

    private void persistResult(Long analysisId, AIResponse response, long elapsedMs) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found with id " + analysisId));

        AnalysisResult result = AnalysisResult.builder()
                .analysis(analysis)
                .answer(response.answer())
                .summary(response.summary())
                .findings(mapper.serializeFindings(response.findings()))
                .changeMapUrl(TextUtils.truncate(response.changeMapUrl(), 1000))
                .build();
        resultRepository.save(result);

        if (response.evidence() != null && !response.evidence().isEmpty()) {
            List<Evidence> evidence = response.evidence().stream()
                    .map(item -> mapper.toEvidence(item, analysis))
                    .toList();
            evidenceRepository.saveAll(evidence);
        }

        analysis.setConfidence(response.confidence());
        analysis.setModelUsed(TextUtils.truncate(response.model(), 150));
        analysis.setModelVersion(TextUtils.truncate(response.modelVersion(), 50));
        analysis.setProcessingTimeMs(response.processingTimeMs() != null ? response.processingTimeMs() : elapsedMs);
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setCompletedAt(LocalDateTime.now());
        analysis.setErrorMessage(null);
        analysisRepository.save(analysis);
    }

    private void markFailed(Long analysisId, String message) {
        try {
            tx.executeWithoutResult(status -> analysisRepository.findById(analysisId).ifPresent(analysis -> {
                analysis.setStatus(AnalysisStatus.FAILED);
                analysis.setErrorMessage(TextUtils.truncate(message, MAX_ERROR_LENGTH));
                analysis.setCompletedAt(LocalDateTime.now());
                analysisRepository.save(analysis);
            }));
        } catch (RuntimeException ex) {
            log.error("Could not mark analysis {} as FAILED", analysisId, ex);
        }
    }

    // ------------------------------------------------------------------ queries

    public AnalysisResponse getAnalysis(Long analysisId) {
        return tx.execute(status -> {
            Analysis analysis = analysisRepository.findWithDetailsById(analysisId)
                    .orElseThrow(() -> new ResourceNotFoundException("Analysis not found with id " + analysisId));
            AnalysisResult result = resultRepository.findByAnalysisId(analysisId).orElse(null);
            List<Evidence> evidence = evidenceRepository.findByAnalysisIdOrderByIdAsc(analysisId);
            return mapper.toResponse(analysis, result, evidence);
        });
    }

    @Transactional(readOnly = true)
    public AnalysisStatusResponse getStatus(Long analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found with id " + analysisId));
        return new AnalysisStatusResponse(analysis.getId(), analysis.getStatus(), analysis.getErrorMessage(),
                analysis.getCreatedAt(), analysis.getCompletedAt());
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getEvidence(Long analysisId) {
        if (!analysisRepository.existsById(analysisId)) {
            throw new ResourceNotFoundException("Analysis not found with id " + analysisId);
        }
        return evidenceRepository.findByAnalysisIdOrderByIdAsc(analysisId).stream()
                .map(mapper::toEvidenceResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AnalysisSummaryResponse> getHistory(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id " + projectId);
        }
        List<Analysis> analyses = analysisRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        if (analyses.isEmpty()) {
            return List.of();
        }
        List<Long> ids = analyses.stream().map(Analysis::getId).toList();
        Map<Long, AnalysisResult> results = resultRepository.findByAnalysisIdIn(ids).stream()
                .collect(Collectors.toMap(r -> r.getAnalysis().getId(), Function.identity(), (a, b) -> a));
        return analyses.stream().map(a -> mapper.toSummary(a, results.get(a.getId()))).toList();
    }

    // ------------------------------------------------------------------ delete

    @Transactional
    public void delete(Long analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found with id " + analysisId));

        List<Report> reports = reportRepository.findByAnalysisId(analysisId);
        for (Report report : reports) {
            if (report.getStorageKey() != null) {
                try {
                    storageService.delete(report.getStorageKey());
                } catch (StorageException ex) {
                    log.warn("Could not delete report file {}: {}", report.getStorageKey(), ex.getMessage());
                }
            }
        }
        reportRepository.deleteAll(reports);
        evidenceRepository.deleteByAnalysisId(analysisId);
        resultRepository.deleteByAnalysisId(analysisId);
        analysisRepository.delete(analysis);
        analysisRepository.flush();
        log.info("Deleted analysis {}", analysisId);
    }
}
