package com.satquery.repository;

import com.satquery.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByAnalysisId(Long analysisId);

    List<AnalysisResult> findByAnalysisIdIn(Collection<Long> analysisIds);

    void deleteByAnalysisId(Long analysisId);
}
