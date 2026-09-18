package com.ewb.execution.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "exec_attempts")
public class AttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "response_category", length = 50, nullable = false)
    private String responseCategory; // SUCCESS, BUSINESS_ERROR, TIMEOUT, UNCERTAIN

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    public AttemptEntity() {}

    public AttemptEntity(Long executionId, Integer attemptNumber, Instant timestamp, String responseCategory, String errorCode, String errorMessage) {
        this.executionId = executionId;
        this.attemptNumber = attemptNumber;
        this.timestamp = timestamp;
        this.responseCategory = responseCategory;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public Long getExecutionId() { return executionId; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public Instant getTimestamp() { return timestamp; }
    public String getResponseCategory() { return responseCategory; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
