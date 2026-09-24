package com.satquery.repository;

import com.satquery.entity.SatelliteImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SatelliteImageRepository extends JpaRepository<SatelliteImage, Long> {

    List<SatelliteImage> findByProjectIdOrderByUploadedAtDesc(Long projectId);

    long countByProjectId(Long projectId);
}
