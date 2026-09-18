package com.ewb.order.service;

import com.ewb.common.dto.AmendStandingOrderRequest;
import com.ewb.common.dto.CreateStandingOrderRequest;
import com.ewb.common.dto.StandingOrderDto;
import com.ewb.common.enums.InstructionStatus;
import com.ewb.order.entity.InstructionVersionEntity;
import com.ewb.order.entity.StandingOrderEntity;
import com.ewb.order.repository.InstructionVersionRepository;
import com.ewb.order.repository.StandingOrderRepository;
import com.ewb.order.util.ScheduleCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class StandingOrderService {

    private static final Logger log = LoggerFactory.getLogger(StandingOrderService.class);

    private final StandingOrderRepository standingOrderRepository;
    private final InstructionVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public StandingOrderService(StandingOrderRepository standingOrderRepository,
                                InstructionVersionRepository versionRepository,
                                ObjectMapper objectMapper) {
        this.standingOrderRepository = standingOrderRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    public List<StandingOrderDto> getInstructionsByCustomer(String customerId) {
        return standingOrderRepository.findByCustomerId(customerId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<StandingOrderDto> getAllInstructions() {
        return standingOrderRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public Optional<StandingOrderDto> getInstruction(Long id) {
        return standingOrderRepository.findById(id).map(this::toDto);
    }

    public List<InstructionVersionEntity> getVersions(Long standingOrderId) {
        return versionRepository.findByStandingOrderIdOrderByVersionNumberAsc(standingOrderId);
    }

    public List<StandingOrderDto> getDueInstructions(ZonedDateTime cutoff) {
        return standingOrderRepository.findDueInstructions(InstructionStatus.ACTIVE, cutoff).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public StandingOrderDto createInstruction(CreateStandingOrderRequest req, String customerId) {
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be strictly greater than zero");
        }
        if (req.sourceAccountId().equalsIgnoreCase(req.destinationAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(req.timeZone()));
        ZonedDateTime nextExecution = ScheduleCalculator.calculateNextExecution(
                req.dayOfMonth(),
                req.executionTime(),
                req.timeZone(),
                req.startDate(),
                req.endDate(),
                now
        );

        StandingOrderEntity entity = new StandingOrderEntity();
        entity.setCustomerId(customerId);
        entity.setSourceAccountId(req.sourceAccountId());
        entity.setDestinationAccountId(req.destinationAccountId());
        entity.setAmount(req.amount());
        entity.setCurrency(req.currency());
        entity.setFrequency(req.frequency());
        entity.setDayOfMonth(req.dayOfMonth());
        entity.setExecutionTime(req.executionTime());
        entity.setTimeZone(req.timeZone());
        entity.setStartDate(req.startDate());
        entity.setEndDate(req.endDate());
        entity.setStatus(InstructionStatus.ACTIVE);
        entity.setVersion(1);
        entity.setNextExecutionTime(nextExecution);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        StandingOrderEntity saved = standingOrderRepository.save(entity);
        recordVersion(saved, "CREATED", customerId, "Initial instruction creation");

        log.info("Created standing order #{} for customer {}, next occurrence: {}",
                saved.getId(), customerId, saved.getNextExecutionTime());
        return toDto(saved);
    }

    @Transactional
    public StandingOrderDto amendInstruction(Long id, AmendStandingOrderRequest req, String actorId) {
        StandingOrderEntity order = standingOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Standing order #" + id + " not found"));

        if (order.getStatus() == InstructionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot amend a cancelled standing order");
        }

        if (req.amount() != null) {
            if (req.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }
            order.setAmount(req.amount());
        }
        if (req.dayOfMonth() != null) {
            order.setDayOfMonth(req.dayOfMonth());
        }
        if (req.executionTime() != null) {
            order.setExecutionTime(req.executionTime());
        }
        if (req.endDate() != null) {
            order.setEndDate(req.endDate());
        }

        order.setVersion(order.getVersion() + 1);
        order.setUpdatedAt(ZonedDateTime.now(ZoneId.of(order.getTimeZone())));

        // Recalculate next occurrence if active
        if (order.getStatus() == InstructionStatus.ACTIVE) {
            ZonedDateTime next = ScheduleCalculator.calculateNextExecution(
                    order.getDayOfMonth(),
                    order.getExecutionTime(),
                    order.getTimeZone(),
                    order.getStartDate(),
                    order.getEndDate(),
                    order.getUpdatedAt()
            );
            order.setNextExecutionTime(next);
        }

        StandingOrderEntity saved = standingOrderRepository.save(order);
        String reason = req.reason() != null ? req.reason() : "Instruction parameters amended";
        recordVersion(saved, "AMENDED", actorId, reason);

        log.info("Amended standing order #{} to version {}", saved.getId(), saved.getVersion());
        return toDto(saved);
    }

    @Transactional
    public StandingOrderDto pauseInstruction(Long id, String actorId) {
        StandingOrderEntity order = standingOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Standing order #" + id + " not found"));

        if (order.getStatus() == InstructionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot pause a cancelled standing order");
        }

        order.setStatus(InstructionStatus.PAUSED);
        order.setVersion(order.getVersion() + 1);
        order.setUpdatedAt(ZonedDateTime.now(ZoneId.of(order.getTimeZone())));

        StandingOrderEntity saved = standingOrderRepository.save(order);
        recordVersion(saved, "PAUSED", actorId, "Instruction paused by user/operations");

        log.info("Paused standing order #{}", id);
        return toDto(saved);
    }

    @Transactional
    public StandingOrderDto resumeInstruction(Long id, String actorId) {
        StandingOrderEntity order = standingOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Standing order #" + id + " not found"));

        if (order.getStatus() == InstructionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot resume a cancelled standing order");
        }

        order.setStatus(InstructionStatus.ACTIVE);
        order.setVersion(order.getVersion() + 1);
        order.setUpdatedAt(ZonedDateTime.now(ZoneId.of(order.getTimeZone())));

        // Recalculate next execution from now
        ZonedDateTime next = ScheduleCalculator.calculateNextExecution(
                order.getDayOfMonth(),
                order.getExecutionTime(),
                order.getTimeZone(),
                order.getStartDate(),
                order.getEndDate(),
                order.getUpdatedAt()
        );
        order.setNextExecutionTime(next);

        StandingOrderEntity saved = standingOrderRepository.save(order);
        recordVersion(saved, "RESUMED", actorId, "Instruction resumed");

        log.info("Resumed standing order #{}, next run: {}", id, next);
        return toDto(saved);
    }

    @Transactional
    public StandingOrderDto cancelInstruction(Long id, String actorId) {
        StandingOrderEntity order = standingOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Standing order #" + id + " not found"));

        order.setStatus(InstructionStatus.CANCELLED);
        order.setNextExecutionTime(null);
        order.setVersion(order.getVersion() + 1);
        order.setUpdatedAt(ZonedDateTime.now(ZoneId.of(order.getTimeZone())));

        StandingOrderEntity saved = standingOrderRepository.save(order);
        recordVersion(saved, "CANCELLED", actorId, "Instruction cancelled");

        log.info("Cancelled standing order #{}", id);
        return toDto(saved);
    }

    @Transactional
    public void advanceNextExecutionTime(Long id) {
        StandingOrderEntity order = standingOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Standing order #" + id + " not found"));

        if (order.getStatus() != InstructionStatus.ACTIVE || order.getNextExecutionTime() == null) {
            return;
        }

        ZonedDateTime currentExec = order.getNextExecutionTime();
        ZonedDateTime subsequent = ScheduleCalculator.calculateSubsequentExecution(
                order.getDayOfMonth(),
                order.getExecutionTime(),
                order.getTimeZone(),
                order.getEndDate(),
                currentExec
        );

        order.setNextExecutionTime(subsequent);
        order.setUpdatedAt(ZonedDateTime.now(ZoneId.of(order.getTimeZone())));

        if (subsequent == null) {
            log.info("Standing order #{} reached its end date. Auto-completing.", id);
            order.setStatus(InstructionStatus.CANCELLED);
        }

        standingOrderRepository.save(order);
        log.info("Advanced schedule for order #{}. New next execution time: {}", id, subsequent);
    }

    private void recordVersion(StandingOrderEntity entity, String action, String modifiedBy, String reason) {
        try {
            String snapshotJson = objectMapper.writeValueAsString(entity);
            InstructionVersionEntity version = new InstructionVersionEntity(
                    entity.getId(),
                    entity.getVersion(),
                    snapshotJson,
                    action,
                    modifiedBy != null ? modifiedBy : "SYSTEM",
                    Instant.now(),
                    reason
            );
            versionRepository.save(version);
        } catch (Exception e) {
            log.error("Failed to record version snapshot for standing order #{}", entity.getId(), e);
        }
    }

    public StandingOrderDto toDto(StandingOrderEntity e) {
        return new StandingOrderDto(
                e.getId(),
                e.getCustomerId(),
                e.getSourceAccountId(),
                e.getDestinationAccountId(),
                e.getAmount(),
                e.getCurrency(),
                e.getFrequency(),
                e.getDayOfMonth(),
                e.getExecutionTime(),
                e.getTimeZone(),
                e.getStartDate(),
                e.getEndDate(),
                e.getStatus(),
                e.getVersion(),
                e.getNextExecutionTime(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
