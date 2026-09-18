package com.ewb.notification.controller;

import com.ewb.common.dto.ApiResponse;
import com.ewb.common.dto.NotificationDto;
import com.ewb.common.event.ExecutionOutcomeEvent;
import com.ewb.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/consume-event")
    public ResponseEntity<ApiResponse<String>> consumeEvent(@RequestBody ExecutionOutcomeEvent event) {
        boolean delivered = notificationService.consumeOutcomeEvent(event);
        if (delivered) {
            return ResponseEntity.ok(ApiResponse.ok("Notification delivered", "DELIVERED"));
        } else {
            return ResponseEntity.status(503).body(ApiResponse.error("Notification delivery failed (simulated outage)"));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDto>>> list(
            @RequestParam(value = "customerId", required = false) String customerId) {
        List<NotificationDto> list = (customerId != null && !customerId.isBlank())
                ? notificationService.getNotificationsByCustomer(customerId)
                : notificationService.getAllNotifications();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/simulate-outage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleOutage(@RequestBody Map<String, Boolean> body) {
        boolean outage = body.getOrDefault("outage", true);
        notificationService.setSimulateOutage(outage);
        return ResponseEntity.ok(ApiResponse.ok("Notification outage simulation updated",
                Map.of("simulatedOutage", outage)));
    }
}
