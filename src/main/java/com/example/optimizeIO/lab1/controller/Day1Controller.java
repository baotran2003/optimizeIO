package com.example.optimizeIO.lab1.controller;

import com.example.optimizeIO.lab1.dto.BenchmarkResponse;
import com.example.optimizeIO.lab1.service.day1.DataSeederService;
import com.example.optimizeIO.lab1.service.day1.InMemoryPreloadService;
import com.example.optimizeIO.lab1.service.day1.InMemoryPreloadService.PreloadStats;
import com.example.optimizeIO.lab1.service.day1.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/lab1/day1"})
@RequiredArgsConstructor
public class Day1Controller {

    private final DataSeederService dataSeederService;
    private final InMemoryPreloadService inMemoryPreloadService;
    private final NotificationService notificationService;

    /**
     * 1. Sinh dữ liệu mẫu vào PostgreSQL.
     * Mặc định: 100,000 bản ghi.
     */
    @PostMapping("/seed")
    public ResponseEntity<?> seedData(@RequestParam(defaultValue = "100000") int count) {
        long durationMs = dataSeederService.seedUsers(count);
        return ResponseEntity.ok(Map.of(
            "message", "Đã sinh dữ liệu thành công!",
            "totalRecords", count,
            "seedingTimeMs", durationMs
        ));
    }

    /**
     * 2. Pre-warm: Mô phỏng Interim Worker nạp sẵn dữ liệu từ Storage vào RAM trước giờ G.
     */
    @PostMapping("/prewarm")
    public ResponseEntity<PreloadStats> prewarmMemory() {
        PreloadStats stats = inMemoryPreloadService.preload();
        return ResponseEntity.ok(stats);
    }

    /**
     * Bài 6.1 — Health Check tiến độ Pre-warm
     * GET /api/lab1/day1/prewarm-status
     */
    @GetMapping("/prewarm-status")
    public ResponseEntity<?> getPrewarmStatus() {
        // TODO ⑦: Gọi hàm lấy tiến độ từ inMemoryPreloadService và return ResponseEntity.ok(...)
        Map<String, Object> prewarmProgress = inMemoryPreloadService.getPrewarmProgress();
        return ResponseEntity.ok(prewarmProgress);
    }

    /**
     * 3. Trạng thái hiện tại của DB và RAM.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
            "totalRecordsInDatabase", dataSeederService.countTotalRecords(),
            "totalRecordsInMemory", inMemoryPreloadService.size(),
            "isPreloaded", inMemoryPreloadService.isPreloaded()
        ));
    }

    /**
     * 4. Bấm nút theo kiểu Naive DB (Query DB tại thời điểm gửi).
     */
    @GetMapping("/trigger-db")
    public ResponseEntity<?> triggerDb() {
        return ResponseEntity.ok(notificationService.executeNaiveDbApproach());
    }

    /**
     * 5. Bấm nút theo kiểu In-Memory của Duolingo (Đọc trực tiếp từ RAM).
     */
    @GetMapping("/trigger-memory")
    public ResponseEntity<?> triggerMemory() {
        return ResponseEntity.ok(notificationService.executeInMemoryApproach());
    }

    /**
     * 6. So sánh trực tiếp cả 2 phương án.
     */
    @GetMapping("/benchmark")
    public ResponseEntity<BenchmarkResponse> runBenchmark() {
        return ResponseEntity.ok(notificationService.runComparisonBenchmark());
    }
}
