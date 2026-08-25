package com.example.vectorsearch.api;

import com.example.vectorsearch.stats.Statistics;
import com.example.vectorsearch.stats.StatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public Statistics stats() {
        return statisticsService.snapshot();
    }
}
