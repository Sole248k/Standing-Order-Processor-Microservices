package com.ewb.common.dto;

import com.ewb.common.enums.InstructionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public record StandingOrderDto(
        Long id,
        String customerId,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        String frequency,
        Integer dayOfMonth,
        LocalTime executionTime,
        String timeZone,
        LocalDate startDate,
        LocalDate endDate,
        InstructionStatus status,
        Integer version,
        ZonedDateTime nextExecutionTime,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {}
