package com.ewb.order.repository;

import com.ewb.common.enums.InstructionStatus;
import com.ewb.order.entity.StandingOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface StandingOrderRepository extends JpaRepository<StandingOrderEntity, Long> {
    List<StandingOrderEntity> findByCustomerId(String customerId);

    @Query("SELECT s FROM StandingOrderEntity s WHERE s.status = :status AND s.nextExecutionTime IS NOT NULL AND s.nextExecutionTime <= :cutoff")
    List<StandingOrderEntity> findDueInstructions(@Param("status") InstructionStatus status, @Param("cutoff") ZonedDateTime cutoff);
}
