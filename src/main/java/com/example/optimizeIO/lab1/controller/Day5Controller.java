package com.example.optimizeIO.lab1.controller;

import com.example.optimizeIO.lab1.service.day5.ConnectionPoolService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/lab1/day5")
@RequiredArgsConstructor

/*
Thread 1-10: Chạy bình thường, mỗi thread mất ~2,000ms.
Thread 11-15: Phải xếp hàng chờ connection được trả về, tổng thời gian mất ~4,000ms (chờ 2s + chạy 2s).
*/

public class Day5Controller {
    private final JdbcTemplate jdbcTemplate;
    private final ConnectionPoolService connectionPoolService;

    // TODO ①: Inject thêm DataSource vào đây (Spring tự inject HikariDataSource)
    private final DataSource dataSource;

    /**
     * Bài 5.1 — Slow Endpoint
     * Gọi pg_sleep(2) để giả lập một query nặng tốn 2 giây.
     * GET /api/lab1/day5/slow-query
     */
    @GetMapping("/slow-query")
    public ResponseEntity<?> slowQuery() {
        log.info("[Day5] Bắt đầu slow query...");

        long start = System.currentTimeMillis();

        // Lệnh PostgreSQL: giữ connection ngủ đúng 2 giây
        jdbcTemplate.execute("SELECT pg_sleep(2)");

        long durationMs = System.currentTimeMillis() - start;

        log.info("[Day5] slow query hoàn thành sau {} ms", durationMs);

        return ResponseEntity.ok(Map.of(
            "endpoint",     "GET /api/lab1/day5/slow-query",
            "description",  "Giả lập query nặng bằng pg_sleep(2)",
            "durationMs",   durationMs,
            "explanation",  "Connection bị giữ trong ~2000ms, mô phỏng một DB query tốn tài nguyên."
        ));
    }

    @GetMapping("/stress-test")
    public ResponseEntity<?> testPool(@RequestParam(defaultValue = "15") int requests) {
        return ResponseEntity.ok(connectionPoolService.runConcurrentStressTest(requests));
    }

    /**
     * Bài 5.4 — HikariCP Metrics Endpoint
     * Trả về trạng thái thực tế của Connection Pool.
     */
    @GetMapping("/pool-status")
    public ResponseEntity<?> poolMetrics() {

        // TODO ②: Cast DataSource về HikariDataSource để truy cập thông tin pool
        HikariDataSource hikari = (HikariDataSource) dataSource;

        // TODO ③: Lấy đối tượng HikariPoolMXBean từ hikari
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();

        // TODO ④: Đọc 3 chỉ số từ pool:
        //         - pool.getActiveConnections()       → đang bị giữ bởi query
        //         - pool.getIdleConnections()         → đang rảnh, sẵn sàng dùng
        //         - pool.getThreadsAwaitingConnection() → đang xếp hàng chờ
        int active  = pool.getActiveConnections();
        int idle    = pool.getIdleConnections();
        int waiting = pool.getThreadsAwaitingConnection();

        return ResponseEntity.ok(Map.of(
            "activeConnections",          active,
            "idleConnections",            idle,
            "threadsAwaitingConnection",  waiting,
            "totalConnections",           active + idle,
            "explanation",                "Gọi API này trong khi stress-test đang chạy để thấy pool bị lấp đầy."
        ));
    }
}
