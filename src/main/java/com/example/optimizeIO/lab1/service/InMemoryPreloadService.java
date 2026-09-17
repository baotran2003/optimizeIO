package com.example.optimizeIO.lab1.service;

import com.example.optimizeIO.lab1.dto.DeviceCacheItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mô phỏng Interim Worker của Duolingo:
 * Nạp sẵn toàn bộ danh sách User -> DeviceToken vào RAM trước giờ G.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InMemoryPreloadService {

    private final JdbcTemplate jdbcTemplate;

    // Danh sách lưu trữ trực tiếp trên RAM
    private List<DeviceCacheItem> inMemoryDevices = Collections.emptyList();
    private volatile boolean isPreloaded = false;

    public synchronized PreloadStats preload() {
        log.info("Bắt đầu Pre-warm dữ liệu vào RAM...");
        System.gc(); // Gợi ý dọn dẹp trước khi đo bộ nhớ
        long memBefore = getUsedMemoryBytes();
        long startTime = System.currentTimeMillis();

        List<DeviceCacheItem> loadedList = new ArrayList<>(120_000);

        // Đọc raw bằng JDBC để nạp nhanh nhất có thể vào RAM
        jdbcTemplate.query(
            "SELECT user_id, device_token, os_type FROM user_devices",
            rs -> {
                loadedList.add(new DeviceCacheItem(
                    rs.getLong("user_id"),
                    rs.getString("device_token"),
                    rs.getString("os_type")
                ));
            }
        );

        this.inMemoryDevices = Collections.unmodifiableList(loadedList);
        this.isPreloaded = true;

        long durationMs = System.currentTimeMillis() - startTime;
        long memAfter = getUsedMemoryBytes();
        double memoryUsedMB = (memAfter - memBefore) / (1024.0 * 1024.0);

        log.info("Đã nạp {} bản ghi vào RAM trong {} ms, tiêu tốn ~{:.2f} MB RAM",
                inMemoryDevices.size(), durationMs, memoryUsedMB);

        return new PreloadStats(inMemoryDevices.size(), durationMs, Math.max(0, memoryUsedMB));
    }

    public List<DeviceCacheItem> getInMemoryDevices() {
        return inMemoryDevices;
    }

    public boolean isPreloaded() {
        return isPreloaded;
    }

    public int size() {
        return inMemoryDevices.size();
    }

    private long getUsedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public record PreloadStats(int count, long durationMs, double memoryUsedMB) {

    }
}
