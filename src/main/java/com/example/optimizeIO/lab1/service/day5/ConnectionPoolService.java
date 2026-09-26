package com.example.optimizeIO.lab1.service.day5;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor


public class ConnectionPoolService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> runConcurrentStressTest(int concurrentRequests) {
        log.info("[Day5] Bắt đầu stress test với {} threads đồng thời...", concurrentRequests);

        // TODO ①: Tạo ExecutorService với fixed thread pool = concurrentRequests
        //         Dùng Executors.newFixedThreadPool(...)
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        // "Súng lệnh" — đảm bảo tất cả thread cùng xuất phát 1 lúc
        // CountDownLatch(1): main gọi countDown() 1 lần → toàn bộ thread cùng bắt đầu
        // TODO ②: Tạo CountDownLatch với giá trị khởi tạo là 1
        CountDownLatch startGun = new CountDownLatch(1);

        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        long overallStart = System.currentTimeMillis();

        for (int i = 1; i <= concurrentRequests; i++) {
            final int threadId = i;

            Future<Map<String, Object>> future = executor.submit(() -> {

                // TODO ③: Chờ súng lệnh — gọi startGun.await()
                //         (Mọi thread dừng ở đây, đến khi main gọi countDown() thì cùng chạy)
                try {
                    startGun.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread bị ngắt khi đang chờ súng lệnh", e);
                }

                long threadStart = System.currentTimeMillis();
                String status = "SUCCESS";

                try {
                    // TODO ④: Gọi jdbcTemplate.execute() với lệnh pg_sleep(2)
                    jdbcTemplate.execute("SELECT pg_sleep(2)");

                } catch (Exception e) {
                    status = "TIMEOUT/ERROR";
                    log.warn("[Day5] Thread-{} lỗi: {}", threadId, e.getMessage());
                }

                long threadDuration = System.currentTimeMillis() - threadStart;
                log.info("[Day5] Thread-{} | {} | {}ms", threadId, status, threadDuration);

                return Map.of(
                        "threadId",   threadId,
                        "status",     status,
                        "durationMs", threadDuration
                );
            });

            futures.add(future);
        }

        // TODO ⑥: Bắn súng lệnh — gọi startGun.countDown()
        //         Tất cả thread đang chờ ở await() sẽ đồng loạt chạy
        startGun.countDown();

        // Thu thập kết quả
        List<Map<String, Object>> results = new ArrayList<>();
        for (Future<Map<String, Object>> future : futures) {
            try {
                // TODO ⑦: Lấy kết quả từ future, timeout tối đa 10 giây
                results.add(future.get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                results.add(Map.of("status", "FAILED", "error", e.getMessage()));
            }
        }

        executor.shutdown();

        long overallDuration = System.currentTimeMillis() - overallStart;
        log.info("[Day5] Stress test hoàn thành sau {}ms", overallDuration);

        return Map.of(
                "concurrentRequests", concurrentRequests,
                "overallDurationMs",  overallDuration,
                "threadResults",      results,
                "explanation",        "10 thread đầu chạy song song ~2000ms. Thread 11-15 chờ connection → ~4000ms."
        );
    }
}