package com.ewb.notification;

import com.ewb.common.enums.ExecutionStatus;
import com.ewb.common.event.ExecutionOutcomeEvent;
import com.ewb.notification.repository.NotificationRepository;
import com.ewb.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        notificationService.setSimulateOutage(false);
    }

    @Test
    @DisplayName("Notification deduplication: Event delivered twice results in only ONE delivery record")
    void testEventDeduplication() {
        String eventId = "EVT-DUP-TEST-001";
        ExecutionOutcomeEvent event = new ExecutionOutcomeEvent(
                eventId,
                100L,
                1L,
                "CUST-MARIA",
                "EWB-SAL-1001",
                "EWB-SAV-2001",
                new BigDecimal("5000.00"),
                "PHP",
                ZonedDateTime.now(),
                "REF-001",
                ExecutionStatus.SUCCESS,
                null,
                null,
                Instant.now()
        );

        // First delivery
        boolean first = notificationService.consumeOutcomeEvent(event);
        assertTrue(first);
        assertEquals(1, notificationRepository.count());

        // Second duplicate delivery with same eventId
        boolean second = notificationService.consumeOutcomeEvent(event);
        assertTrue(second, "Duplicate event is gracefully handled and acknowledged");
        assertEquals(1, notificationRepository.count(), "Must not create duplicate delivery record");
    }

    @Test
    @DisplayName("Simulated outage isolates notification delivery without affecting system state")
    void testSimulatedOutage() {
        notificationService.setSimulateOutage(true);

        ExecutionOutcomeEvent event = new ExecutionOutcomeEvent(
                "EVT-OUTAGE-001",
                101L,
                2L,
                "CUST-MARIA",
                "EWB-SAL-1001",
                "EWB-SAV-2001",
                new BigDecimal("5000.00"),
                "PHP",
                ZonedDateTime.now(),
                "REF-002",
                ExecutionStatus.SUCCESS,
                null,
                null,
                Instant.now()
        );

        boolean delivered = notificationService.consumeOutcomeEvent(event);
        assertFalse(delivered, "Delivery must fail during outage");
        assertEquals(0, notificationRepository.count());

        // Restore service
        notificationService.setSimulateOutage(false);
        boolean redelivered = notificationService.consumeOutcomeEvent(event);
        assertTrue(redelivered, "Delivery must succeed after outage recovery");
        assertEquals(1, notificationRepository.count());
    }
}
