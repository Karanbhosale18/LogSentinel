package com.digiplus.loganalyzer.controller;

import com.digiplus.loganalyzer.dto.LogResponse;
import com.digiplus.loganalyzer.dto.PageResponse;
import com.digiplus.loganalyzer.service.LogService;
import org.springframework.web.bind.annotation.*;

/** Convenience endpoint: the flagged entries only. */
@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final LogService logService;

    public AnomalyController(LogService logService) { this.logService = logService; }

    @GetMapping
    public PageResponse<LogResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "score") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        return logService.search(true, null, q, page, Math.min(Math.max(size, 1), 200), sort, dir);
    }
}
