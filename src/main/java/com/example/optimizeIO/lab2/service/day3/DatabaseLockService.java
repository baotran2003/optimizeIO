package com.example.optimizeIO.lab2.service.day3;

import com.example.optimizeIO.lab2.entity.CampaignExecution;
import com.example.optimizeIO.lab2.repository.CampaignExecutionRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseLockService {

    private final CampaignExecutionRepository repository;

    /**
     * Ngày 3 — Bảo vệ bằng Database Unique Constraint
     * 50 luồng đồng thời INSERT cùng campaign_code → chỉ 1 thành công, 49 nhận lỗi 409
     */
    public Map<String, Object> triggerWithUniqueConstraint(int concurrentUsers) {
        String campaignCode = "SUPERBOWL_2024";

        // Dọn sạch dữ liệu cũ trước mỗi lần chạy
        repository.deleteAll();

        AtomicInteger successCount  = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicLong    totalDbLatencyMs = new AtomicLong(0);
        List<String>  executionLog = Collections.synchronizedList(new ArrayList<>());

        log.info("[Lab2-Day3] Bắt đầu DB Unique Constraint test với {} users...", concurrentUsers);

        // TODO ①: Tạo ExecutorService với fixed thread pool = concurrentUsers
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);

        // TODO ②: Tạo CountDownLatch(1) làm súng lệnh
        CountDownLatch startGun = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 1; i <= concurrentUsers; i++) {
            final int userId = i;
            futures.add(executor.submit(() -> {
                try {
                    // TODO ③: Chờ súng lệnh
                    startGun.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                long dbStart = System.currentTimeMillis();

                try {
                    // TODO ④: Gọi hàm insertCampaign(campaignCode, userId)
                    //         Nếu thành công → tăng successCount, thêm vào executionLog
                    insertCampaign(campaignCode, userId);

                    successCount.incrementAndGet();
                    String msg = "User-" + userId + " WINNER — INSERT thành công vào DB!";
                    executionLog.add(msg);
                    log.info("[Lab2-Day3] 🏆 {}", msg);

                } catch (DataIntegrityViolationException e) {
                    // ✅ DB bắt vi phạm UNIQUE Constraint và ném exception này
                    // TODO ⑤: Tăng rejectedCount, log warning kèm userId
                    rejectedCount.incrementAndGet();
                    log.warn("[Lab2-Day3] ❌ User-{} bị REJECTED — Duplicate campaign_code!", userId);

                } finally {
                    long dbLatency = System.currentTimeMillis() - dbStart;
                    // TODO ⑥: Cộng dbLatency vào tổng totalDbLatencyMs
                    //         Dùng: totalDbLatencyMs.addAndGet(dbLatency)
                    totalDbLatencyMs.addAndGet(dbLatency);
                }
            }));
        }

        // TODO ⑦: Bắn súng lệnh
        startGun.countDown();

        for (Future<?> f : futures) {
            try { f.get(15, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }

        // TODO ⑧: Shutdown executor
        executor.shutdown();

        // Kiểm chứng thực tế trong DB sau khi tất cả hoàn thành
        // TODO ⑨: Đếm số bản ghi thực tế trong DB bằng repository.countByCampaignCode(campaignCode)
        long actualRecordsInDb = repository.countByCampaignCode(campaignCode);

        // Tính latency trung bình
        // TODO ⑩: Tính avgDbLatencyMs = totalDbLatencyMs / concurrentUsers
        long avgDbLatencyMs = totalDbLatencyMs.get() / concurrentUsers;

        log.info("[Lab2-Day3] Kết quả: {} winner, {} rejected | DB records: {} | Avg latency: {}ms",
                successCount.get(), rejectedCount.get(), actualRecordsInDb, avgDbLatencyMs);

        return Map.of(
            "approach",          "Database Unique Constraint (INSERT + catch DataIntegrityViolationException)",
            "campaignCode",      campaignCode,
            "concurrentUsers",   concurrentUsers,
            "successCount",      successCount.get(),
            "rejectedCount",     rejectedCount.get(),
            "actualRecordsInDb", actualRecordsInDb,   // Phải = 1 dù successCount có thể tạm thời > 1
            "avgDbLatencyMs",    avgDbLatencyMs,       // So sánh với nanosecond ở Ngày 2
            "verdict",           actualRecordsInDb == 1
                                 ? "✅ HOÀN HẢO! DB chỉ có đúng 1 bản ghi — Unique Constraint hoạt động!"
                                 : "❌ LỖI! DB có " + actualRecordsInDb + " bản ghi — Kiểm tra lại!"
        );
    }

    /**
     * Thực hiện INSERT một bản ghi vào DB.
     * Nếu vi phạm UNIQUE(campaign_code) → PostgreSQL ném lỗi 23505
     * → Spring bắt và ném DataIntegrityViolationException lên trên.
     */
    @Transactional
    public void insertCampaign(String campaignCode, int userId) {
        // TODO ⑪: Dùng repository.save() để lưu một CampaignExecution mới
        //         Build object bằng: CampaignExecution.builder()
        //             .campaignCode(campaignCode)
        //             .status("TRIGGERED")
        //             .triggeredBy("User-" + userId)
        //             .threadName(Thread.currentThread().getName())
        //             .triggeredAt(LocalDateTime.now())
        //             .build()
        repository.save(
            CampaignExecution.builder()
                .campaignCode(campaignCode)
                .status("TRIGGERED")
                .triggeredBy("User-" + userId)
                .threadName(Thread.currentThread().getName())
                .triggeredAt(LocalDateTime.now())
                .build()
        );
    }

    /**
     * Test tận mắt lỗi OptimisticLockException:
     * Tạo sẵn 1 bản ghi trong DB, sau đó cho N luồng cùng đọc lên và cùng UPDATE status.
     */
    public Map<String, Object> testOptimisticLocking(int concurrentUsers) {
        String campaignCode = "SUPERBOWL_OPTIMISTIC_TEST";
        // 1. Tạo sẵn 1 bản ghi ban đầu với status = "INITIAL", version = 0
        repository.deleteAll();
        CampaignExecution initialRecord = repository.save(
                CampaignExecution.builder()
                        .campaignCode(campaignCode)
                        .status("INITIAL")
                        .triggeredBy("System_Setup")
                        .threadName("Main_Setup")
                        .triggeredAt(LocalDateTime.now())
                        .build()
        );
        Long recordId = initialRecord.getId();
        log.info("[OptimisticTest] Đã tạo record ban đầu: ID = {}, version = {}", recordId, initialRecord.getVersion());
        // 2. Chuẩn bị bắn N luồng đồng thời cùng UPDATE vào bản ghi recordId này
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticFailureCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 1; i <= concurrentUsers; i++) {
            final int userId = i;
            futures.add(executor.submit(() -> {
                try {
                    startGun.await(); // Đứng chờ súng lệnh
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    // Gọi hàm update có @Transactional riêng biệt
                    updateCampaignStatus(recordId, "UPDATED_BY_USER_" + userId);
                    successCount.incrementAndGet();
                    log.info("[OptimisticTest] 🏆 User-{} UPDATE thành công! Version đã được tăng lên!", userId);
                } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                    // 💥 ĐÂY CHÍNH LÀ LỖI BẠN MUỐN THẤY!
                    optimisticFailureCount.incrementAndGet();
                    log.warn("[OptimisticTest] ⚡ User-{} bị dính OPTIMISTIC LOCK EXCEPTION! Chi tiết: {}",
                            userId, e.getMessage());
                } catch (Exception e) {
                    log.error("[OptimisticTest] Lỗi khác: {}", e.getMessage());
                }
            }));
        }
        // Bắn súng lệnh để các luồng cùng đập vào UPDATE
        startGun.countDown();
        for (Future<?> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();
        // Lấy lại record cuối cùng trong DB xem version đã nhảy lên bao nhiêu
        CampaignExecution finalRecord = repository.findById(recordId).orElseThrow();
        return Map.of(
                "testName", "Optimistic Locking Test (@Version)",
                "recordId", recordId,
                "concurrentUsers", concurrentUsers,
                "successCount", successCount.get(),
                "optimisticFailureCount", optimisticFailureCount.get(),
                "finalVersionInDb", finalRecord.getVersion(),
                "finalStatusInDb", finalRecord.getStatus(),
                "explanation", "Chỉ luồng đầu tiên update thành công (version 0 -> 1), các luồng còn lại cầm version 0 bị DB phát hiện đã cũ -> Ném OptimisticLockException!"
        );
    }
    /**
     * Hàm đọc lên và update. Lưu ý: Phải có @Transactional để Hibernate
     * tự động flush câu lệnh UPDATE ... WHERE id = ? AND version = ?
     */
    @Transactional
    public void updateCampaignStatus(Long id, String newStatus) {
        CampaignExecution record = repository.findById(id).orElseThrow();

        // Giả lập xử lý business 5ms để các luồng khác kịp đọc cùng version 0
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        record.setStatus(newStatus);
        repository.save(record);
        // Khi method kết thúc, Spring commit transaction -> Hibernate chạy UPDATE kèm kiểm tra version
    }
}
