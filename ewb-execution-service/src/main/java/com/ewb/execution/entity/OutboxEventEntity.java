package com.ewb.execution.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "exec_outbox")
public class OutboxEventEntity {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", length = 20, nullable = false)
    private String status; // PENDING, PUBLISHED, FAILED

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OutboxEventEntity() {}

    public OutboxEventEntity(String eventId, Long executionId, String eventType, String payload, String status, Instant createdAt) {
        this.eventId = eventId;
        this.executionId = executionId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.retryCount = 0;
        this.createdAt = createdAt;
    }

    public String getEventId() { return eventId; }
    public Long getExecutionId() { return executionId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Instant getCreatedAt() { return createdAt; }
}
