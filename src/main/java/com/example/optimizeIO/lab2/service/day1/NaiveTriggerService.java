package com.example.optimizeIO.lab2.service.day1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class NaiveTriggerService {

    // Giả lập "database" bằng một Set trong RAM
    // (thay vì JPA để bài tập đơn giản, tập trung vào hiện tượng Race Condition)
    private final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

    // Đếm số lần chiến dịch bị kích hoạt thành công (lẽ ra chỉ được = 1)
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger rejectedCount = new AtomicInteger(0);

    // "Database" lưu chiến dịch đã chạy — dùng Set thông thường để KHÔNG thread-safe
    // ❌ Cố tình dùng HashSet (không an toàn) để tái hiện Race Condition
    private final Set<String> campaignDatabase = new HashSet<>();

    /**
     * Bài Ngày 1 — Tái hiện Race Condition với Check-Then-Act
     * Giả lập N người cùng bấm nút kích hoạt chiến dịch SUPERBOWL_2024 đồng thời.
     */
    public Map<String, Object> simulateRaceCondition(int concurrentUsers) {
        String campaignCode = "SUPERBOWL_2024";

        // Reset lại trạng thái trước mỗi lần chạy
        campaignDatabase.clear();
        executionLog.clear();
        successCount.set(0);
        rejectedCount.set(0);

        log.info("[Lab2-Day1] Bắt đầu: {} user đồng thời bấm nút...", concurrentUsers);

        // TODO ①: Tạo ExecutorService với fixed thread pool = concurrentUsers
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);

        // TODO ②: Tạo CountDownLatch(1) làm "súng lệnh" — đảm bảo tất cả thread cùng xuất phát 1 lúc
        CountDownLatch startGun = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 1; i <= concurrentUsers; i++) {
            final int userId = i;
            futures.add(executor.submit(() -> {

                // TODO ③: Chờ súng lệnh — startGun.await()
                try {
                    startGun.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // ❌ CẠM BẪY CHECK-THEN-ACT: 2 bước này KHÔNG phải atomic!
                // Bước 1 - CHECK: Kiểm tra chiến dịch đã chạy chưa
                boolean alreadyExists = campaignDatabase.contains(campaignCode);

                if (!alreadyExists) {
                    // ⚠️ KHOẢNG TRỐNG CHẾT NGƯỜI: Nhiều thread cùng lọt qua đây!
                    // Giả lập độ trễ xử lý nhỏ (0.1ms) — đủ để thread khác chen vào
                    try { Thread.sleep(0, 100_000); }
                    catch (InterruptedException ignored) {}

                    // Bước 2 - ACT: Lưu vào database và kích hoạt
                    // TODO ④: Gọi hàm executeCampaign(campaignCode, userId) và lưu kết quả
                    boolean fired = executeCampaign(campaignCode, userId);

                    if (fired) {
                        successCount.incrementAndGet();
                        log.warn("[Lab2-Day1] ⚡ User-{} KÍCH HOẠT thành công! (Tổng: {})", userId, successCount.get());
                    }
                } else {
                    rejectedCount.incrementAndGet();
                }
            }));
        }

        // TODO ⑤: Bắn súng lệnh — startGun.countDown()
        startGun.countDown();

        // Chờ tất cả hoàn thành
        for (Future<?> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }

        // TODO ⑥: Shutdown executor
        executor.shutdown();

        log.info("[Lab2-Day1] Kết quả: {} lần kích hoạt, {} lần từ chối", successCount.get(), rejectedCount.get());

        return Map.of(
                "campaignCode",     campaignCode,
                "concurrentUsers",  concurrentUsers,
                "successCount",     successCount.get(),     // ❌ Nếu > 1 → Race Condition xảy ra!
                "rejectedCount",    rejectedCount.get(),
                "bugDetected",      successCount.get() > 1, // true = hệ thống bị lỗi
                "executionLog",     executionLog,
                "verdict",          successCount.get() > 1
                        ? "❌ RACE CONDITION! Chiến dịch bị kích hoạt " + successCount.get() + " lần!"
                        : "✅ May mắn không bị lỗi lần này (thử lại với số lượng lớn hơn)"
        );
    }

    /**
     * Giả lập lưu vào DB và kích hoạt campaign
     * Trả về true nếu là thread đầu tiên lưu được (thắng race)
     */
    private boolean executeCampaign(String campaignCode, int userId) {
        // ❌ Đây chính là điểm dễ bị race: add() trên HashSet không atomic!
        boolean isNew = campaignDatabase.add(campaignCode);
        if (isNew) {
            String log_msg = "User-" + userId + " fired campaign " + campaignCode;
            executionLog.add(log_msg);
        }
        return isNew;
    }
}