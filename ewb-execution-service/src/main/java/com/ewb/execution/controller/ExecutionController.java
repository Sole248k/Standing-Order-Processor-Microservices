package com.ewb.execution.controller;

import com.ewb.common.dto.ApiResponse;
import com.ewb.common.dto.ExecutionDto;
import com.ewb.execution.entity.OutboxEventEntity;
import com.ewb.execution.service.ExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@CrossOrigin(origins = "*")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/executions")
    public ResponseEntity<ApiResponse<List<ExecutionDto>>> getAllExecutions() {
        return ResponseEntity.ok(ApiResponse.ok(executionService.getAllExecutions()));
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<ApiResponse<ExecutionDto>> getExecution(@PathVariable("id") Long id) {
        return executionService.getExecution(id)
                .map(dto -> ResponseEntity.ok(ApiResponse.ok(dto)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/standing-orders/{standingOrderId}/executions")
    public ResponseEntity<ApiResponse<List<ExecutionDto>>> getExecutionsForOrder(@PathVariable("standingOrderId") Long standingOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.getExecutionsForOrder(standingOrderId)));
    }

    @PostMapping("/executions/trigger-discovery")
    public ResponseEntity<ApiResponse<Integer>> triggerDiscovery() {
        int count = executionService.discoverAndExecuteDueInstructions();
        executionService.dispatchOutboxEvents();
        return ResponseEntity.ok(ApiResponse.ok("Executed due instructions", count));
    }

    @PostMapping("/executions/{id}/resolve")
    public ResponseEntity<ApiResponse<ExecutionDto>> resolveUncertain(@PathVariable("id") Long id) {
        ExecutionDto resolved = executionService.resolveUncertainExecution(id);
        return ResponseEntity.ok(ApiResponse.ok("Resolved execution status", resolved));
    }

    @GetMapping("/executions/outbox")
    public ResponseEntity<ApiResponse<List<OutboxEventEntity>>> getOutboxEvents() {
        return ResponseEntity.ok(ApiResponse.ok(executionService.getOutboxEvents()));
    }
}
