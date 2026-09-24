package com.satquery.service;

import com.satquery.dto.AnalysisResponse;
import com.satquery.dto.EvidenceResponse;
import com.satquery.dto.ImageResponse;
import com.satquery.dto.ProjectResponse;
import com.satquery.entity.AnalysisType;
import com.satquery.entity.EvidenceType;
import com.satquery.exception.ReportGenerationException;
import com.satquery.util.PdfReportWriter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders a completed analysis as a PDF document (PDFBox). */
@Component
public class ReportGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] generate(ProjectResponse project, AnalysisResponse a) {
        try (PdfReportWriter pdf = new PdfReportWriter()) {
            pdf.title("SATQUERY AI");
            pdf.subtitle("Satellite Intelligence Analysis Report");
            pdf.spacer(6);
            pdf.keyValue("Report generated", LocalDateTime.now().format(TS));

            pdf.heading("1. Project Information");
            pdf.keyValue("Project", project.name());
            pdf.keyValue("Project ID", String.valueOf(project.id()));
            pdf.keyValue("Description", project.description());

            pdf.heading("2. Analysis Date");
            pdf.keyValue("Analysis ID", String.valueOf(a.id()));
            pdf.keyValue("Requested at", format(a.createdAt()));
            pdf.keyValue("Completed at", format(a.completedAt()));

            pdf.heading("3. Input Satellite Images");
            if (a.images() == null || a.images().isEmpty()) {
                pdf.paragraph("No images.");
            } else {
                int index = 1;
                for (ImageResponse image : a.images()) {
                    pdf.bullet("Image " + index++ + ": " + image.fileName() + " (ID " + image.id() + ", " + image.imageType()
                            + (image.satellite() != null ? ", " + image.satellite() : "")
                            + (image.acquisitionDate() != null ? ", acquired " + image.acquisitionDate() : "")
                            + (image.latitude() != null && image.longitude() != null
                            ? ", location " + image.latitude() + ", " + image.longitude() : "")
                            + (image.resolution() != null ? ", resolution " + image.resolution() + " m/px" : "") + ")");
                }
            }

            pdf.heading("4. User Question");
            pdf.paragraph(a.question());

            pdf.heading("5. Analysis Type");
            pdf.keyValue("Executed type", String.valueOf(a.analysisType()));
            pdf.keyValue("Requested type", String.valueOf(a.requestedAnalysisType()));

            pdf.heading("6. AI Answer");
            pdf.paragraph(a.answer());

            pdf.heading("7. Summary / Findings");
            pdf.paragraph(a.summary() == null || a.summary().isBlank() ? "No summary provided." : a.summary());
            if (a.findings() != null) {
                a.findings().forEach(f -> safeBullet(pdf, f));
            }

            pdf.heading("8. Confidence Score");
            pdf.paragraph(a.confidence() == null ? "Not reported" : String.format(Locale.ROOT, "%.1f%%", a.confidence() * 100));

            pdf.heading("9. Model Used");
            pdf.paragraph(a.model() == null || a.model().name() == null ? "Not reported" : a.model().name());

            pdf.heading("10. Model Version");
            pdf.paragraph(a.model() == null || a.model().version() == null ? "Not reported" : a.model().version());

            pdf.heading("11. Evidence");
            List<EvidenceResponse> evidence = a.evidence() == null ? List.of() : a.evidence();
            if (evidence.isEmpty()) {
                pdf.paragraph("No evidence items were returned by the model.");
            } else {
                int index = 1;
                for (EvidenceResponse e : evidence) {
                    pdf.bullet(index++ + ". [" + e.type() + "] " + nullToDash(e.description())
                            + (e.confidence() != null ? " | confidence " + String.format(Locale.ROOT, "%.1f%%", e.confidence() * 100) : "")
                            + regionText(e));
                }
            }

            pdf.heading("12. Detected Change Information");
            if (a.analysisType() == AnalysisType.CHANGE_DETECTION) {
                long regions = evidence.stream()
                        .filter(e -> e.type() == EvidenceType.CHANGED_REGION || e.type() == EvidenceType.TEMPORAL_COMPARISON)
                        .count();
                pdf.keyValue("Changed regions reported", String.valueOf(regions));
                pdf.keyValue("Change map", a.changeMapUrl());
            } else {
                pdf.paragraph("Not applicable for analysis type " + a.analysisType() + ".");
                if (a.changeMapUrl() != null) {
                    pdf.keyValue("Change map", a.changeMapUrl());
                }
            }

            pdf.heading("13. Processing Information");
            pdf.keyValue("Status", String.valueOf(a.status()));
            pdf.keyValue("Processing time", a.processingTimeMs() == null ? "-" : a.processingTimeMs() + " ms");
            pdf.keyValue("Created", format(a.createdAt()));
            pdf.keyValue("Completed", format(a.completedAt()));

            pdf.heading("14. Conclusion");
            pdf.paragraph("The " + a.analysisType() + " analysis for project \"" + project.name() + "\" completed"
                    + (a.model() != null && a.model().name() != null ? " using " + a.model().name() : "")
                    + (a.confidence() != null ? " with an overall confidence of "
                    + String.format(Locale.ROOT, "%.1f%%", a.confidence() * 100) : "")
                    + ". Primary result: " + nullToDash(a.answer()));

            return pdf.toBytes();
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to generate PDF report: " + e.getMessage(), e);
        }
    }

    private void safeBullet(PdfReportWriter pdf, String text) {
        try {
            pdf.bullet(text);
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to write finding to PDF", e);
        }
    }

    private String regionText(EvidenceResponse e) {
        if (e.x() == null || e.y() == null || e.width() == null || e.height() == null) {
            return "";
        }
        return String.format(Locale.ROOT, " | region x=%.0f y=%.0f w=%.0f h=%.0f", e.x(), e.y(), e.width(), e.height());
    }

    private String format(LocalDateTime value) {
        return value == null ? "-" : value.format(TS);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
