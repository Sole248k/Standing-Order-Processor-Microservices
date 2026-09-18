package com.ewb.execution.repository;

import com.ewb.common.enums.ExecutionStatus;
import com.ewb.execution.entity.ExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutionRepository extends JpaRepository<ExecutionEntity, Long> {
    List<ExecutionEntity> findByStandingOrderIdOrderByScheduledTimeDesc(Long standingOrderId);

    Optional<ExecutionEntity> findByStandingOrderIdAndScheduledTime(Long standingOrderId, ZonedDateTime scheduledTime);

    Optional<ExecutionEntity> findByIdempotencyKey(String idempotencyKey);

    List<ExecutionEntity> findByStatus(ExecutionStatus status);

    @Query("SELECT e FROM ExecutionEntity e WHERE e.status = 'CLAIMED' AND e.claimExpiresAt < :now")
    List<ExecutionEntity> findExpiredClaims(@Param("now") Instant now);
}
