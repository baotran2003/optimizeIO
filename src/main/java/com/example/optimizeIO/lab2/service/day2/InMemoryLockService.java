package com.example.optimizeIO.lab2.service.day2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class InMemoryLockService {

    // ✅ VŨ KHÍ CAS: AtomicBoolean bảo vệ 1 chiến dịch
    // false = chưa ai kích hoạt, true = đã có người kích hoạt rồi
    // TODO ①: Khai báo AtomicBoolean campaignLock khởi tạo là false
    private final AtomicBoolean campaignLock = new AtomicBoolean(false);

    // Đếm kết quả
    private final AtomicInteger successCount  = new AtomicInteger(0);
    private final AtomicInteger rejectedCount = new AtomicInteger(0);
    private final AtomicLong    totalRejectionNs = new AtomicLong(0); // Tổng ns bị reject

    private final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

    /**
     * Ngày 2 — Khóa In-Memory bằng CAS (Compare-And-Swap)
     * Giả lập N user đồng thời bấm nút, chỉ duy nhất 1 Winner được kích hoạt.
     */
    public Map<String, Object> simulateCasLock(int concurrentUsers) {
        String campaignCode = "SUPERBOWL_2024";

        // Reset trước mỗi lần chạy
        // TODO ②: Reset campaignLock về false bằng campaignLock.set(false)
        campaignLock.set(false);
        successCount.set(0);
        rejectedCount.set(0);
        totalRejectionNs.set(0);
        executionLog.clear();

        log.info("[Lab2-Day2] Bắt đầu CAS test với {} users...", concurrentUsers);

        ExecutorService executor   = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch  startGun   = new CountDownLatch(1);
        List<Future<?>> futures    = new ArrayList<>();

        for (int i = 1; i <= concurrentUsers; i++) {
            final int userId = i;
            futures.add(executor.submit(() -> {
                try {
                    startGun.await(); // Chờ súng lệnh
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                long attemptStart = System.nanoTime();

                // ✅ CAS ATOMIC: compareAndSet(false, true)
                // → Nếu campaignLock đang là false, đổi sang true và trả về true (WINNER)
                // → Nếu campaignLock đã là true rồi,  không đổi  và trả về false (LOSER)
                // TODO ③: Gọi campaignLock.compareAndSet(false, true) để tranh giành quyền kích hoạt
                boolean isWinner = campaignLock.compareAndSet(false, true);

                if (isWinner) {
                    // Chỉ 1 luồng duy nhất vào được đây!
                    successCount.incrementAndGet();
                    String msg = "User-" + userId + " là WINNER — Kích hoạt " + campaignCode;
                    executionLog.add(msg);
                    log.info("[Lab2-Day2] 🏆 {}", msg);

                } else {
                    // Tất cả các luồng còn lại bị từ chối ngay lập tức
                    long rejectionNs = System.nanoTime() - attemptStart;
                    // TODO ④: Tăng rejectedCount và cộng rejectionNs vào totalRejectionNs
                    rejectedCount.incrementAndGet();
                    totalRejectionNs.addAndGet(rejectionNs);
                }
            }));
        }

        // Bắn súng lệnh
        startGun.countDown();

        for (Future<?> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        // Tính thời gian từ chối trung bình (ns)
        // TODO ⑤: Tính avgRejectionNs = totalRejectionNs / rejectedCount (nếu rejectedCount > 0, ngược lại = 0)
        long avgRejectionNs = rejectedCount.get() > 0 ? totalRejectionNs.get() / rejectedCount.get() : 0;

        log.info("[Lab2-Day2] Kết quả: {} winner, {} rejected, từ chối TB {}ns",
                successCount.get(), rejectedCount.get(), avgRejectionNs);

        return Map.of(
            "approach",          "In-Memory CAS (AtomicBoolean.compareAndSet)",
            "campaignCode",      campaignCode,
            "concurrentUsers",   concurrentUsers,
            "successCount",      successCount.get(),
            "rejectedCount",     rejectedCount.get(),
            "avgRejectionNs",    avgRejectionNs,
            "avgRejectionMs",    avgRejectionNs / 1_000_000.0,
            "executionLog",      executionLog,
            "verdict",           successCount.get() == 1
                                 ? "✅ HOÀN HẢO! Đúng 1 Winner, " + rejectedCount.get() + " bị từ chối siêu tốc!"
                                 : "❌ CÓ LỖI! successCount = " + successCount.get()
        );
    }
}
