package com.example.optimizeIO.lab1.controller;

import com.example.optimizeIO.lab1.dto.BenchmarkResponse;
import com.example.optimizeIO.lab1.service.DataSeederService;
import com.example.optimizeIO.lab1.service.InMemoryPreloadService;
import com.example.optimizeIO.lab1.service.InMemoryPreloadService.PreloadStats;
import com.example.optimizeIO.lab1.service.Lab1NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/lab1")
@RequiredArgsConstructor
public class Lab1Controller {

    private final DataSeederService dataSeederService;
    private final InMemoryPreloadService inMemoryPreloadService;
    private final Lab1NotificationService notificationService;
    private final com.example.optimizeIO.lab1.service.JpaMemoryExperimentService jpaMemoryExperimentService;
    private final com.example.optimizeIO.lab1.service.BatchInsertExperimentService batchInsertExperimentService;
    private final com.example.optimizeIO.lab1.service.Day4DataStructureExperimentService day4DataStructureExperimentService;

    /**
     * Day 4 Experiment: So sánh dung lượng RAM giữa ArrayList vs HashMap vs ConcurrentHashMap
     */
    @GetMapping("/experiment/day4-datastructures")
    public ResponseEntity<?> testDay4DataStructures() {
        return ResponseEntity.ok(day4DataStructureExperimentService.compareDataStructures());
    }

    /**
     * Day 2 Experiment: Đo lượng RAM và thời gian của JPA findAll()
     */
    @GetMapping("/experiment/jpa-memory")
    public ResponseEntity<?> testJpaMemory() {
        return ResponseEntity.ok(jpaMemoryExperimentService.measureJpaMemoryConsumption());
    }

    /**
     * Day 2 Experiment: Đo lượng RAM và thời gian của JDBC Record
     */
    @GetMapping("/experiment/jdbc-memory")
    public ResponseEntity<?> testJdbcMemory() {
        return ResponseEntity.ok(jpaMemoryExperimentService.measureJdbcRecordMemoryConsumption());
    }

    /**
     * Day 3 Experiment: Chèn 2,000 bản ghi bằng JPA saveAll()
     */
    @PostMapping("/experiment/batch-jpa")
    public ResponseEntity<?> testBatchJpa(@RequestParam(defaultValue = "2000") int count) {
        return ResponseEntity.ok(batchInsertExperimentService.testJpaSaveAll(count));
    }

    /**
     * Day 3 Experiment: Chèn 2,000 bản ghi bằng JDBC batchUpdate()
     */
    @PostMapping("/experiment/batch-jdbc")
    public ResponseEntity<?> testBatchJdbc(@RequestParam(defaultValue = "2000") int count) {
        return ResponseEntity.ok(batchInsertExperimentService.testJdbcBatch(count));
    }

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
