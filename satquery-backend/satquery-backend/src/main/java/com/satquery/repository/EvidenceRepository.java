package com.satquery.repository;

import com.satquery.entity.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findByAnalysisIdOrderByIdAsc(Long analysisId);

    void deleteByAnalysisId(Long analysisId);
}
