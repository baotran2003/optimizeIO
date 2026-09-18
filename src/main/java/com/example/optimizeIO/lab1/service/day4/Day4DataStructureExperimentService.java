package com.example.optimizeIO.lab1.service.day4;

import com.example.optimizeIO.lab1.dto.DeviceCacheItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class Day4DataStructureExperimentService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * So sánh chi phí RAM và tốc độ giữa 3 cấu trúc dữ liệu lưu trữ 100k bản ghi:
     * 1. ArrayList (Duyệt tuần tự - Sequential Stream, CPU Cache Friendly)
     * 2. HashMap (Tra cứu O(1) theo userId, nhưng tốn RAM vì Node<K,V>)
     * 3. ConcurrentHashMap (Thread-safe, tối ưu đa luồng)
     */
    public Map<String, Object> compareDataStructures() {
        log.info("[Ngày 4] Bắt đầu đọc dữ liệu thô từ Database để đo đạc...");

        List<RawData> rawDataList = new ArrayList<>(120_000);
        jdbcTemplate.query("SELECT user_id, device_token, os_type FROM user_devices", rs -> {
            rawDataList.add(new RawData(
                rs.getLong("user_id"),
                rs.getString("device_token"),
                rs.getString("os_type")
            ));
        });

        int totalRecords = rawDataList.size();
        if (totalRecords == 0) {
            return Map.of("error", "Chưa có dữ liệu trong DB. Hãy bấm Bước 1 (Sinh 100k records) trước!");
        }

        // 1. Đo ArrayList<DeviceCacheItem>
        System.gc();
        sleep(100);
        long memBeforeList = getUsedMemoryBytes();
        long startList = System.currentTimeMillis();

        List<DeviceCacheItem> recordList = new ArrayList<>(totalRecords);
        for (RawData r : rawDataList) {
            recordList.add(new DeviceCacheItem(r.userId, r.token, r.os));
        }
        long listBuildTime = System.currentTimeMillis() - startList;
        long listMemory = getUsedMemoryBytes() - memBeforeList;

        // Đo tốc độ duyệt tuần tự 100k items trong List (Sequential Scan)
        long startIterate = System.nanoTime();
        long countValid = 0;
        for (DeviceCacheItem item : recordList) {
            if (item.deviceToken() != null) countValid++;
        }
        long listScanTimeNs = System.nanoTime() - startIterate;

        // 2. Đo HashMap<Long, String> (userId -> deviceToken)
        System.gc();
        sleep(100);
        long memBeforeMap = getUsedMemoryBytes();
        long startMap = System.currentTimeMillis();

        Map<Long, String> hashMap = new HashMap<>(totalRecords * 2);
        for (RawData r : rawDataList) {
            hashMap.put(r.userId, r.token);
        }
        long mapBuildTime = System.currentTimeMillis() - startMap;
        long mapMemory = getUsedMemoryBytes() - memBeforeMap;

        // 3. Đo ConcurrentHashMap<Long, String>
        System.gc();
        sleep(100);
        long memBeforeConcurrent = getUsedMemoryBytes();
        long startConcurrent = System.currentTimeMillis();

        Map<Long, String> concurrentMap = new ConcurrentHashMap<>(totalRecords * 2);
        for (RawData r : rawDataList) {
            concurrentMap.put(r.userId, r.token);
        }
        long concurrentBuildTime = System.currentTimeMillis() - startConcurrent;
        long concurrentMemory = getUsedMemoryBytes() - memBeforeConcurrent;

        return Map.of(
            "totalRecords", totalRecords,
            "arrayListRecord", Map.of(
                "structure", "ArrayList<DeviceCacheItem>",
                "memoryConsumedMB", String.format("%.2f MB", Math.max(0, listMemory / (1024.0 * 1024.0))),
                "buildTimeMs", listBuildTime,
                "scanTimeMs", String.format("%.2f ms", listScanTimeNs / 1_000_000.0),
                "advantage", "Tốn ít RAM nhất, bộ nhớ nằm liền kề, CPU L1/L2/L3 nạp trước qua Cache-line."
            ),
            "hashMap", Map.of(
                "structure", "HashMap<Long, String>",
                "memoryConsumedMB", String.format("%.2f MB", Math.max(0, mapMemory / (1024.0 * 1024.0))),
                "buildTimeMs", mapBuildTime,
                "lookupTime", "O(1) theo userId",
                "disadvantage", "Tốn thêm ~32 bytes cho mỗi Node<K,V> và mảng bucket rải rác trong Heap."
            ),
            "concurrentHashMap", Map.of(
                "structure", "ConcurrentHashMap<Long, String>",
                "memoryConsumedMB", String.format("%.2f MB", Math.max(0, concurrentMemory / (1024.0 * 1024.0))),
                "buildTimeMs", concurrentBuildTime,
                "lookupTime", "O(1) Thread-safe",
                "advantage", "An toàn khi nhiều worker cùng đọc/ghi đồng thời mà không bị nghẽn khóa toàn cục."
            )
        );
    }

    private long getUsedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private record RawData(long userId, String token, String os) {}
}
