package com.ewb.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notif_deliveries", indexes = {
        @Index(name = "idx_notif_event_id", columnList = "event_id", unique = true)
})
public class NotificationDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 64, nullable = false, unique = true)
    private String eventId;

    @Column(name = "customer_id", length = 50, nullable = false)
    private String customerId;

    @Column(name = "channel", length = 20, nullable = false)
    private String channel; // SMS, EMAIL

    @Column(name = "recipient", length = 100, nullable = false)
    private String recipient;

    @Column(name = "subject", length = 150)
    private String subject;

    @Lob
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "status", length = 20, nullable = false)
    private String status; // SENT, FAILED, DUPLICATE_IGNORED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public NotificationDeliveryEntity() {}

    public NotificationDeliveryEntity(String eventId, String customerId, String channel, String recipient, String subject, String message, String status, Instant createdAt) {
        this.eventId = eventId;
        this.customerId = customerId;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getCustomerId() { return customerId; }
    public String getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
