package com.satquery.service;

import com.satquery.dto.AnalysisResponse;
import com.satquery.dto.EvidenceResponse;
import com.satquery.dto.ImageResponse;
import com.satquery.dto.ProjectResponse;
import com.satquery.entity.AnalysisStatus;
import com.satquery.entity.AnalysisType;
import com.satquery.entity.EvidenceType;
import com.satquery.entity.ImageType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportGeneratorTest {

    private final ReportGenerator generator = new ReportGenerator();

    private ProjectResponse project() {
        return new ProjectResponse(1L, "Delhi Urban Growth", "Urban study", 2, 1,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private ImageResponse image(long id, String name) {
        return new ImageResponse(id, 1L, name, "http://x/files/" + name, ImageType.OPTICAL, "Sentinel-2",
                LocalDate.of(2024, 1, 1), 28.6, 77.2, 10.0, LocalDateTime.now());
    }

    private AnalysisResponse analysis(AnalysisType type, List<EvidenceResponse> evidence) {
        return new AnalysisResponse(5L, 1L, "Delhi Urban Growth", AnalysisStatus.COMPLETED, type, type,
                "What changed between the two images?", "New buildings appeared in the north-east.",
                "Urban expansion detected", List.of("Finding one", "Finding two"), 0.87,
                new AnalysisResponse.ModelInfo("change-model", "v2"), evidence, "http://ai/change.png",
                List.of(image(10, "before.tif"), image(11, "after.tif")), 1234L,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now(), null);
    }

    private String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void generatesValidPdfContainingKeySections() throws Exception {
        EvidenceResponse ev = new EvidenceResponse(1L, EvidenceType.CHANGED_REGION, "New construction", 0.9,
                10.0, 20.0, 30.0, 40.0, null, null);

        byte[] pdf = generator.generate(project(), analysis(AnalysisType.CHANGE_DETECTION, List.of(ev)));

        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4));
        String content = text(pdf);
        assertTrue(content.contains("Delhi Urban Growth"));
        assertTrue(content.contains("New buildings appeared"));
        assertTrue(content.contains("change-model"));
        assertTrue(content.contains("87.0%"));
        assertTrue(content.contains("New construction"));
        assertTrue(content.contains("Conclusion"));
    }

    @Test
    void handlesMissingOptionalDataWithoutFailing() throws Exception {
        AnalysisResponse a = new AnalysisResponse(6L, 1L, "P", AnalysisStatus.COMPLETED, AnalysisType.VQA,
                AnalysisType.VQA, "Q?", "A.", null, null, null, null, null, null, List.of(image(10, "a.tif")),
                null, LocalDateTime.now(), null, null);

        byte[] pdf = generator.generate(project(), a);

        String content = text(pdf);
        assertTrue(content.contains("Not reported"));
        assertTrue(content.contains("No evidence items"));
        assertTrue(content.contains("Not applicable"));
    }

    @Test
    void paginatesLongContent() throws Exception {
        String longAnswer = "satellite imagery analysis ".repeat(600);
        AnalysisResponse a = new AnalysisResponse(7L, 1L, "P", AnalysisStatus.COMPLETED, AnalysisType.CAPTION,
                AnalysisType.CAPTION, "Q?", longAnswer, "s", List.of(), 0.5, null, List.of(), null,
                List.of(image(10, "a.tif")), 1L, LocalDateTime.now(), LocalDateTime.now(), null);

        byte[] pdf = generator.generate(project(), a);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() > 1);
        }
    }
}
