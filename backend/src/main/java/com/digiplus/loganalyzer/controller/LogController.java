package com.digiplus.loganalyzer.controller;

import com.digiplus.loganalyzer.dto.*;
import com.digiplus.loganalyzer.exception.BadRequestException;
import com.digiplus.loganalyzer.service.AnomalyScanService;
import com.digiplus.loganalyzer.service.LogIngestionService;
import com.digiplus.loganalyzer.service.LogService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogIngestionService ingestionService;
    private final LogService logService;
    private final AnomalyScanService scanService;

    public LogController(LogIngestionService ingestionService, LogService logService,
                         AnomalyScanService scanService) {
        this.ingestionService = ingestionService;
        this.logService = logService;
        this.scanService = scanService;
    }

    /** Upload and ingest a CSV of log entries. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded, or the file is empty.");
        }
        try {
            return ingestionService.ingestCsv(file.getInputStream());
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file: " + e.getMessage());
        }
    }

    /** Filtered, paginated list of logs. */
    @GetMapping
    public PageResponse<LogResponse> list(
            @RequestParam(required = false) Boolean anomaly,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "timestamp") String sort,
            @RequestParam(defaultValue = "asc") String dir) {
        return logService.search(anomaly, status, q, page, clampSize(size), sort, dir);
    }

    @GetMapping("/{id}")
    public LogResponse get(@PathVariable Long id) {
        return logService.getById(id);
    }

    /** Generate/refresh the AI explanation for a (flagged) entry. */
    @PostMapping("/{id}/analyze")
    public AiAnalysisResponse analyze(@PathVariable Long id) {
        return logService.analyze(id);
    }

    /** Re-run detection over the whole dataset (e.g. after tuning). */
    @PostMapping("/rescan")
    public Map<String, Object> rescan() {
        long anomalies = scanService.rescanAll();
        return Map.of("anomalies", anomalies);
    }

    @DeleteMapping
    public Map<String, Object> deleteAll() {
        long deleted = logService.deleteAll();
        return Map.of("deleted", deleted);
    }

    private static int clampSize(int size) {
        if (size < 1) return 1;
        return Math.min(size, 200);
    }
}
