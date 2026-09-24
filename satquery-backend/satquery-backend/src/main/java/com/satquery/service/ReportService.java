package com.satquery.service;

import com.satquery.dto.AnalysisResponse;
import com.satquery.dto.ProjectResponse;
import com.satquery.dto.ReportResponse;
import com.satquery.entity.AnalysisStatus;
import com.satquery.entity.Report;
import com.satquery.entity.ReportStatus;
import com.satquery.exception.InvalidAnalysisRequestException;
import com.satquery.exception.ReportGenerationException;
import com.satquery.exception.ResourceNotFoundException;
import com.satquery.repository.AnalysisRepository;
import com.satquery.repository.ReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class ReportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AnalysisService analysisService;
    private final ProjectService projectService;
    private final ReportGenerator reportGenerator;
    private final ReportRepository reportRepository;
    private final AnalysisRepository analysisRepository;
    private final StorageService storageService;
    private final TransactionTemplate tx;

    public ReportService(AnalysisService analysisService,
                         ProjectService projectService,
                         ReportGenerator reportGenerator,
                         ReportRepository reportRepository,
                         AnalysisRepository analysisRepository,
                         StorageService storageService,
                         PlatformTransactionManager transactionManager) {
        this.analysisService = analysisService;
        this.projectService = projectService;
        this.reportGenerator = reportGenerator;
        this.reportRepository = reportRepository;
        this.analysisRepository = analysisRepository;
        this.storageService = storageService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Generates a PDF report for a COMPLETED analysis and stores its metadata. */
    public ReportResponse generateReport(Long analysisId) {
        AnalysisResponse analysis = analysisService.getAnalysis(analysisId);
        if (analysis.status() != AnalysisStatus.COMPLETED) {
            throw new InvalidAnalysisRequestException(
                    "A report can only be generated for COMPLETED analyses. Current status: " + analysis.status());
        }
        ProjectResponse project = projectService.getProject(analysis.projectId());
        String title = "SatQuery AI Analysis Report - Analysis #" + analysisId;
        log.info("Generating report for analysis {}", analysisId);

        Long reportId = tx.execute(status -> reportRepository.save(Report.builder()
                .analysis(analysisRepository.getReferenceById(analysisId))
                .title(title)
                .generatedAt(LocalDateTime.now())
                .reportStatus(ReportStatus.GENERATING)
                .build()).getId());

        try {
            byte[] pdf = reportGenerator.generate(project, analysis);
            String fileName = "satquery-report-analysis-" + analysisId + "-" + LocalDateTime.now().format(FILE_TS) + ".pdf";
            String key = storageService.store(pdf, StorageService.REPORTS_DIR, fileName);
            String url = storageService.getUrl(key);

            Report saved = tx.execute(status -> {
                Report report = reportRepository.findById(reportId)
                        .orElseThrow(() -> new ResourceNotFoundException("Report not found with id " + reportId));
                report.setStorageKey(key);
                report.setFileUrl(url);
                report.setGeneratedAt(LocalDateTime.now());
                report.setReportStatus(ReportStatus.COMPLETED);
                return reportRepository.save(report);
            });
            log.info("Report {} generated for analysis {} at {}", reportId, analysisId, url);
            return toResponse(saved, analysisId);
        } catch (RuntimeException ex) {
            log.error("Report generation failed for analysis {}", analysisId, ex);
            try {
                tx.executeWithoutResult(status -> reportRepository.findById(reportId).ifPresent(report -> {
                    report.setReportStatus(ReportStatus.FAILED);
                    reportRepository.save(report);
                }));
            } catch (RuntimeException inner) {
                log.error("Could not mark report {} as FAILED", reportId, inner);
            }
            if (ex instanceof ReportGenerationException rge) {
                throw rge;
            }
            throw new ReportGenerationException("Failed to generate report for analysis " + analysisId + ": " + ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id " + reportId));
        return toResponse(report, report.getAnalysis().getId());
    }

    private ReportResponse toResponse(Report report, Long analysisId) {
        return new ReportResponse(report.getId(), analysisId, report.getTitle(), report.getGeneratedAt(),
                report.getFileUrl(), report.getReportStatus());
    }
}
