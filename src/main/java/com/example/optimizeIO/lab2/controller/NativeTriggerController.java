package com.example.optimizeIO.lab2.controller;

import com.example.optimizeIO.lab2.service.day1.NaiveTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping({"/api/lab2/day1"})
@RequiredArgsConstructor

public class NativeTriggerController {
    private final NaiveTriggerService naiveTriggerService;

    /**
     * Ngày 1 — Tái hiện Race Condition (Check-Then-Act)
     * GET /api/lab2/day1/race-condition?users=20
     */
    @GetMapping("/race-condition")
    public ResponseEntity<?> simulateRaceCondition(
            @RequestParam(defaultValue = "60") int users) {

        // TODO ⑦: Gọi naiveTriggerService.simulateRaceCondition(users) và trả về kết quả
        return ResponseEntity.ok(naiveTriggerService.simulateRaceCondition(users));
    }
}
