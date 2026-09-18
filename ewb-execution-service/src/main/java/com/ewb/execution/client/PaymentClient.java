package com.ewb.execution.client;

import com.ewb.common.dto.ApiResponse;
import com.ewb.common.dto.TransferRequest;
import com.ewb.common.dto.TransferResponse;
import com.ewb.common.enums.TransferStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;

@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);
    private final RestClient restClient;

    public PaymentClient(@Value("${services.payment.url:http://localhost:8083}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public TransferResponse executeTransfer(TransferRequest request) {
        try {
            ApiResponse<TransferResponse> response = restClient.post()
                    .uri("/transfers")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<TransferResponse>>() {});
            return (response != null) ? response.data() : null;
        } catch (Exception e) {
            log.warn("Call to payment service failed or timed out: {}", e.getMessage());
            return new TransferResponse(
                    request.reference(),
                    request.idempotencyKey(),
                    request.sourceAccountId(),
                    request.destinationAccountId(),
                    request.amount(),
                    request.currency(),
                    TransferStatus.UNCERTAIN,
                    "CONNECTION_TIMEOUT",
                    "Payment service call timed out or failed: " + e.getMessage(),
                    Instant.now()
            );
        }
    }

    public Optional<TransferResponse> queryByReference(String reference) {
        try {
            ApiResponse<TransferResponse> response = restClient.get()
                    .uri("/transfers/by-reference/{reference}", reference)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<TransferResponse>>() {});
            return (response != null && response.data() != null) ? Optional.of(response.data()) : Optional.empty();
        } catch (Exception e) {
            log.error("Failed to query transfer by reference {}: {}", reference, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<TransferResponse> queryByIdempotencyKey(String key) {
        try {
            ApiResponse<TransferResponse> response = restClient.get()
                    .uri("/transfers/by-idempotency/{key}", key)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<TransferResponse>>() {});
            return (response != null && response.data() != null) ? Optional.of(response.data()) : Optional.empty();
        } catch (Exception e) {
            log.error("Failed to query transfer by idempotency key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
}
