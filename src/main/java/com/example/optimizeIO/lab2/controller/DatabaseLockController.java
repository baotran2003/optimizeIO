package com.example.optimizeIO.lab2.controller;

import com.example.optimizeIO.lab2.repository.CampaignExecutionRepository;
import com.example.optimizeIO.lab2.service.day3.DatabaseLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/lab2/day3")
@RequiredArgsConstructor
public class DatabaseLockController {

    private final DatabaseLockService databaseLockService;
    private final CampaignExecutionRepository campaignExecutionRepository;

    /**
     * Ngày 3 — Bảo vệ bằng Database Unique Constraint
     * 50 luồng đồng thời INSERT cùng campaign_code → DB đảm bảo chỉ 1 bản ghi tồn tại.
     * GET /api/lab2/day3/db-unique-constraint?users=50
     */
    @GetMapping("/db-unique-constraint")
    public ResponseEntity<?> testDbUniqueConstraint(
            @RequestParam(defaultValue = "50") int users) {

        // TODO ⑫: Gọi databaseLockService.triggerWithUniqueConstraint(users) và return kết quả
        return ResponseEntity.ok(databaseLockService.triggerWithUniqueConstraint(users));
    }

    @GetMapping("/db-status")
    public ResponseEntity<?> getDbStatus() {
        return ResponseEntity.ok(Map.of(
                "totalRecord", campaignExecutionRepository.countByCampaignCode("SUPERBOWL_2024"),
                "allExecutions", campaignExecutionRepository.findAll()
        ));
    }

    /**
     * Test xem lỗi OptimisticLockException khi nhiều luồng cùng UPDATE 1 bản ghi
     * GET /api/lab2/day3/test-optimistic-lock?users=10
     */
    @GetMapping("/test-optimistic-lock")
    public ResponseEntity<?> testOptimisticLock(
            @RequestParam(defaultValue = "10") int users) {
        return ResponseEntity.ok(databaseLockService.testOptimisticLocking(users));
    }
}
