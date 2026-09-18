package com.example.optimizeIO.lab1.controller;

import com.example.optimizeIO.lab1.service.day4.Day4DataStructureExperimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping({"/api/lab1/day4", "/api/lab1"})
@RequiredArgsConstructor
public class Day4Controller {

    private final Day4DataStructureExperimentService day4DataStructureExperimentService;

    /**
     * Day 4 Experiment: So sánh dung lượng RAM giữa ArrayList vs HashMap vs ConcurrentHashMap
     */
    @GetMapping({"/datastructures", "/experiment/day4-datastructures"})
    public ResponseEntity<?> testDay4DataStructures() {
        return ResponseEntity.ok(day4DataStructureExperimentService.compareDataStructures());
    }
}
