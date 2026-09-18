package com.ewb.execution.client;

import com.ewb.common.dto.ApiResponse;
import com.ewb.common.dto.StandingOrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Component
public class StandingOrderClient {

    private static final Logger log = LoggerFactory.getLogger(StandingOrderClient.class);
    private final RestClient restClient;

    public StandingOrderClient(@Value("${services.standing-order.url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<StandingOrderDto> fetchDueInstructions(ZonedDateTime cutoff) {
        try {
            String isoCutoff = cutoff.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            ApiResponse<List<StandingOrderDto>> response = restClient.get()
                    .uri("/standing-orders/due?cutoff={cutoff}", isoCutoff)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<List<StandingOrderDto>>>() {});
            return (response != null && response.data() != null) ? response.data() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch due instructions from standing order service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void advanceSchedule(Long id) {
        try {
            restClient.post()
                    .uri("/standing-orders/{id}/advance-schedule", id)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully advanced schedule for standing order #{}", id);
        } catch (Exception e) {
            log.error("Failed to advance schedule for standing order #{}: {}", id, e.getMessage());
        }
    }

    public StandingOrderDto getOrder(Long id) {
        try {
            ApiResponse<StandingOrderDto> response = restClient.get()
                    .uri("/standing-orders/{id}", id)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<StandingOrderDto>>() {});
            return (response != null) ? response.data() : null;
        } catch (Exception e) {
            log.error("Failed to get standing order #{}: {}", id, e.getMessage());
            return null;
        }
    }
}
