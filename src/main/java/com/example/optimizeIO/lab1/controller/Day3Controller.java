package com.example.optimizeIO.lab1.controller;

import com.example.optimizeIO.lab1.service.day3.BatchInsertExperimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping({"/api/lab1/day3", "/api/lab1"})
@RequiredArgsConstructor
public class Day3Controller {

    private final BatchInsertExperimentService batchInsertExperimentService;

    /**
     * Day 3 Experiment: Chèn 2,000 bản ghi bằng JPA saveAll()
     */
    @PostMapping({"/batch-jpa", "/experiment/batch-jpa"})
    public ResponseEntity<?> testBatchJpa(@RequestParam(defaultValue = "2000") int count) {
        return ResponseEntity.ok(batchInsertExperimentService.testJpaSaveAll(count));
    }

    /**
     * Day 3 Experiment: Chèn 2,000 bản ghi bằng JDBC batchUpdate()
     */
    @PostMapping({"/batch-jdbc", "/experiment/batch-jdbc"})
    public ResponseEntity<?> testBatchJdbc(@RequestParam(defaultValue = "2000") int count) {
        return ResponseEntity.ok(batchInsertExperimentService.testJdbcBatch(count));
    }
}
