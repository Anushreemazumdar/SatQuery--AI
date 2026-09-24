package com.satquery.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "evidence", indexes = @Index(name = "idx_evidence_analysis", columnList = "analysis_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvidenceType type;

    @Column(length = 2000)
    private String description;

    private Double confidence;

    /** Bounding box in image pixel coordinates. */
    private Double x;
    private Double y;
    private Double width;
    private Double height;

    /** Optional GeoJSON / polygon geometry serialised as JSON. */
    @Column(name = "geometry_json", columnDefinition = "TEXT")
    private String geometry;

    @Column(name = "evidence_image_url", length = 1000)
    private String evidenceImageUrl;
}
