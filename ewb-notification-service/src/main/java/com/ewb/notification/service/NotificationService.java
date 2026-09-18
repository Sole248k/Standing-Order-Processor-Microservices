package com.ewb.notification.service;

import com.ewb.common.dto.NotificationDto;
import com.ewb.common.enums.ExecutionStatus;
import com.ewb.common.event.ExecutionOutcomeEvent;
import com.ewb.notification.entity.NotificationDeliveryEntity;
import com.ewb.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final AtomicBoolean simulateOutage = new AtomicBoolean(false);

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void setSimulateOutage(boolean outage) {
        this.simulateOutage.set(outage);
        log.warn("Notification outage simulation set to: {}", outage);
    }

    public boolean isOutageSimulated() {
        return simulateOutage.get();
    }

    public List<NotificationDto> getAllNotifications() {
        return notificationRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public List<NotificationDto> getNotificationsByCustomer(String customerId) {
        return notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public boolean consumeOutcomeEvent(ExecutionOutcomeEvent event) {
        log.info("Consuming outcome event: {} for standing order #{}, status: {}",
                event.eventId(), event.standingOrderId(), event.status());

        // 1. Check for duplicate event delivery
        Optional<NotificationDeliveryEntity> existing = notificationRepository.findByEventId(event.eventId());
        if (existing.isPresent()) {
            log.info("Duplicate event {} ignored (already delivered). Deduplication successful.", event.eventId());
            return true;
        }

        // 2. Check for simulated outage
        if (simulateOutage.get()) {
            log.warn("Simulated notification outage in effect! Rejecting delivery for event {}", event.eventId());
            return false;
        }

        // 3. Compose message
        String recipient = event.customerId().toLowerCase() + "@ewb.com.ph";
        String subject;
        String messageBody;

        if (event.status() == ExecutionStatus.SUCCESS) {
            subject = "EastWest Bank: Standing Order Execution Successful";
            messageBody = String.format(
                    "Dear Customer, your scheduled transfer of %s %s from %s to %s has been successfully executed. Reference: %s. Scheduled Time: %s.",
                    event.currency(), event.amount(), event.sourceAccountId(), event.destinationAccountId(),
                    event.paymentReference(), event.scheduledTime()
            );
        } else {
            subject = "EastWest Bank: Standing Order Transfer Notice";
            messageBody = String.format(
                    "Dear Customer, your scheduled transfer of %s %s from %s to %s could not be processed. Reason: %s (%s). Your recurring instruction remains active.",
                    event.currency(), event.amount(), event.sourceAccountId(), event.destinationAccountId(),
                    event.errorCode(), event.errorMessage()
            );
        }

        // 4. Save delivery record
        NotificationDeliveryEntity delivery = new NotificationDeliveryEntity(
                event.eventId(),
                event.customerId(),
                "SMS_AND_EMAIL",
                recipient,
                subject,
                messageBody,
                "SENT",
                Instant.now()
        );

        notificationRepository.save(delivery);
        log.info("Customer notification delivered successfully to {} for event {}", recipient, event.eventId());
        return true;
    }

    private NotificationDto toDto(NotificationDeliveryEntity entity) {
        return new NotificationDto(
                entity.getId(),
                entity.getEventId(),
                entity.getCustomerId(),
                entity.getChannel(),
                entity.getRecipient(),
                entity.getSubject(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
