package com.ewb.execution.service;

import com.ewb.common.dto.*;
import com.ewb.common.enums.ExecutionStatus;
import com.ewb.common.enums.InstructionStatus;
import com.ewb.common.enums.TransferStatus;
import com.ewb.common.event.ExecutionOutcomeEvent;
import com.ewb.execution.client.NotificationClient;
import com.ewb.execution.client.PaymentClient;
import com.ewb.execution.client.StandingOrderClient;
import com.ewb.execution.entity.AttemptEntity;
import com.ewb.execution.entity.ExecutionEntity;
import com.ewb.execution.entity.OutboxEventEntity;
import com.ewb.execution.repository.AttemptRepository;
import com.ewb.execution.repository.ExecutionRepository;
import com.ewb.execution.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionRepository executionRepository;
    private final AttemptRepository attemptRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final StandingOrderClient standingOrderClient;
    private final PaymentClient paymentClient;
    private final NotificationClient notificationClient;
    private final ObjectMapper objectMapper;

    public ExecutionService(ExecutionRepository executionRepository,
                            AttemptRepository attemptRepository,
                            OutboxEventRepository outboxEventRepository,
                            StandingOrderClient standingOrderClient,
                            PaymentClient paymentClient,
                            NotificationClient notificationClient,
                            ObjectMapper objectMapper) {
        this.executionRepository = executionRepository;
        this.attemptRepository = attemptRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.standingOrderClient = standingOrderClient;
        this.paymentClient = paymentClient;
        this.notificationClient = notificationClient;
        this.objectMapper = objectMapper;
    }

    public List<ExecutionDto> getAllExecutions() {
        return executionRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public List<ExecutionDto> getExecutionsForOrder(Long standingOrderId) {
        return executionRepository.findByStandingOrderIdOrderByScheduledTimeDesc(standingOrderId).stream()
                .map(this::toDto)
                .toList();
    }

    public Optional<ExecutionDto> getExecution(Long id) {
        return executionRepository.findById(id).map(this::toDto);
    }

    public List<OutboxEventEntity> getOutboxEvents() {
        return outboxEventRepository.findAll();
    }

    /**
     * Automatic discovery poll running every 10 seconds.
     */
    @Scheduled(fixedDelay = 10000)
    public void scheduledDiscovery() {
        discoverAndExecuteDueInstructions();
        dispatchOutboxEvents();
    }

    /**
     * Discover due instructions, deduplicate, claim, and process transfers.
     */
    public int discoverAndExecuteDueInstructions() {
        ZonedDateTime cutoff = ZonedDateTime.now().plusMinutes(5);
        List<StandingOrderDto> dueOrders = standingOrderClient.fetchDueInstructions(cutoff);
        if (dueOrders.isEmpty()) {
            return 0;
        }

        log.info("Discovered {} due standing order instruction(s)", dueOrders.size());
        int executedCount = 0;

        for (StandingOrderDto order : dueOrders) {
            try {
                boolean executed = processOrderOccurrence(order);
                if (executed) {
                    executedCount++;
                }
            } catch (Exception e) {
                log.error("Error processing standing order #{}: {}", order.id(), e.getMessage(), e);
            }
        }

        return executedCount;
    }

    private boolean processOrderOccurrence(StandingOrderDto order) {
        ZonedDateTime scheduledTime = order.nextExecutionTime();
        if (scheduledTime == null) return false;

        // Stable idempotency key: standing-order-ID + scheduled-occurrence-UTC
        String utcString = scheduledTime.withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
        String idempotencyKey = "SO-" + order.id() + "-" + utcString;

        // 1. Deduplicate check
        Optional<ExecutionEntity> existing = executionRepository.findByStandingOrderIdAndScheduledTime(order.id(), scheduledTime);
        if (existing.isPresent()) {
            log.info("Execution already exists for order #{} at {}. Skipping duplicate.", order.id(), scheduledTime);
            return false;
        }

        // 2. Create Execution record (enforced by DB unique constraint)
        ExecutionEntity execution = new ExecutionEntity();
        execution.setStandingOrderId(order.id());
        execution.setScheduledTime(scheduledTime);
        execution.setInstructionVersion(order.version());
        execution.setStatus(ExecutionStatus.CLAIMED);
        execution.setIdempotencyKey(idempotencyKey);
        execution.setClaimedBy("worker-" + Thread.currentThread().getName());
        execution.setClaimExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        execution.setCreatedAt(Instant.now());
        execution.setUpdatedAt(Instant.now());

        try {
            execution = executionRepository.saveAndFlush(execution);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate creation prevented for order #{} at {}", order.id(), scheduledTime);
            return false;
        }

        // 3. Revalidate instruction
        StandingOrderDto freshOrder = standingOrderClient.getOrder(order.id());
        if (freshOrder == null || freshOrder.status() != InstructionStatus.ACTIVE) {
            log.warn("Standing order #{} is no longer active (status: {}). Cancelling execution.",
                    order.id(), (freshOrder != null ? freshOrder.status() : "NOT_FOUND"));
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setUpdatedAt(Instant.now());
            executionRepository.save(execution);

            recordAttempt(execution.getId(), 1, "BUSINESS_ERROR", "INSTRUCTION_INACTIVE",
                    "Instruction is no longer active at time of execution");
            return false;
        }

        // 4. Submit Payment
        TransferRequest transferRequest = new TransferRequest(
                freshOrder.sourceAccountId(),
                freshOrder.destinationAccountId(),
                freshOrder.amount(),
                freshOrder.currency(),
                idempotencyKey,
                "REF-" + execution.getId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                freshOrder.id(),
                execution.getId(),
                false
        );

        TransferResponse transferResponse = paymentClient.executeTransfer(transferRequest);

        // 5. Handle Outcome
        handleTransferOutcome(execution, freshOrder, transferResponse, 1);
        return true;
    }

    private void handleTransferOutcome(ExecutionEntity execution, StandingOrderDto order, TransferResponse response, int attemptNumber) {
        Instant now = Instant.now();
        execution.setUpdatedAt(now);

        if (response != null && response.status() == TransferStatus.SUCCESS) {
            execution.setStatus(ExecutionStatus.SUCCESS);
            execution.setPaymentReference(response.reference());
            executionRepository.save(execution);

            recordAttempt(execution.getId(), attemptNumber, "SUCCESS", null, "Transfer completed successfully");
            createOutboxEvent(execution, order, ExecutionStatus.SUCCESS, null, "Transfer executed successfully");
            standingOrderClient.advanceSchedule(order.id());

            log.info("Execution #{} for order #{} succeeded. Payment ref: {}", execution.getId(), order.id(), response.reference());
        } else if (response != null && response.status() == TransferStatus.FAILED) {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setPaymentReference(response.reference());
            executionRepository.save(execution);

            recordAttempt(execution.getId(), attemptNumber, "BUSINESS_ERROR", response.errorCode(), response.errorMessage());
            createOutboxEvent(execution, order, ExecutionStatus.FAILED, response.errorCode(), response.errorMessage());

            // Section 5: "Insufficient funds: Fail this occurrence; keep the recurring instruction active"
            standingOrderClient.advanceSchedule(order.id());

            log.warn("Execution #{} for order #{} failed: [{}] {}", execution.getId(), order.id(), response.errorCode(), response.errorMessage());
        } else {
            // UNCERTAIN / Lost Response / Timeout
            execution.setStatus(ExecutionStatus.UNRESOLVED);
            if (response != null && response.reference() != null) {
                execution.setPaymentReference(response.reference());
            }
            executionRepository.save(execution);

            String code = (response != null) ? response.errorCode() : "TIMEOUT";
            String msg = (response != null) ? response.errorMessage() : "Transfer outcome is uncertain. Query reference to resolve.";
            recordAttempt(execution.getId(), attemptNumber, "TIMEOUT", code, msg);

            log.warn("Execution #{} for order #{} outcome is uncertain. Flagged as UNRESOLVED for recovery.", execution.getId(), order.id());
        }
    }

    /**
     * Recovery endpoint to resolve an uncertain execution without duplicate payment.
     */
    @Transactional
    public ExecutionDto resolveUncertainExecution(Long executionId) {
        ExecutionEntity execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution #" + executionId + " not found"));

        if (execution.getStatus() != ExecutionStatus.UNRESOLVED) {
            log.info("Execution #{} is already in state {}, no recovery needed", executionId, execution.getStatus());
            return toDto(execution);
        }

        log.info("Resolving uncertain execution #{} with idempotencyKey: {}", executionId, execution.getIdempotencyKey());

        Optional<TransferResponse> transferOpt = paymentClient.queryByIdempotencyKey(execution.getIdempotencyKey());
        if (transferOpt.isEmpty() && execution.getPaymentReference() != null) {
            transferOpt = paymentClient.queryByReference(execution.getPaymentReference());
        }

        List<AttemptEntity> attempts = attemptRepository.findByExecutionIdOrderByAttemptNumberAsc(executionId);
        int nextAttemptNum = attempts.size() + 1;

        if (transferOpt.isPresent()) {
            TransferResponse transfer = transferOpt.get();
            if (transfer.status() == TransferStatus.SUCCESS) {
                execution.setStatus(ExecutionStatus.SUCCESS);
                execution.setPaymentReference(transfer.reference());
                execution.setUpdatedAt(Instant.now());
                executionRepository.save(execution);

                recordAttempt(executionId, nextAttemptNum, "RECOVERY_SUCCESS", null, "Recovered confirmed payment via reference query");
                StandingOrderDto order = standingOrderClient.getOrder(execution.getStandingOrderId());
                if (order != null) {
                    createOutboxEvent(execution, order, ExecutionStatus.SUCCESS, null, "Payment confirmed via recovery");
                    standingOrderClient.advanceSchedule(order.id());
                }
                log.info("Recovered execution #{} to SUCCESS!", executionId);
            } else if (transfer.status() == TransferStatus.FAILED) {
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setPaymentReference(transfer.reference());
                execution.setUpdatedAt(Instant.now());
                executionRepository.save(execution);

                recordAttempt(executionId, nextAttemptNum, "RECOVERY_FAILED", transfer.errorCode(), transfer.errorMessage());
                StandingOrderDto order = standingOrderClient.getOrder(execution.getStandingOrderId());
                if (order != null) {
                    createOutboxEvent(execution, order, ExecutionStatus.FAILED, transfer.errorCode(), transfer.errorMessage());
                    standingOrderClient.advanceSchedule(order.id());
                }
                log.info("Recovered execution #{} to FAILED.", executionId);
            }
        } else {
            log.warn("Transfer record still not found for execution #{}. Marked as FAILED (unreachable)", executionId);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setUpdatedAt(Instant.now());
            executionRepository.save(execution);

            recordAttempt(executionId, nextAttemptNum, "RECOVERY_NOT_FOUND", "PAYMENT_NOT_FOUND", "Payment not found in core banking");
        }

        return toDto(execution);
    }

    private void recordAttempt(Long execId, int attemptNum, String category, String errCode, String errMsg) {
        attemptRepository.save(new AttemptEntity(execId, attemptNum, Instant.now(), category, errCode, errMsg));
    }

    private void createOutboxEvent(ExecutionEntity execution, StandingOrderDto order, ExecutionStatus status, String errCode, String errMsg) {
        try {
            ExecutionOutcomeEvent event = new ExecutionOutcomeEvent(
                    "EVT-" + UUID.randomUUID().toString(),
                    execution.getId(),
                    order.id(),
                    order.customerId(),
                    order.sourceAccountId(),
                    order.destinationAccountId(),
                    order.amount(),
                    order.currency(),
                    execution.getScheduledTime(),
                    execution.getPaymentReference(),
                    status,
                    errCode,
                    errMsg,
                    Instant.now()
            );

            String json = objectMapper.writeValueAsString(event);
            OutboxEventEntity outbox = new OutboxEventEntity(
                    event.eventId(),
                    execution.getId(),
                    "EXECUTION_OUTCOME",
                    json,
                    "PENDING",
                    Instant.now()
            );
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event for execution #{}", execution.getId(), e);
        }
    }

    /**
     * Transactional outbox publisher: reads PENDING events and delivers to Notification Service.
     */
    public void dispatchOutboxEvents() {
        List<OutboxEventEntity> pending = outboxEventRepository.findByStatus("PENDING");
        for (OutboxEventEntity outbox : pending) {
            try {
                ExecutionOutcomeEvent event = objectMapper.readValue(outbox.getPayload(), ExecutionOutcomeEvent.class);
                boolean delivered = notificationClient.sendEvent(event);
                if (delivered) {
                    outbox.setStatus("PUBLISHED");
                    outboxEventRepository.save(outbox);
                    log.info("Published outbox event {}", outbox.getEventId());
                } else {
                    outbox.setRetryCount(outbox.getRetryCount() + 1);
                    if (outbox.getRetryCount() >= 5) {
                        outbox.setStatus("FAILED");
                    }
                    outboxEventRepository.save(outbox);
                }
            } catch (Exception e) {
                log.error("Error dispatching outbox event {}: {}", outbox.getEventId(), e.getMessage());
            }
        }
    }

    private ExecutionDto toDto(ExecutionEntity e) {
        List<ExecutionDto.AttemptDto> attempts = attemptRepository.findByExecutionIdOrderByAttemptNumberAsc(e.getId())
                .stream()
                .map(a -> new ExecutionDto.AttemptDto(a.getId(), a.getAttemptNumber(), a.getTimestamp(),
                        a.getResponseCategory(), a.getErrorCode(), a.getErrorMessage()))
                .toList();

        return new ExecutionDto(
                e.getId(),
                e.getStandingOrderId(),
                e.getScheduledTime(),
                e.getInstructionVersion(),
                e.getStatus(),
                e.getPaymentReference(),
                e.getIdempotencyKey(),
                e.getClaimedBy(),
                e.getClaimExpiresAt(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                attempts
        );
    }
}
