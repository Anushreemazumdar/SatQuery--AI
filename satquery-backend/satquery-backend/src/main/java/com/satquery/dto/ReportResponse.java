package com.satquery.dto;

import com.satquery.entity.ReportStatus;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        Long analysisId,
        String title,
        LocalDateTime generatedAt,
        String fileUrl,
        ReportStatus reportStatus) {
}
