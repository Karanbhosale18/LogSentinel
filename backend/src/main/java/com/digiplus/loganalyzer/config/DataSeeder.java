package com.digiplus.loganalyzer.config;

import com.digiplus.loganalyzer.dto.UploadResponse;
import com.digiplus.loganalyzer.repository.LogEntryRepository;
import com.digiplus.loganalyzer.service.LogIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * On first start (empty database) optionally loads the bundled sample dataset so
 * the app is immediately demoable and the persistence story is visible. Controlled
 * by {@code app.seed.enabled} (default true). Never fails application startup.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final LogEntryRepository repository;
    private final LogIngestionService ingestionService;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    public DataSeeder(LogEntryRepository repository, LogIngestionService ingestionService) {
        this.repository = repository;
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;
        if (repository.count() > 0) {
            log.info("Database already contains {} log entries; skipping seed.", repository.count());
            return;
        }
        try (InputStream in = new ClassPathResource("sample-logs.csv").getInputStream()) {
            UploadResponse r = ingestionService.ingestCsv(in);
            log.info("Seeded sample dataset: {}", r.message());
        } catch (Exception e) {
            log.warn("Could not seed sample dataset (this is non-fatal): {}", e.getMessage());
        }
    }
}
