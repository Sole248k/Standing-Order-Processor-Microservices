package com.ewb.payment.service;

import com.ewb.common.dto.AccountDto;
import com.ewb.common.dto.TransferRequest;
import com.ewb.common.dto.TransferResponse;
import com.ewb.common.enums.AccountStatus;
import com.ewb.common.enums.TransferStatus;
import com.ewb.payment.entity.AccountEntity;
import com.ewb.payment.entity.LedgerEntryEntity;
import com.ewb.payment.entity.TransferEntity;
import com.ewb.payment.repository.AccountRepository;
import com.ewb.payment.repository.LedgerEntryRepository;
import com.ewb.payment.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public PaymentService(AccountRepository accountRepository,
                          TransferRepository transferRepository,
                          LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(a -> new AccountDto(a.getAccountId(), a.getAccountName(), a.getCustomerId(),
                        a.getBalance(), a.getCurrency(), a.getStatus()))
                .toList();
    }

    public Optional<AccountDto> getAccount(String accountId) {
        return accountRepository.findById(accountId)
                .map(a -> new AccountDto(a.getAccountId(), a.getAccountName(), a.getCustomerId(),
                        a.getBalance(), a.getCurrency(), a.getStatus()));
    }

    public List<AccountDto> getAccountsByCustomer(String customerId) {
        return accountRepository.findByCustomerId(customerId).stream()
                .map(a -> new AccountDto(a.getAccountId(), a.getAccountName(), a.getCustomerId(),
                        a.getBalance(), a.getCurrency(), a.getStatus()))
                .toList();
    }

    public Optional<TransferResponse> getTransferByReference(String reference) {
        return transferRepository.findById(reference).map(this::toResponse);
    }

    public Optional<TransferResponse> getTransferByIdempotencyKey(String key) {
        return transferRepository.findByIdempotencyKey(key).map(this::toResponse);
    }

    public List<LedgerEntryEntity> getLedgerEntries(String reference) {
        return ledgerEntryRepository.findByTransferReference(reference);
    }

    public List<LedgerEntryEntity> getAllLedgerEntries() {
        return ledgerEntryRepository.findAll();
    }

    @Transactional
    public TransferResponse executeTransfer(TransferRequest request) {
        log.info("Processing transfer with idempotencyKey: {}, source: {}, dest: {}, amount: {}",
                request.idempotencyKey(), request.sourceAccountId(), request.destinationAccountId(), request.amount());

        // 1. Check idempotency
        Optional<TransferEntity> existingOpt = transferRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingOpt.isPresent()) {
            TransferEntity existing = existingOpt.get();
            log.warn("Duplicate request detected for idempotencyKey: {}. Returning original result.", request.idempotencyKey());
            // Verify request fingerprint (source, dest, amount match)
            if (!existing.getSourceAccountId().equals(request.sourceAccountId()) ||
                    !existing.getDestinationAccountId().equals(request.destinationAccountId()) ||
                    existing.getAmount().compareTo(request.amount()) != 0) {
                return new TransferResponse(
                        existing.getReference(),
                        request.idempotencyKey(),
                        request.sourceAccountId(),
                        request.destinationAccountId(),
                        request.amount(),
                        request.currency(),
                        TransferStatus.FAILED,
                        "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH",
                        "Idempotency key reused with different payment parameters",
                        Instant.now()
                );
            }
            return toResponse(existing);
        }

        String reference = (request.reference() != null && !request.reference().isBlank())
                ? request.reference()
                : "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 2. Validate basic constraints
        if (request.sourceAccountId().equalsIgnoreCase(request.destinationAccountId())) {
            return recordFailure(reference, request, "IDENTICAL_ACCOUNTS", "Source and destination accounts must be different");
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return recordFailure(reference, request, "INVALID_AMOUNT", "Amount must be strictly greater than zero");
        }

        // 3. Find source and destination accounts
        Optional<AccountEntity> sourceOpt = accountRepository.findById(request.sourceAccountId());
        if (sourceOpt.isEmpty()) {
            return recordFailure(reference, request, "SOURCE_ACCOUNT_NOT_FOUND", "Source account does not exist");
        }

        Optional<AccountEntity> destOpt = accountRepository.findById(request.destinationAccountId());
        if (destOpt.isEmpty()) {
            return recordFailure(reference, request, "DEST_ACCOUNT_NOT_FOUND", "Destination account does not exist");
        }

        AccountEntity source = sourceOpt.get();
        AccountEntity dest = destOpt.get();

        // 4. Validate account status
        if (source.getStatus() == AccountStatus.FROZEN) {
            return recordFailure(reference, request, "ACCOUNT_FROZEN", "Source account is frozen. Payment rejected.");
        }
        if (dest.getStatus() == AccountStatus.FROZEN) {
            return recordFailure(reference, request, "ACCOUNT_FROZEN", "Destination account is frozen. Payment rejected.");
        }

        // 5. Check sufficient balance
        if (source.getBalance().compareTo(request.amount()) < 0) {
            return recordFailure(reference, request, "INSUFFICIENT_FUNDS",
                    String.format("Insufficient funds. Balance: %s, Required: %s", source.getBalance(), request.amount()));
        }

        // 6. Execute atomic double-entry transfer
        BigDecimal newSourceBal = source.getBalance().subtract(request.amount());
        BigDecimal newDestBal = dest.getBalance().add(request.amount());

        source.setBalance(newSourceBal);
        dest.setBalance(newDestBal);

        accountRepository.save(source);
        accountRepository.save(dest);

        Instant now = Instant.now();
        ledgerEntryRepository.save(new LedgerEntryEntity(reference, source.getAccountId(), "DEBIT", request.amount(), newSourceBal, now));
        ledgerEntryRepository.save(new LedgerEntryEntity(reference, dest.getAccountId(), "CREDIT", request.amount(), newDestBal, now));

        // 7. Save Transfer record
        TransferEntity transfer = new TransferEntity();
        transfer.setReference(reference);
        transfer.setIdempotencyKey(request.idempotencyKey());
        transfer.setSourceAccountId(request.sourceAccountId());
        transfer.setDestinationAccountId(request.destinationAccountId());
        transfer.setAmount(request.amount());
        transfer.setCurrency(request.currency());
        transfer.setStandingOrderId(request.standingOrderId());
        transfer.setExecutionId(request.executionId());
        transfer.setCreatedAt(now);

        // Check if simulated timeout
        if (request.simulateTimeout()) {
            transfer.setStatus(TransferStatus.SUCCESS);
            transferRepository.save(transfer);
            log.info("Simulated timeout: Transfer {} committed successfully to database, but returning UNCERTAIN to caller", reference);
            return new TransferResponse(
                    reference,
                    request.idempotencyKey(),
                    request.sourceAccountId(),
                    request.destinationAccountId(),
                    request.amount(),
                    request.currency(),
                    TransferStatus.UNCERTAIN,
                    "SIMULATED_TIMEOUT",
                    "Simulated network timeout. Query reference to resolve.",
                    now
            );
        }

        transfer.setStatus(TransferStatus.SUCCESS);
        TransferEntity saved = transferRepository.save(transfer);
        log.info("Transfer {} completed successfully: {} debited from {}, credited to {}",
                reference, request.amount(), source.getAccountId(), dest.getAccountId());
        return toResponse(saved);
    }

    private TransferResponse recordFailure(String reference, TransferRequest req, String code, String message) {
        log.warn("Transfer failed: [{}]: {}", code, message);
        TransferEntity transfer = new TransferEntity();
        transfer.setReference(reference);
        transfer.setIdempotencyKey(req.idempotencyKey());
        transfer.setSourceAccountId(req.sourceAccountId());
        transfer.setDestinationAccountId(req.destinationAccountId());
        transfer.setAmount(req.amount() != null ? req.amount() : BigDecimal.ZERO);
        transfer.setCurrency(req.currency() != null ? req.currency() : "PHP");
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setErrorCode(code);
        transfer.setErrorMessage(message);
        transfer.setStandingOrderId(req.standingOrderId());
        transfer.setExecutionId(req.executionId());
        transfer.setCreatedAt(Instant.now());
        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    private TransferResponse toResponse(TransferEntity t) {
        return new TransferResponse(
                t.getReference(),
                t.getIdempotencyKey(),
                t.getSourceAccountId(),
                t.getDestinationAccountId(),
                t.getAmount(),
                t.getCurrency(),
                t.getStatus(),
                t.getErrorCode(),
                t.getErrorMessage(),
                t.getCreatedAt()
        );
    }
}
