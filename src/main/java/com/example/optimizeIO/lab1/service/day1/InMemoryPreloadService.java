package com.example.optimizeIO.lab1.service.day1;

import com.example.optimizeIO.lab1.dto.DeviceCacheItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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

    private final AtomicLong loadedCount = new AtomicLong(0);
    private volatile long totalExpected = 0;
    private volatile long preloadStartTime = 0;

    private volatile String status = "NOT_STARTED";

    public synchronized PreloadStats preload() {
        log.info("Bắt đầu Pre-warm dữ liệu vào RAM...");

        // TODO ④: Đếm tổng số bản ghi dự kiến từ DB trước (SELECT count(*) FROM user_devices)
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM user_devices", Long.class);
        this.totalExpected = (count != null) ? count : 0;

        // Reset lại các biến tracking
        this.loadedCount.set(0);
        this.preloadStartTime = System.currentTimeMillis();
        this.status = "IN_PROGRESS";
        this.isPreloaded = false;

        System.gc();

        long memBefore = getUsedMemoryBytes();
        List<DeviceCacheItem> loadedList = new ArrayList<>((int) Math.min(this.totalExpected, 200_000));

        try {
            // Đọc raw bằng JDBC để nạp vào RAM
            jdbcTemplate.query(
                    "SELECT user_id, device_token, os_type FROM user_devices",
                    rs -> {
                        loadedList.add(new DeviceCacheItem(
                                rs.getLong("user_id"),
                                rs.getString("device_token"),
                                rs.getString("os_type")
                        ));

                        // TODO ⑤: Tăng bộ đếm loadedCount lên 1 cho mỗi dòng đọc được
                        loadedCount.incrementAndGet();
                    }
            );

            this.inMemoryDevices = Collections.unmodifiableList(loadedList);
            this.isPreloaded = true;
            this.status = "COMPLETED";

        } catch (Exception e) {
            this.status = "FAILED";
            log.error("Prewarm thất bại: {}", e.getMessage());
            throw e;
        }

        long durationMs = System.currentTimeMillis() - this.preloadStartTime;
        long memAfter = getUsedMemoryBytes();
        double memoryUsedMB = (memAfter - memBefore) / (1024.0 * 1024.0);

        log.info("Đã nạp {} bản ghi vào RAM trong {} ms, tiêu tốn ~{:.2f} MB RAM",
                inMemoryDevices.size(), durationMs, memoryUsedMB);
        return new PreloadStats(inMemoryDevices.size(), durationMs, Math.max(0, memoryUsedMB));
    }

    /**
     * Hàm lấy tiến độ Pre-warm thời gian thực
     */
    public Map<String, Object> getPrewarmProgress() {
        long currentLoaded = loadedCount.get();
        long elapsedMs = (preloadStartTime > 0) ? (System.currentTimeMillis() - preloadStartTime) : 0;

        // TODO ⑥: Tính % hoàn thành: (currentLoaded * 100.0) / totalExpected (nếu totalExpected > 0, ngược lại = 0.0)
        // Làm tròn 1 chữ số thập phân (ví dụ 67.5)
        double progressPercent = (currentLoaded * 100.0) / totalExpected;

        return Map.of(
                "status", status,
                "progressPercent", Math.round(progressPercent * 10.0) / 10.0,
                "loadedRecords", currentLoaded,
                "totalExpected", totalExpected,
                "elapsedMs", elapsedMs
        );
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
