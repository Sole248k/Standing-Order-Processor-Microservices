package com.ewb.payment.entity;

import com.ewb.common.enums.AccountStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "pay_accounts")
public class AccountEntity {

    @Id
    @Column(name = "account_id", length = 32, nullable = false)
    private String accountId;

    @Column(name = "account_name", length = 100, nullable = false)
    private String accountName;

    @Column(name = "customer_id", length = 50, nullable = false)
    private String customerId;

    @Column(name = "balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal balance;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AccountStatus status;

    @Version
    @Column(name = "version")
    private Long version;

    public AccountEntity() {}

    public AccountEntity(String accountId, String accountName, String customerId, BigDecimal balance, String currency, AccountStatus status) {
        this.accountId = accountId;
        this.accountName = accountName;
        this.customerId = customerId;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
