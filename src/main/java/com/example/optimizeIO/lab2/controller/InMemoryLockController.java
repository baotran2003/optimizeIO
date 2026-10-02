package com.example.optimizeIO.lab2.controller;

import com.example.optimizeIO.lab2.service.day2.InMemoryLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lab2/day2")
@RequiredArgsConstructor
public class InMemoryLockController {

    private final InMemoryLockService inMemoryLockService;

    /**
     * Ngày 2 — Khóa In-Memory bằng CAS (Compare-And-Swap)
     * So sánh trực tiếp với Ngày 1: successCount phải = 1 thay vì > 1.
     * GET /api/lab2/day2/in-memory-cas?users=60
     */
    @GetMapping("/in-memory-cas")
    public ResponseEntity<?> testCasLock(
            @RequestParam(defaultValue = "60") int users) {

        // TODO ⑥: Gọi inMemoryLockService.simulateCasLock(users)
        return ResponseEntity.ok(inMemoryLockService.simulateCasLock(users));
    }
}
