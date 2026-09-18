package com.ewb.execution.entity;

import com.ewb.common.enums.ExecutionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.ZonedDateTime;

@Entity
@Table(name = "exec_executions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_scheduled_time", columnNames = {"standing_order_id", "scheduled_time"})
})
public class ExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "standing_order_id", nullable = false)
    private Long standingOrderId;

    @Column(name = "scheduled_time", nullable = false)
    private ZonedDateTime scheduledTime;

    @Column(name = "instruction_version", nullable = false)
    private Integer instructionVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ExecutionStatus status;

    @Column(name = "payment_reference", length = 64)
    private String paymentReference;

    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "claimed_by", length = 50)
    private String claimedBy;

    @Column(name = "claim_expires_at")
    private Instant claimExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ExecutionEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStandingOrderId() { return standingOrderId; }
    public void setStandingOrderId(Long standingOrderId) { this.standingOrderId = standingOrderId; }

    public ZonedDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(ZonedDateTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public Integer getInstructionVersion() { return instructionVersion; }
    public void setInstructionVersion(Integer instructionVersion) { this.instructionVersion = instructionVersion; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }

    public Instant getClaimExpiresAt() { return claimExpiresAt; }
    public void setClaimExpiresAt(Instant claimExpiresAt) { this.claimExpiresAt = claimExpiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
