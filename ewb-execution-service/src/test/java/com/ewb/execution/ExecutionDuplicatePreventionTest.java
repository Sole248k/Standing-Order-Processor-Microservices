package com.ewb.execution;

import com.ewb.common.enums.ExecutionStatus;
import com.ewb.execution.entity.ExecutionEntity;
import com.ewb.execution.repository.ExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ExecutionDuplicatePreventionTest {

    @Autowired
    private ExecutionRepository executionRepository;

    @BeforeEach
    void setUp() {
        executionRepository.deleteAll();
    }

    @Test
    @DisplayName("Duplicate Prevention: DB uniqueness constraint permits only ONE execution per order and scheduled occurrence")
    void testDuplicateExecutionConstraint() {
        Long orderId = 101L;
        ZonedDateTime scheduledTime = ZonedDateTime.of(2026, 10, 25, 9, 0, 0, 0, ZoneId.of("Asia/Manila"));

        ExecutionEntity first = new ExecutionEntity();
        first.setStandingOrderId(orderId);
        first.setScheduledTime(scheduledTime);
        first.setInstructionVersion(1);
        first.setStatus(ExecutionStatus.CLAIMED);
        first.setIdempotencyKey("SO-101-2026-10-25T01:00:00Z");
        first.setClaimedBy("worker-1");
        first.setClaimExpiresAt(Instant.now().plusSeconds(300));
        first.setCreatedAt(Instant.now());
        first.setUpdatedAt(Instant.now());

        executionRepository.saveAndFlush(first);

        // Attempt second concurrent insert for the exact same order and scheduled time
        ExecutionEntity duplicate = new ExecutionEntity();
        duplicate.setStandingOrderId(orderId);
        duplicate.setScheduledTime(scheduledTime);
        duplicate.setInstructionVersion(1);
        duplicate.setStatus(ExecutionStatus.CLAIMED);
        duplicate.setIdempotencyKey("SO-101-2026-10-25T01:00:00Z");
        duplicate.setClaimedBy("worker-2");
        duplicate.setClaimExpiresAt(Instant.now().plusSeconds(300));
        duplicate.setCreatedAt(Instant.now());
        duplicate.setUpdatedAt(Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> {
            executionRepository.saveAndFlush(duplicate);
        }, "Database unique constraint UK_ORDER_SCHEDULED_TIME must prevent duplicate creation");

        // Exactly one record remains
        assertEquals(1, executionRepository.count());
    }
}
