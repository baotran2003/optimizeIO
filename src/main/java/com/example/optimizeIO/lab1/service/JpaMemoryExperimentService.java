package com.example.optimizeIO.lab1.service;

import com.example.optimizeIO.lab1.dto.DeviceCacheItem;
import com.example.optimizeIO.lab1.entity.UserDevice;
import com.example.optimizeIO.lab1.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JpaMemoryExperimentService {

    private final UserDeviceRepository repository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Thí nghiệm 1: Đo lượng RAM và thời gian mà JPA findAll() tiêu thụ
     */
    public Map<String, Object> measureJpaMemoryConsumption() {
        System.gc(); // Gợi ý GC dọn dẹp trước khi đo
        sleep(200);

        long memBefore = getUsedMemoryBytes();
        long startTime = System.currentTimeMillis();

        // 1. Tải 100,000 entities qua Hibernate JPA
        List<UserDevice> list = repository.findAll();

        long durationMs = System.currentTimeMillis() - startTime;
        long memAfter = getUsedMemoryBytes();
        double memoryConsumedMB = (memAfter - memBefore) / (1024.0 * 1024.0);

        log.info("[JPA findAll] Load {} entities, mất {} ms, ngốn ~{:.2f} MB RAM",
                list.size(), durationMs, memoryConsumedMB);

        return Map.of(
            "approach", "JPA findAll() Managed Entities",
            "recordsLoaded", list.size(),
            "timeMs", durationMs,
            "memoryConsumedMB", String.format("%.2f MB", Math.max(0, memoryConsumedMB)),
            "note", "Ngốn nhiều RAM do First-Level Cache, Dirty Checking Snapshot và Entity Metadata."
        );
    }

    /**
     * Thí nghiệm 2: Đo lượng RAM khi dùng JDBC thô nạp vào Java 17 Record (Không qua Hibernate)
     */
    public Map<String, Object> measureJdbcRecordMemoryConsumption() {
        System.gc();
        sleep(200);

        long memBefore = getUsedMemoryBytes();
        long startTime = System.currentTimeMillis();

        List<DeviceCacheItem> list = new ArrayList<>(120_000);
        jdbcTemplate.query("SELECT user_id, device_token, os_type FROM user_devices", rs -> {
            list.add(new DeviceCacheItem(
                rs.getLong("user_id"),
                rs.getString("device_token"),
                rs.getString("os_type")
            ));
        });

        long durationMs = System.currentTimeMillis() - startTime;
        long memAfter = getUsedMemoryBytes();
        double memoryConsumedMB = (memAfter - memBefore) / (1024.0 * 1024.0);

        log.info("[JDBC Record] Load {} records, mất {} ms, ngốn ~{:.2f} MB RAM",
                list.size(), durationMs, memoryConsumedMB);

        return Map.of(
            "approach", "JDBC + Java 17 Record (Zero Hibernate)",
            "recordsLoaded", list.size(),
            "timeMs", durationMs,
            "memoryConsumedMB", String.format("%.2f MB", Math.max(0, memoryConsumedMB)),
            "note", "Rất nhẹ vì chỉ là Plain Data Carrier, không có Snapshot, không Dirty Checking."
        );
    }

    private long getUsedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
