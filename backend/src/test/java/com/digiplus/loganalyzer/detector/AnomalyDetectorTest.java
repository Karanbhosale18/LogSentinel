package com.digiplus.loganalyzer.detector;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the detector's core behaviour and, crucially, that it does NOT simply
 * flag common HTTP errors — it flags statistically rare / behaviourally abnormal
 * entries. Mirrors the Python prototype validated against the real dataset.
 */
class AnomalyDetectorTest {

    private final LocalDateTime base = LocalDateTime.of(2023, 1, 1, 0, 0, 0);

    /** Build a "mostly normal" dataset: common countries/agents, one request per IP. */
    private List<LogView> normalDataset(int n) {
        String[] countries = {"USA", "India", "Germany", "Brazil", "France", "Canada", "China"};
        String[] agents = {"Chrome", "Firefox", "Safari", "Edge", "Opera"};
        int[] codes = {200, 301, 403, 404, 500}; // uniformly common -> should be treated as normal
        String[] methods = {"GET", "POST", "PUT", "DELETE"};
        List<LogView> logs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            logs.add(new TestLog(base.plusMinutes(i), "10.0." + (i / 250) + "." + (i % 250),
                    codes[i % codes.length], methods[i % methods.length],
                    agents[i % agents.length], countries[i % countries.length]));
        }
        return logs;
    }

    @Test
    void commonHttp500IsNotFlagged() {
        List<LogView> logs = normalDataset(2000);
        AnomalyDetector d = new AnomalyDetector(new DetectorConfig());
        d.fit(logs);

        // a perfectly ordinary 500 from a normal country / unique IP
        LogView ordinary500 = new TestLog(base, "10.0.0.7", 500, "POST", "Chrome", "USA");
        DetectionResult r = d.score(ordinary500);
        assertFalse(r.anomaly(), "A common HTTP 500 must NOT be flagged in this dataset");
        assertTrue(r.score() < 0.7);
    }

    @Test
    void rareLocationIsFlagged() {
        List<LogView> logs = normalDataset(2000);
        // inject a handful of rare-location rows
        for (int i = 0; i < 3; i++) {
            logs.add(new TestLog(base.plusHours(i), "8.8.4." + i, 200, "GET", "Chrome", "North Korea"));
        }
        AnomalyDetector d = new AnomalyDetector(new DetectorConfig());
        d.fit(logs);

        LogView rare = new TestLog(base, "8.8.4.99", 200, "GET", "Chrome", "North Korea");
        DetectionResult r = d.score(rare);
        assertTrue(r.anomaly(), "A rare location should be flagged");
        assertTrue(r.reason().toLowerCase().contains("rare"));
        assertTrue(r.signals().stream().anyMatch(s -> s.name().equals("rarity") && s.rawScore() > 0));
    }

    @Test
    void abnormalSourceVolumeIsFlagged() {
        List<LogView> logs = normalDataset(2000);
        // one noisy IP making many requests
        for (int i = 0; i < 40; i++) {
            logs.add(new TestLog(base.plusMinutes(i * 5), "66.66.66.66",
                    new int[]{200, 301, 403, 404, 500}[i % 5], "GET", "Chrome", "USA"));
        }
        AnomalyDetector d = new AnomalyDetector(new DetectorConfig());
        d.fit(logs);

        LogView noisy = new TestLog(base, "66.66.66.66", 200, "GET", "Chrome", "USA");
        DetectionResult r = d.score(noisy);
        assertTrue(r.anomaly(), "An IP with abnormally high volume should be flagged");
        assertTrue(r.signals().stream().anyMatch(s -> s.name().equals("source") && s.rawScore() > 0));
    }

    @Test
    void anomalyRateIsSmallOnMostlyNormalData() {
        List<LogView> logs = new ArrayList<>(normalDataset(2000));
        for (int i = 0; i < 3; i++) logs.add(new TestLog(base.plusHours(i), "8.8.4." + i, 500, "GET", "Bot", "North Korea"));
        for (int i = 0; i < 40; i++) logs.add(new TestLog(base.plusMinutes(i), "66.66.66.66", 500, "GET", "Chrome", "USA"));

        AnomalyDetector d = new AnomalyDetector(new DetectorConfig());
        d.fit(logs);
        long flagged = logs.stream().filter(l -> d.score(l).anomaly()).count();
        double rate = flagged / (double) logs.size();
        assertTrue(rate < 0.10, "Anomaly rate should stay small (was " + rate + ")");
        assertTrue(flagged >= 43, "Both injected anomaly groups should be caught (was " + flagged + ")");
    }

    @Test
    void scoreClampedAndDeterministic() {
        List<LogView> logs = normalDataset(500);
        AnomalyDetector d = new AnomalyDetector(new DetectorConfig());
        d.fit(logs);
        LogView x = logs.get(0);
        DetectionResult a = d.score(x);
        DetectionResult b = d.score(x);
        assertEquals(a.score(), b.score(), "Detector must be deterministic");
        assertTrue(a.score() >= 0.0 && a.score() <= 1.0, "Score must be within [0,1]");
    }

    @Test
    void fitMustBeCalledFirst() {
        AnomalyDetector d = new AnomalyDetector(new DetectorConfig());
        assertThrows(IllegalStateException.class,
                () -> d.score(new TestLog(base, "1.1.1.1", 200, "GET", "Chrome", "USA")));
    }
}
