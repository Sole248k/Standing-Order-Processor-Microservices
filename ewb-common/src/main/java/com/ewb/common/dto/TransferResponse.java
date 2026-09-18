package com.ewb.common.dto;

import com.ewb.common.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        String reference,
        String idempotencyKey,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String errorCode,
        String errorMessage,
        Instant timestamp
) {}
