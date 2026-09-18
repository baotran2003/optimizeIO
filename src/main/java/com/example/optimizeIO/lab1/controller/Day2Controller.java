package com.example.optimizeIO.lab1.controller;

import com.example.optimizeIO.lab1.service.day2.JpaMemoryExperimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping({"/api/lab1/day2", "/api/lab1"})
@RequiredArgsConstructor
public class Day2Controller {

    private final JpaMemoryExperimentService jpaMemoryExperimentService;

    /**
     * Day 2 Experiment: Đo lượng RAM và thời gian của JPA findAll()
     */
    @GetMapping({"/jpa-memory", "/experiment/jpa-memory"})
    public ResponseEntity<?> testJpaMemory() {
        return ResponseEntity.ok(jpaMemoryExperimentService.measureJpaMemoryConsumption());
    }

    /**
     * Day 2 Experiment: Đo lượng RAM và thời gian của JDBC Record
     */
    @GetMapping({"/jdbc-memory", "/experiment/jdbc-memory"})
    public ResponseEntity<?> testJdbcMemory() {
        return ResponseEntity.ok(jpaMemoryExperimentService.measureJdbcRecordMemoryConsumption());
    }
}
