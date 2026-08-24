package com.digiplus.loganalyzer.controller;

import com.digiplus.loganalyzer.dto.StatsResponse;
import com.digiplus.loganalyzer.service.LogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final LogService logService;

    public StatsController(LogService logService) { this.logService = logService; }

    @GetMapping
    public StatsResponse stats() { return logService.stats(); }
}
