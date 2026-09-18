package com.ewb.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record AmendStandingOrderRequest(
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,
        @Min(1) @Max(31)
        Integer dayOfMonth,
        LocalTime executionTime,
        LocalDate endDate,
        String reason
) {}
