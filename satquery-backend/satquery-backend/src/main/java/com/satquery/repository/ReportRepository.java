package com.satquery.repository;

import com.satquery.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByAnalysisId(Long analysisId);
}
