package com.satquery.repository;

import com.satquery.entity.AIModel;
import com.satquery.entity.ModelTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AIModelRepository extends JpaRepository<AIModel, Long> {

    Optional<AIModel> findFirstByTaskAndActiveTrueOrderByCreatedAtDesc(ModelTask task);

    List<AIModel> findAllByOrderByTaskAscCreatedAtDesc();
}
