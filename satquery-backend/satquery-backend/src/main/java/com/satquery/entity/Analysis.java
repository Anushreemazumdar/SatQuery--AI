package com.satquery.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analysis", indexes = {
        @Index(name = "idx_analysis_project", columnList = "project_id"),
        @Index(name = "idx_analysis_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Ordered input images (for CHANGE_DETECTION: index 0 = before, index 1 = after). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "analysis_images",
            joinColumns = @JoinColumn(name = "analysis_id"),
            inverseJoinColumns = @JoinColumn(name = "image_id"))
    @OrderColumn(name = "image_order")
    @Builder.Default
    private List<SatelliteImage> inputImages = new ArrayList<>();

    @Column(nullable = false, length = 2000)
    private String question;

    /** The analysis type that was actually executed (AUTO is resolved before execution). */
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 30)
    private AnalysisType analysisType;

    /** The analysis type the client asked for (may be AUTO). */
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_analysis_type", nullable = false, length = 30)
    private AnalysisType requestedAnalysisType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    private Double confidence;

    @Column(name = "model_used", length = 150)
    private String modelUsed;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
