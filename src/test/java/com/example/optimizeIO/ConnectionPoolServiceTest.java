package com.example.optimizeIO;

import com.example.optimizeIO.lab1.service.day5.ConnectionPoolService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConnectionPoolServiceTest {

    @Autowired
    private ConnectionPoolService connectionPoolService;

    @Test
    @DisplayName("Test Stress Test với 15 threads đồng thời")
    void testRunConcurrentStressTest() {
        int concurrentRequests = 15;

        Map<String, Object> result = connectionPoolService.runConcurrentStressTest(concurrentRequests);

        // In kết quả ra console để quan sát
        System.out.println("=== KẾT QUẢ TEST ===");
        System.out.println("Tổng thời gian: " + result.get("overallDurationMs") + " ms");
        System.out.println("Chi tiết từng thread:");
        result.forEach((k, v) -> System.out.println(k + ": " + v));

        // Kiểm tra cơ bản
        assertNotNull(result);
        assertEquals(concurrentRequests, result.get("concurrentRequests"));
        assertTrue((Long) result.get("overallDurationMs") > 2000,
                "Tổng thời gian phải lớn hơn 2000ms do nghẽn connection");
    }
}
