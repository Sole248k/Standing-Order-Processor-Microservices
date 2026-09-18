package com.ewb.order.controller;

import com.ewb.common.dto.AmendStandingOrderRequest;
import com.ewb.common.dto.ApiResponse;
import com.ewb.common.dto.CreateStandingOrderRequest;
import com.ewb.common.dto.StandingOrderDto;
import com.ewb.order.entity.InstructionVersionEntity;
import com.ewb.order.service.StandingOrderService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/standing-orders")
@CrossOrigin(origins = "*")
public class StandingOrderController {

    private final StandingOrderService standingOrderService;

    public StandingOrderController(StandingOrderService standingOrderService) {
        this.standingOrderService = standingOrderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StandingOrderDto>> create(
            @Valid @RequestBody CreateStandingOrderRequest request,
            @RequestHeader(value = "X-Customer-Id", defaultValue = "CUST-MARIA") String customerId) {
        StandingOrderDto created = standingOrderService.createInstruction(request, customerId);
        return ResponseEntity.ok(ApiResponse.ok("Standing order instruction created", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StandingOrderDto>>> list(
            @RequestHeader(value = "X-Customer-Id", required = false) String headerCustId,
            @RequestParam(value = "customerId", required = false) String queryCustId,
            @RequestParam(value = "all", defaultValue = "false") boolean all) {
        String custId = (queryCustId != null && !queryCustId.isBlank()) ? queryCustId : headerCustId;
        List<StandingOrderDto> list = (all || custId == null || custId.equalsIgnoreCase("OPERATIONS"))
                ? standingOrderService.getAllInstructions()
                : standingOrderService.getInstructionsByCustomer(custId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StandingOrderDto>> getById(@PathVariable("id") Long id) {
        return standingOrderService.getInstruction(id)
                .map(dto -> ResponseEntity.ok(ApiResponse.ok(dto)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<InstructionVersionEntity>>> getVersions(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(standingOrderService.getVersions(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<StandingOrderDto>> amend(
            @PathVariable("id") Long id,
            @Valid @RequestBody AmendStandingOrderRequest request,
            @RequestHeader(value = "X-Customer-Id", defaultValue = "CUST-MARIA") String actorId) {
        StandingOrderDto amended = standingOrderService.amendInstruction(id, request, actorId);
        return ResponseEntity.ok(ApiResponse.ok("Standing order amended", amended));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<StandingOrderDto>> pause(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Customer-Id", defaultValue = "CUST-MARIA") String actorId) {
        StandingOrderDto paused = standingOrderService.pauseInstruction(id, actorId);
        return ResponseEntity.ok(ApiResponse.ok("Standing order paused", paused));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<StandingOrderDto>> resume(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Customer-Id", defaultValue = "CUST-MARIA") String actorId) {
        StandingOrderDto resumed = standingOrderService.resumeInstruction(id, actorId);
        return ResponseEntity.ok(ApiResponse.ok("Standing order resumed", resumed));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<StandingOrderDto>> cancel(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Customer-Id", defaultValue = "CUST-MARIA") String actorId) {
        StandingOrderDto cancelled = standingOrderService.cancelInstruction(id, actorId);
        return ResponseEntity.ok(ApiResponse.ok("Standing order cancelled", cancelled));
    }

    @GetMapping("/due")
    public ResponseEntity<ApiResponse<List<StandingOrderDto>>> getDue(
            @RequestParam(value = "cutoff", required = false) String cutoffStr) {
        ZonedDateTime effectiveCutoff;
        if (cutoffStr != null && !cutoffStr.isBlank()) {
            try {
                effectiveCutoff = ZonedDateTime.parse(cutoffStr);
            } catch (Exception e) {
                effectiveCutoff = ZonedDateTime.now().plusDays(1);
            }
        } else {
            effectiveCutoff = ZonedDateTime.now().plusDays(1);
        }
        List<StandingOrderDto> due = standingOrderService.getDueInstructions(effectiveCutoff);
        return ResponseEntity.ok(ApiResponse.ok(due));
    }

    @PostMapping("/{id}/advance-schedule")
    public ResponseEntity<ApiResponse<String>> advanceSchedule(@PathVariable("id") Long id) {
        standingOrderService.advanceNextExecutionTime(id);
        return ResponseEntity.ok(ApiResponse.ok("Schedule advanced", "OK"));
    }

    @PostMapping("/seed-demo")
    public ResponseEntity<ApiResponse<StandingOrderDto>> seedDemo() {
        CreateStandingOrderRequest req = new CreateStandingOrderRequest(
                "EWB-SAL-1001",
                "EWB-SAV-2001",
                new BigDecimal("5000.00"),
                "PHP",
                "MONTHLY",
                25,
                LocalTime.of(9, 0),
                "Asia/Manila",
                LocalDate.now().minusMonths(1).withDayOfMonth(25),
                null
        );
        StandingOrderDto dto = standingOrderService.createInstruction(req, "CUST-MARIA");
        return ResponseEntity.ok(ApiResponse.ok("Demo standing order seeded", dto));
    }
}
