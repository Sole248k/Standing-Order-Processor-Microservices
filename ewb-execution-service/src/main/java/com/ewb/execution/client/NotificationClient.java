package com.ewb.execution.client;

import com.ewb.common.dto.ApiResponse;
import com.ewb.common.event.ExecutionOutcomeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private final RestClient restClient;

    public NotificationClient(@Value("${services.notification.url:http://localhost:8084}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean sendEvent(ExecutionOutcomeEvent event) {
        try {
            ApiResponse<String> response = restClient.post()
                    .uri("/notifications/consume-event")
                    .body(event)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<String>>() {});
            return response != null && response.success();
        } catch (Exception e) {
            log.warn("Failed to deliver outbox event {} to notification service: {}", event.eventId(), e.getMessage());
            return false;
        }
    }
}
