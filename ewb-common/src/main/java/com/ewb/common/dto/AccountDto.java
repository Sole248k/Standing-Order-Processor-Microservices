package com.ewb.common.dto;

import com.ewb.common.enums.AccountStatus;

import java.math.BigDecimal;

public record AccountDto(
        String accountId,
        String accountName,
        String customerId,
        BigDecimal balance,
        String currency,
        AccountStatus status
) {}
