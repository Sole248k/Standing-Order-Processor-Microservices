package com.ewb.order.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "so_versions")
public class InstructionVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "standing_order_id", nullable = false)
    private Long standingOrderId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Lob
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "action", length = 30, nullable = false)
    private String action; // CREATED, AMENDED, PAUSED, RESUMED, CANCELLED

    @Column(name = "modified_by", length = 50, nullable = false)
    private String modifiedBy;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @Column(name = "reason", length = 255)
    private String reason;

    public InstructionVersionEntity() {}

    public InstructionVersionEntity(Long standingOrderId, Integer versionNumber, String snapshotJson, String action, String modifiedBy, Instant modifiedAt, String reason) {
        this.standingOrderId = standingOrderId;
        this.versionNumber = versionNumber;
        this.snapshotJson = snapshotJson;
        this.action = action;
        this.modifiedBy = modifiedBy;
        this.modifiedAt = modifiedAt;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getStandingOrderId() { return standingOrderId; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getAction() { return action; }
    public String getModifiedBy() { return modifiedBy; }
    public Instant getModifiedAt() { return modifiedAt; }
    public String getReason() { return reason; }
}
