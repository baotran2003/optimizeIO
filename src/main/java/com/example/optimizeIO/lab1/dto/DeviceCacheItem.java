package com.example.optimizeIO.lab1.dto;

/**
 * Record Java 17 đại diện cho dữ liệu nạp sẵn vào RAM.
 * Dùng Record cực kỳ nhẹ, tối ưu memory footprint hơn Entity JPA rất nhiều.
 */
public record DeviceCacheItem(long userId, String deviceToken, String osType) {
}
