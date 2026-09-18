package com.ewb.payment.config;

import com.ewb.common.enums.AccountStatus;
import com.ewb.payment.entity.AccountEntity;
import com.ewb.payment.repository.AccountRepository;
import com.ewb.payment.repository.LedgerEntryRepository;
import com.ewb.payment.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public DataInitializer(AccountRepository accountRepository,
                           TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    public void run(String... args) {
        initAccounts();
    }

    public synchronized void initAccounts() {
        if (accountRepository.count() == 0) {
            log.info("Seeding initial mock core banking accounts...");
            // Maria's accounts
            accountRepository.save(new AccountEntity("EWB-SAL-1001", "Maria Salary Account", "CUST-MARIA", new BigDecimal("20000.00"), "PHP", AccountStatus.ACTIVE));
            accountRepository.save(new AccountEntity("EWB-SAV-2001", "Maria Savings Account", "CUST-MARIA", new BigDecimal("1000.00"), "PHP", AccountStatus.ACTIVE));

            // Low balance test account
            accountRepository.save(new AccountEntity("EWB-SAL-1002", "Low Balance Test Account", "CUST-JOHN", new BigDecimal("2000.00"), "PHP", AccountStatus.ACTIVE));

            // Frozen account test
            accountRepository.save(new AccountEntity("EWB-SAL-FROZEN", "Frozen Test Account", "CUST-FROZEN", new BigDecimal("50000.00"), "PHP", AccountStatus.FROZEN));

            log.info("Seeded 4 mock bank accounts successfully.");
        }
    }

    public synchronized void resetDemoData() {
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteAll();
        initAccounts();
        log.info("Payment demo data has been reset to starting conditions.");
    }
}
