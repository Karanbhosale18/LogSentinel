package com.digiplus.loganalyzer.service;

import com.digiplus.loganalyzer.dto.UploadResponse;
import com.digiplus.loganalyzer.dto.ValidationIssue;
import com.digiplus.loganalyzer.entity.LogEntry;
import com.digiplus.loganalyzer.exception.BadRequestException;
import com.digiplus.loganalyzer.ingest.CsvLogParser;
import com.digiplus.loganalyzer.ingest.LogValidator;
import com.digiplus.loganalyzer.ingest.ParsedRow;
import com.digiplus.loganalyzer.repository.LogEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Orchestrates: parse CSV -> validate -> persist valid rows -> re-scan for anomalies. */
@Service
public class LogIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LogIngestionService.class);
    private static final int MAX_REPORTED_ISSUES = 100;

    private final CsvLogParser parser;
    private final LogValidator validator;
    private final LogEntryRepository repository;
    private final AnomalyScanService scanService;

    public LogIngestionService(CsvLogParser parser, LogValidator validator,
                               LogEntryRepository repository, AnomalyScanService scanService) {
        this.parser = parser;
        this.validator = validator;
        this.repository = repository;
        this.scanService = scanService;
    }

    public UploadResponse ingestCsv(InputStream in) {
        CsvLogParser.ParseResult parsed;
        try {
            parsed = parser.parse(in);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file: " + e.getMessage());
        }

        // Basic validation issue: empty dataset
        if (parsed.dataLineCount() == 0) {
            throw new BadRequestException("The uploaded file contains no log records (empty dataset).");
        }
        if (parsed.recognizedColumns().isEmpty()) {
            throw new BadRequestException(
                    "No recognizable log columns found in the header. Expected at least a timestamp column.");
        }

        List<LogEntry> valid = new ArrayList<>();
        List<ValidationIssue> issues = new ArrayList<>();
        int skipped = 0;

        for (ParsedRow row : parsed.rows()) {
            LogValidator.Result result = validator.validate(row);
            if (result.valid()) {
                valid.add(result.entry());
            } else {
                skipped++;
                if (issues.size() < MAX_REPORTED_ISSUES) {
                    issues.add(new ValidationIssue(row.lineNumber(), result.error(), sample(row.rawLine())));
                }
            }
        }

        if (!valid.isEmpty()) {
            repository.saveAll(valid);
        }

        long anomalies = scanService.rescanAll();

        String message = String.format("Ingested %d of %d rows (%d skipped). %d anomalies in dataset.",
                valid.size(), parsed.dataLineCount(), skipped, anomalies);
        log.info(message);

        return new UploadResponse(parsed.dataLineCount(), valid.size(), skipped, anomalies,
                parsed.recognizedColumns(), issues, message);
    }

    private static String sample(String raw) {
        if (raw == null) return "";
        return raw.length() > 160 ? raw.substring(0, 160) + "..." : raw;
    }
}
