package com.example.optimizeIO.lab1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSeederService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Dùng JdbcTemplate Batch Update để sinh 100,000 dòng trong ~2-3 giây.
     * Nếu dùng JPA saveAll(), Hibernate sẽ mất hàng phút và ngốn rất nhiều RAM do First-Level Cache!
     */
    public long seedUsers(int count) {
        log.info("Bắt đầu sinh {} bản ghi vào database...", count);
        long startTime = System.currentTimeMillis();

        // Xóa dữ liệu cũ để kết quả test chính xác
        jdbcTemplate.execute("TRUNCATE TABLE user_devices RESTART IDENTITY");

        String sql = "INSERT INTO user_devices (user_id, device_token, os_type, created_at) VALUES (?, ?, ?, ?)";
        int batchSize = 2000;
        List<Object[]> batchArgs = new ArrayList<>(batchSize);  // gom tam data truoc khi gui xuong DB
        Timestamp now = Timestamp.from(Instant.now());

        for (int i = 1; i <= count; i++) {
            long userId = 1000000L + i;
            String token = "fcm_token_" + UUID.randomUUID().toString().replace("-", "") + "_" + i;
            String os = (i % 2 == 0) ? "IOS" : "ANDROID";

            batchArgs.add(new Object[]{userId, token, os, now});

            if (batchArgs.size() >= batchSize) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
                batchArgs.clear();
            }
        }

        if (batchArgs.isEmpty() == false) {
            jdbcTemplate.batchUpdate(sql, batchArgs);
            batchArgs.clear();
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Hoàn tất sinh {} bản ghi trong {} ms", count, durationMs);
        return durationMs;
    }

    public long countTotalRecords() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM user_devices", Long.class);
        return count != null ? count : 0;
    }
}
