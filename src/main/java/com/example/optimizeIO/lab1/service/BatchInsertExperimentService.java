package com.example.optimizeIO.lab1.service;

import com.example.optimizeIO.lab1.entity.UserDevice;
import com.example.optimizeIO.lab1.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchInsertExperimentService {

    private final UserDeviceRepository repository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Thử chèn 2,000 dòng bằng JPA saveAll()
     */
    @Transactional
    public Map<String, Object> testJpaSaveAll(int count) {
        log.info("[JPA saveAll] Chuẩn bị chèn {} bản ghi...", count);

        List<UserDevice> entities = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            entities.add(UserDevice.builder()
                .userId(9000000L + i)
                .deviceToken("jpa_token_" + UUID.randomUUID())
                .osType(i % 2 == 0 ? "IOS" : "ANDROID")
                .createdAt(LocalDateTime.now())
                .build());
        }

        long start = System.currentTimeMillis();
        // JPA saveAll: Gọi save() từng dòng trong vòng lặp for, IDENTITY tắt batching!
        repository.saveAll(entities);
        long durationMs = System.currentTimeMillis() - start;

        log.info("[JPA saveAll] Chèn {} dòng mất {} ms", count, durationMs);

        return Map.of(
            "approach", "JPA saveAll() (Chèn từng dòng đơn lẻ)",
            "count", count,
            "durationMs", durationMs,
            "opsPerSec", (count * 1000L) / Math.max(1, durationMs),
            "explanation", "Bị chậm vì GenerationType.IDENTITY vô hiệu hóa batching, mỗi dòng tốn 1 Network Round-trip."
        );
    }

    /**
     * Thử chèn 2,000 dòng bằng JDBC batchUpdate()
     */
    public Map<String, Object> testJdbcBatch(int count) {
        log.info("[JDBC batchUpdate] Chuẩn bị chèn {} bản ghi...", count);

        String sql = "INSERT INTO user_devices (user_id, device_token, os_type, created_at) VALUES (?, ?, ?, ?)";
        List<Object[]> batchArgs = new ArrayList<>(count);
        Timestamp now = Timestamp.from(Instant.now());

        // chuan bi du lieu vao RAm
        for (int i = 1; i <= count; i++) {
            batchArgs.add(new Object[]{
                8000000L + i,
                "jdbc_token_" + UUID.randomUUID(),  //  token random + duy nhat
                i % 2 == 0 ? "IOS" : "ANDROID",
                now
            });
        }

        long start = System.currentTimeMillis();
        // Gom toàn bộ(ghep SQL + tung record) vào 1 gói tin gửi sang DB
        jdbcTemplate.batchUpdate(sql, batchArgs);
        long durationMs = System.currentTimeMillis() - start;

        log.info("[JDBC batchUpdate] Chèn {} dòng mất {} ms", count, durationMs);

        return Map.of(
            "approach", "JDBC batchUpdate() (Gom thành 1 batch)",
            "count", count,
            "durationMs", durationMs,
            "opsPerSec", (count * 1000L) / Math.max(1, durationMs),
            "explanation", "Cực nhanh vì toàn bộ tham số được đóng gói gửi qua mạng 1 lần duy nhất."
        );
    }
}
