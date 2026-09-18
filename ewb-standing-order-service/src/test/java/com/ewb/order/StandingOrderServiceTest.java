package com.ewb.order;

import com.ewb.common.dto.AmendStandingOrderRequest;
import com.ewb.common.dto.CreateStandingOrderRequest;
import com.ewb.common.dto.StandingOrderDto;
import com.ewb.common.enums.InstructionStatus;
import com.ewb.order.entity.InstructionVersionEntity;
import com.ewb.order.repository.InstructionVersionRepository;
import com.ewb.order.repository.StandingOrderRepository;
import com.ewb.order.service.StandingOrderService;
import com.ewb.order.util.ScheduleCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StandingOrderServiceTest {

    @Autowired
    private StandingOrderService standingOrderService;

    @Autowired
    private StandingOrderRepository standingOrderRepository;

    @Autowired
    private InstructionVersionRepository versionRepository;

    @BeforeEach
    void setUp() {
        versionRepository.deleteAll();
        standingOrderRepository.deleteAll();
    }

    @Test
    @DisplayName("Scenario 7: Month-end rule correctly clamps day 31 to last day of shorter month")
    void testMonthEndClamping() {
        LocalTime time = LocalTime.of(9, 0);
        String timeZone = "Asia/Manila";
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        ZonedDateTime refTime = ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneId.of(timeZone));

        // Day 31 in February 2026 (non leap year = 28 days)
        ZonedDateTime next = ScheduleCalculator.calculateNextExecution(31, time, timeZone, startDate, null, refTime);

        assertNotNull(next);
        assertEquals(28, next.getDayOfMonth());
        assertEquals(Month.FEBRUARY, next.getMonth());
        assertEquals(2026, next.getYear());

        // Subsequent in March 2026 (has 31 days)
        ZonedDateTime march = ScheduleCalculator.calculateSubsequentExecution(31, time, timeZone, null, next);
        assertNotNull(march);
        assertEquals(31, march.getDayOfMonth());
        assertEquals(Month.MARCH, march.getMonth());

        // Subsequent in April 2026 (has 30 days)
        ZonedDateTime april = ScheduleCalculator.calculateSubsequentExecution(31, time, timeZone, null, march);
        assertNotNull(april);
        assertEquals(30, april.getDayOfMonth());
        assertEquals(Month.APRIL, april.getMonth());
    }

    @Test
    @DisplayName("Create standing order and verify version 1 snapshot")
    void testCreateInstructionAndVersioning() {
        CreateStandingOrderRequest req = new CreateStandingOrderRequest(
                "EWB-SAL-1001",
                "EWB-SAV-2001",
                new BigDecimal("5000.00"),
                "PHP",
                "MONTHLY",
                25,
                LocalTime.of(9, 0),
                "Asia/Manila",
                LocalDate.of(2026, 10, 25),
                null
        );

        StandingOrderDto created = standingOrderService.createInstruction(req, "CUST-MARIA");

        assertNotNull(created.id());
        assertEquals(InstructionStatus.ACTIVE, created.status());
        assertEquals(1, created.version());

        List<InstructionVersionEntity> versions = standingOrderService.getVersions(created.id());
        assertEquals(1, versions.size());
        assertEquals("CREATED", versions.get(0).getAction());
    }

    @Test
    @DisplayName("Lifecycle: Pause, Resume, Amend, Cancel increments versions")
    void testLifecycleVersioning() {
        CreateStandingOrderRequest req = new CreateStandingOrderRequest(
                "EWB-SAL-1001",
                "EWB-SAV-2001",
                new BigDecimal("5000.00"),
                "PHP",
                "MONTHLY",
                25,
                LocalTime.of(9, 0),
                "Asia/Manila",
                LocalDate.of(2026, 10, 25),
                null
        );

        StandingOrderDto created = standingOrderService.createInstruction(req, "CUST-MARIA");
        Long id = created.id();

        // Pause -> version 2
        StandingOrderDto paused = standingOrderService.pauseInstruction(id, "CUST-MARIA");
        assertEquals(InstructionStatus.PAUSED, paused.status());
        assertEquals(2, paused.version());

        // Resume -> version 3
        StandingOrderDto resumed = standingOrderService.resumeInstruction(id, "CUST-MARIA");
        assertEquals(InstructionStatus.ACTIVE, resumed.status());
        assertEquals(3, resumed.version());

        // Amend -> version 4
        AmendStandingOrderRequest amend = new AmendStandingOrderRequest(
                new BigDecimal("7500.00"),
                26,
                LocalTime.of(10, 0),
                null,
                "Increase monthly savings"
        );
        StandingOrderDto amended = standingOrderService.amendInstruction(id, amend, "CUST-MARIA");
        assertEquals(new BigDecimal("7500.00"), amended.amount());
        assertEquals(4, amended.version());

        // Cancel -> version 5
        StandingOrderDto cancelled = standingOrderService.cancelInstruction(id, "CUST-MARIA");
        assertEquals(InstructionStatus.CANCELLED, cancelled.status());
        assertEquals(5, cancelled.version());
        assertNull(cancelled.nextExecutionTime());

        List<InstructionVersionEntity> versions = standingOrderService.getVersions(id);
        assertEquals(5, versions.size());
    }
}
