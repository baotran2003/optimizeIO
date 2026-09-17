package com.example.optimizeIO.lab1.dto;

import java.util.List;

public record BenchmarkResponse(
    int totalRecords,
    ExecutionDetail naiveDbApproach,
    ExecutionDetail inMemoryApproach,
    String speedupRatio,
    String conclusion
) {
    public record ExecutionDetail(
        String approachName,
        long durationMs,
        long throughputPerSecond,
        String bottleneckExplanation
    ) {}
}
