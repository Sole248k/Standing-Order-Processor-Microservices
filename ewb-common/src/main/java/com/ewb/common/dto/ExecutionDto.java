package com.ewb.common.dto;

import com.ewb.common.enums.ExecutionStatus;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

public record ExecutionDto(
        Long id,
        Long standingOrderId,
        ZonedDateTime scheduledTime,
        Integer instructionVersion,
        ExecutionStatus status,
        String paymentReference,
        String idempotencyKey,
        String claimedBy,
        Instant claimExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        List<AttemptDto> attempts
) {
    public record AttemptDto(
            Long id,
            Integer attemptNumber,
            Instant timestamp,
            String responseCategory,
            String errorCode,
            String errorMessage
    ) {}
}
