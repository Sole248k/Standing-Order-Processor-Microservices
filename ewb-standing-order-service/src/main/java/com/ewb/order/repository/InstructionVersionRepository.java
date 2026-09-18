package com.ewb.order.repository;

import com.ewb.order.entity.InstructionVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstructionVersionRepository extends JpaRepository<InstructionVersionEntity, Long> {
    List<InstructionVersionEntity> findByStandingOrderIdOrderByVersionNumberAsc(Long standingOrderId);
}
