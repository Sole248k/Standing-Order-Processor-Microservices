package com.ewb.common.event;

import com.ewb.common.enums.ExecutionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;

public record ExecutionOutcomeEvent(
        String eventId,
        Long executionId,
        Long standingOrderId,
        String customerId,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        ZonedDateTime scheduledTime,
        String paymentReference,
        ExecutionStatus status,
        String errorCode,
        String errorMessage,
        Instant occurredAt
) {}
