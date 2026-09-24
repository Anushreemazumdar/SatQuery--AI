package com.satquery.repository;

import com.satquery.entity.Analysis;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    @EntityGraph(attributePaths = {"project", "inputImages"})
    Optional<Analysis> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"inputImages"})
    List<Analysis> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<Analysis> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    boolean existsByInputImagesId(Long imageId);
}
