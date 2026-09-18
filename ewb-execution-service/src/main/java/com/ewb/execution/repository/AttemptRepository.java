package com.ewb.execution.repository;

import com.ewb.execution.entity.AttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptRepository extends JpaRepository<AttemptEntity, Long> {
    List<AttemptEntity> findByExecutionIdOrderByAttemptNumberAsc(Long executionId);
}
