package com.ewb.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pay_ledger")
public class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long entryId;

    @Column(name = "transfer_reference", length = 64, nullable = false)
    private String transferReference;

    @Column(name = "account_id", length = 32, nullable = false)
    private String accountId;

    @Column(name = "entry_type", length = 10, nullable = false)
    private String entryType; // DEBIT or CREDIT

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 18, scale = 2, nullable = false)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public LedgerEntryEntity() {}

    public LedgerEntryEntity(String transferReference, String accountId, String entryType, BigDecimal amount, BigDecimal balanceAfter, Instant createdAt) {
        this.transferReference = transferReference;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public Long getEntryId() { return entryId; }
    public String getTransferReference() { return transferReference; }
    public String getAccountId() { return accountId; }
    public String getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
