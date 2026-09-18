package com.ewb.common.dto;

import java.time.Instant;

public record NotificationDto(
        Long id,
        String eventId,
        String customerId,
        String channel,
        String recipient,
        String subject,
        String message,
        String status,
        Instant createdAt
) {}
