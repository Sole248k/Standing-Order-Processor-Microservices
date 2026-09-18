package com.ewb.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateStandingOrderRequest(
        @NotBlank(message = "Source account is required") String sourceAccountId,
        @NotBlank(message = "Destination account is required") String destinationAccountId,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero") BigDecimal amount,
        @NotBlank(message = "Currency is required") String currency,
        @NotBlank(message = "Frequency is required") String frequency,
        @NotNull(message = "Day of month is required")
        @Min(1) @Max(31) Integer dayOfMonth,
        @NotNull(message = "Execution time is required") LocalTime executionTime,
        @NotBlank(message = "Time zone is required") String timeZone,
        @NotNull(message = "Start date is required") LocalDate startDate,
        LocalDate endDate
) {}
