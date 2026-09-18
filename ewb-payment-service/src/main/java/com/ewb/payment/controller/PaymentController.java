package com.ewb.payment.controller;

import com.ewb.common.dto.AccountDto;
import com.ewb.common.dto.ApiResponse;
import com.ewb.common.dto.TransferRequest;
import com.ewb.common.dto.TransferResponse;
import com.ewb.payment.config.DataInitializer;
import com.ewb.payment.entity.LedgerEntryEntity;
import com.ewb.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;
    private final DataInitializer dataInitializer;

    public PaymentController(PaymentService paymentService, DataInitializer dataInitializer) {
        this.paymentService = paymentService;
        this.dataInitializer = dataInitializer;
    }

    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = paymentService.executeTransfer(request);
        return ResponseEntity.ok(ApiResponse.ok("Transfer processed", response));
    }

    @GetMapping("/transfers/by-reference/{reference}")
    public ResponseEntity<ApiResponse<TransferResponse>> getByReference(@PathVariable("reference") String reference) {
        return paymentService.getTransferByReference(reference)
                .map(res -> ResponseEntity.ok(ApiResponse.ok(res)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/transfers/by-idempotency/{key}")
    public ResponseEntity<ApiResponse<TransferResponse>> getByIdempotency(@PathVariable("key") String key) {
        return paymentService.getTransferByIdempotencyKey(key)
                .map(res -> ResponseEntity.ok(ApiResponse.ok(res)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/transfers/ledger")
    public ResponseEntity<ApiResponse<List<LedgerEntryEntity>>> getAllLedgerEntries() {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getAllLedgerEntries()));
    }

    @GetMapping("/transfers/{reference}/ledger")
    public ResponseEntity<ApiResponse<List<LedgerEntryEntity>>> getLedgerEntries(@PathVariable("reference") String reference) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getLedgerEntries(reference)));
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AccountDto>>> getAccounts(@RequestParam(value = "customerId", required = false) String customerId) {
        List<AccountDto> accounts = (customerId != null && !customerId.isBlank())
                ? paymentService.getAccountsByCustomer(customerId)
                : paymentService.getAllAccounts();
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<ApiResponse<AccountDto>> getAccount(@PathVariable("accountId") String accountId) {
        return paymentService.getAccount(accountId)
                .map(acc -> ResponseEntity.ok(ApiResponse.ok(acc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/accounts/reset")
    public ResponseEntity<ApiResponse<String>> resetData() {
        dataInitializer.resetDemoData();
        return ResponseEntity.ok(ApiResponse.ok("Accounts reset to initial conditions", "OK"));
    }
}
