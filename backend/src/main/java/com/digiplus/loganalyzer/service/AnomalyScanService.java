package com.digiplus.loganalyzer.service;

import com.digiplus.loganalyzer.detector.AnomalyDetector;
import com.digiplus.loganalyzer.detector.DetectionResult;
import com.digiplus.loganalyzer.detector.DetectorConfig;
import com.digiplus.loganalyzer.entity.LogEntry;
import com.digiplus.loganalyzer.repository.LogEntryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Runs the anomaly detector across the whole persisted dataset and writes the
 * results back onto each entry. A full re-scan is used (rather than scoring only
 * the newest batch) so the learned baselines always reflect all available data —
 * important because rarity and per-source volume are relative to the full set.
 */
@Service
public class AnomalyScanService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyScanService.class);

    private final LogEntryRepository repository;
    private final DetectorConfig detectorConfig;
    private final ObjectMapper mapper;

    public AnomalyScanService(LogEntryRepository repository, DetectorConfig detectorConfig, ObjectMapper mapper) {
        this.repository = repository;
        this.detectorConfig = detectorConfig;
        this.mapper = mapper;
    }

    /** Re-scores every log entry. Returns the number of anomalies flagged. */
    @Transactional
    public long rescanAll() {
        List<LogEntry> all = repository.findAll();
        if (all.isEmpty()) return 0;

        AnomalyDetector detector = new AnomalyDetector(detectorConfig);
        detector.fit(all);

        long anomalies = 0;
        for (LogEntry e : all) {
            DetectionResult r = detector.score(e);
            e.setAnomaly(r.anomaly());
            e.setAnomalyScore(r.score());
            e.setAnomalyReason(r.reason());
            e.setSignalBreakdown(writeSignals(r));
            if (r.anomaly()) anomalies++;
        }
        repository.saveAll(all);
        log.info("Re-scan complete: {} entries, {} anomalies", all.size(), anomalies);
        return anomalies;
    }

    private String writeSignals(DetectionResult r) {
        try {
            return mapper.writeValueAsString(r.signals());
        } catch (Exception e) {
            return null;
        }
    }
}
