package com.example.optimizeIO.lab2.controller;

import com.example.optimizeIO.lab2.service.day3.PessimisticLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lab2/day3")
@RequiredArgsConstructor

public class PessimisticLockController {
    private final PessimisticLockService pessimisticLockService;

    /**
     * Day 3: Co che Pessimistic Lock (SELECT FOR UPDATE)
     * Case 1: Rút tiền Naive (Không Lock) -> Xem tiền bị mất (Lost Update)
     * GET /api/lab2/day3/bank/naive?threads=10&amount=10
     */
    @GetMapping("/bank/naive")
    public ResponseEntity<?> testNaiveWithdraw(
            @RequestParam(defaultValue = "10") int threads,
            @RequestParam(defaultValue = "10") long amount) {
        return ResponseEntity.ok(pessimisticLockService.simulateConcurrentWithdrawals(threads, amount, false));
    }

    /**
     * Case 2: Rút tiền dùng Pessimistic Lock (SELECT FOR UPDATE) -> Số dư chuẩn từng xu
     * GET /api/lab2/day3/bank/pessimistic?threads=10&amount=10
     */
    @GetMapping("/bank/pessimistic")
    public ResponseEntity<?> testPessimisticWithdraw(
            @RequestParam(defaultValue = "10") int threads,
            @RequestParam(defaultValue = "10") long amount) {
        return ResponseEntity.ok(pessimisticLockService.simulateConcurrentWithdrawals(threads, amount, true));
    }
}
