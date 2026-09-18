package com.ewb.payment;

import com.ewb.common.dto.TransferRequest;
import com.ewb.common.dto.TransferResponse;
import com.ewb.common.enums.AccountStatus;
import com.ewb.common.enums.TransferStatus;
import com.ewb.payment.entity.AccountEntity;
import com.ewb.payment.entity.LedgerEntryEntity;
import com.ewb.payment.repository.AccountRepository;
import com.ewb.payment.repository.LedgerEntryRepository;
import com.ewb.payment.repository.TransferRepository;
import com.ewb.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteAll();

        // Seed Maria and test accounts
        accountRepository.save(new AccountEntity("EWB-SAL-1001", "Maria Salary", "CUST-MARIA", new BigDecimal("20000.00"), "PHP", AccountStatus.ACTIVE));
        accountRepository.save(new AccountEntity("EWB-SAV-2001", "Maria Savings", "CUST-MARIA", new BigDecimal("1000.00"), "PHP", AccountStatus.ACTIVE));
        accountRepository.save(new AccountEntity("EWB-SAL-1002", "Low Balance", "CUST-LOW", new BigDecimal("2000.00"), "PHP", AccountStatus.ACTIVE));
        accountRepository.save(new AccountEntity("EWB-SAL-FROZEN", "Frozen Account", "CUST-FROZEN", new BigDecimal("50000.00"), "PHP", AccountStatus.FROZEN));
    }

    @Test
    @DisplayName("Scenario 1: Atomic transfer debits source and credits destination with ledger entries")
    void testAtomicTransferSuccess() {
        TransferRequest req = new TransferRequest(
                "EWB-SAL-1001",
                "EWB-SAV-2001",
                new BigDecimal("5000.00"),
                "PHP",
                "IDEMP-TEST-001",
                "REF-001",
                1L,
                100L,
                false
        );

        TransferResponse response = paymentService.executeTransfer(req);

        assertEquals(TransferStatus.SUCCESS, response.status());
        assertEquals("REF-001", response.reference());

        // Verify balances
        AccountEntity source = accountRepository.findById("EWB-SAL-1001").orElseThrow();
        AccountEntity dest = accountRepository.findById("EWB-SAV-2001").orElseThrow();

        assertEquals(new BigDecimal("15000.00"), source.getBalance());
        assertEquals(new BigDecimal("6000.00"), dest.getBalance());

        // Verify double-entry ledger
        List<LedgerEntryEntity> ledger = ledgerEntryRepository.findByTransferReference("REF-001");
        assertEquals(2, ledger.size());
    }

    @Test
    @DisplayName("Scenario 2: Insufficient funds fails transfer and leaves balances untouched")
    void testInsufficientFunds() {
        TransferRequest req = new TransferRequest(
                "EWB-SAL-1002", // has 2000
                "EWB-SAV-2001",
                new BigDecimal("5000.00"),
                "PHP",
                "IDEMP-TEST-002",
                "REF-002",
                2L,
                101L,
                false
        );

        TransferResponse response = paymentService.executeTransfer(req);

        assertEquals(TransferStatus.FAILED, response.status());
        assertEquals("INSUFFICIENT_FUNDS", response.errorCode());

        // Balances must remain unchanged
        AccountEntity source = accountRepository.findById("EWB-SAL-1002").orElseThrow();
        assertEquals(new BigDecimal("2000.00"), source.getBalance());

        List<LedgerEntryEntity> ledger = ledgerEntryRepository.findByTransferReference("REF-002");
        assertTrue(ledger.isEmpty(), "No ledger entries should exist for failed transfer");
    }

    @Test
    @DisplayName("Scenario 3: Frozen account is rejected with error code and no ledger movement")
    void testFrozenAccountRejected() {
        TransferRequest req = new TransferRequest(
                "EWB-SAL-FROZEN",
                "EWB-SAV-2001",
                new BigDecimal("5000.00"),
                "PHP",
                "IDEMP-TEST-003",
                "REF-003",
                3L,
                102L,
                false
        );

        TransferResponse response = paymentService.executeTransfer(req);

        assertEquals(TransferStatus.FAILED, response.status());
        assertEquals("ACCOUNT_FROZEN", response.errorCode());

        AccountEntity source = accountRepository.findById("EWB-SAL-FROZEN").orElseThrow();
        assertEquals(new BigDecimal("50000.00"), source.getBalance());
    }

    @Test
    @DisplayName("Scenario 4: Duplicate request returns cached result with exactly one financial movement")
    void testDuplicateRequestReturnsCachedResult() {
        TransferRequest req = new TransferRequest(
                "EWB-SAL-1001",
                "EWB-SAV-2001",
                new BigDecimal("1000.00"),
                "PHP",
                "STABLE-IDEMP-KEY-999",
                "REF-DUP-01",
                4L,
                103L,
                false
        );

        // First call
        TransferResponse first = paymentService.executeTransfer(req);
        assertEquals(TransferStatus.SUCCESS, first.status());

        // Replay with exact same idempotency key
        TransferResponse second = paymentService.executeTransfer(req);
        assertEquals(TransferStatus.SUCCESS, second.status());
        assertEquals(first.reference(), second.reference());

        // Verify balance was debited ONLY once (20,000 - 1,000 = 19,000)
        AccountEntity source = accountRepository.findById("EWB-SAL-1001").orElseThrow();
        assertEquals(new BigDecimal("19000.00"), source.getBalance());

        // Exactly one debit and one credit in ledger
        List<LedgerEntryEntity> ledger = ledgerEntryRepository.findAll();
        assertEquals(2, ledger.size());
    }

    @Test
    @DisplayName("Scenario 5: Lost response (simulated timeout) commits and recovery finds SUCCESS without second debit")
    void testLostResponseAndRecovery() {
        String key = "LOST-RESP-KEY-777";
        TransferRequest req = new TransferRequest(
                "EWB-SAL-1001",
                "EWB-SAV-2001",
                new BigDecimal("2500.00"),
                "PHP",
                key,
                "REF-TIMEOUT-01",
                5L,
                104L,
                true // simulate timeout
        );

        TransferResponse initial = paymentService.executeTransfer(req);
        assertEquals(TransferStatus.UNCERTAIN, initial.status());

        // Recovery query by idempotency key
        Optional<TransferResponse> recovery = paymentService.getTransferByIdempotencyKey(key);
        assertTrue(recovery.isPresent());
        assertEquals(TransferStatus.SUCCESS, recovery.get().status());

        // Verify no second debit occurred
        AccountEntity source = accountRepository.findById("EWB-SAL-1001").orElseThrow();
        assertEquals(new BigDecimal("17500.00"), source.getBalance());
    }
}
