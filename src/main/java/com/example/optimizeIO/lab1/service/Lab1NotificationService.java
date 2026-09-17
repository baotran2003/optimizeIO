package com.example.optimizeIO.lab1.service;

import com.example.optimizeIO.lab1.dto.BenchmarkResponse;
import com.example.optimizeIO.lab1.dto.BenchmarkResponse.ExecutionDetail;
import com.example.optimizeIO.lab1.dto.DeviceCacheItem;
import com.example.optimizeIO.lab1.entity.UserDevice;
import com.example.optimizeIO.lab1.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class Lab1NotificationService {

    private final UserDeviceRepository userDeviceRepository;
    private final InMemoryPreloadService inMemoryPreloadService;

    /**
     * Cách 1 (Naive CRUD): Khi bấm nút, backend mới query Database để lấy danh sách thiết bị.
     */
    public ExecutionDetail executeNaiveDbApproach() {
        log.info("--- BẮT ĐẦU: Naive DB Approach ---");
        long start = System.currentTimeMillis();

        // 1. Tốn Network I/O, Disk I/O & Hibernate Overhead để load 100k entities
        List<UserDevice> devices = userDeviceRepository.findAll();

        // 2. Giả lập chuẩn bị payload notification
        AtomicLong processedCount = new AtomicLong();
        for (UserDevice device : devices) {
            // Giả lập đọc token và tạo payload gửi push
            String payload = "{\"to\":\"" + device.getDeviceToken() + "\",\"title\":\"Học đi bạn êi!\"}";
            if (!payload.isEmpty()) {
                processedCount.incrementAndGet();
            }
        }

        long durationMs = Math.max(1, System.currentTimeMillis() - start);
        long throughput = (processedCount.get() * 1000L) / durationMs;

        log.info("Naive DB Approach: Xử lý {} bản ghi trong {} ms ({} ops/s)",
                processedCount.get(), durationMs, throughput);

        return new ExecutionDetail(
            "Naive DB Query (JPA findAll)",
            durationMs,
            throughput,
            "Nghẽn tại Database Network I/O, Disk I/O và Hibernate Entity Management (First-Level Cache, Dirty Checking)."
        );
    }

    /**
     * Cách 2 (Duolingo Style): Đọc trực tiếp từ In-Memory đã nạp trước.
     */
    public ExecutionDetail executeInMemoryApproach() {
        log.info("--- BẮT ĐẦU: In-Memory Duolingo Approach ---");

        if (!inMemoryPreloadService.isPreloaded() || inMemoryPreloadService.size() == 0) {
            log.info("Chưa pre-warm, tự động pre-warm trước khi chạy...");
            inMemoryPreloadService.preload();
        }

        long start = System.currentTimeMillis();

        List<DeviceCacheItem> devices = inMemoryPreloadService.getInMemoryDevices();
        AtomicLong processedCount = new AtomicLong();

        // Duyệt trực tiếp trên RAM (O(1) access time, CPU Cache Friendly)
        for (DeviceCacheItem device : devices) {
            String payload = "{\"to\":\"" + device.deviceToken() + "\",\"title\":\"Học đi bạn êi!\"}";
            if (!payload.isEmpty()) {
                processedCount.incrementAndGet();
            }
        }

        long durationMs = Math.max(1, System.currentTimeMillis() - start);
        long throughput = (processedCount.get() * 1000L) / durationMs;

        log.info("In-Memory Approach: Xử lý {} bản ghi trong {} ms ({} ops/s)",
                processedCount.get(), durationMs, throughput);

        return new ExecutionDetail(
            "Duolingo In-Memory Preload",
            durationMs,
            throughput,
            "Không tốn I/O Database hay Disk. Dữ liệu nằm sẵn trên RAM, tốc độ đọc CPU L1/L2/L3 Cache cực nhanh."
        );
    }

    /**
     * Chạy cả 2 để so sánh trực tiếp hiệu năng.
     */
    public BenchmarkResponse runComparisonBenchmark() {
        ExecutionDetail inMemoryDetail = executeInMemoryApproach();
        ExecutionDetail naiveDbDetail = executeNaiveDbApproach();

        double speedup = (double) naiveDbDetail.durationMs() / inMemoryDetail.durationMs();

        String conclusion = String.format(
            "Phương pháp In-Memory của Duolingo nhanh gấp %.1f lần so với việc query Database tại thời điểm cao điểm! " +
            "Đây là lý do tại sao tại giờ G (5 giây phát quảng cáo), Duolingo loại bỏ 100%% truy vấn tới Database.",
            speedup
        );

        return new BenchmarkResponse(
            inMemoryPreloadService.size(),
            naiveDbDetail,
            inMemoryDetail,
            String.format("%.1fx faster", speedup),
            conclusion
        );
    }
}
