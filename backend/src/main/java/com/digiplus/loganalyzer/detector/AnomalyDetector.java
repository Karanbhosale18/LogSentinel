package com.digiplus.loganalyzer.detector;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven, explainable anomaly detector.
 *
 * <p><b>Design.</b> The detector runs in two phases:
 * <ol>
 *   <li><b>fit</b> — learn baselines from the whole dataset (categorical
 *       frequencies, per-source request volume statistics, per-source event
 *       timelines). Nothing is hard-coded to a specific value such as
 *       "HTTP 500"; what counts as unusual is derived from the data itself.</li>
 *   <li><b>score</b> — combine several normalized signals into a single 0..1
 *       score via a weighted sum, and flag entries whose score crosses a
 *       threshold.</li>
 * </ol>
 *
 * <p><b>Why data-driven?</b> In a dataset where 20% of requests are HTTP 500s,
 * naively flagging every 500 would flag 20% of the data. Instead we flag values
 * that are statistically <i>rare</i> (e.g. an unusual location), sources that
 * behave <i>abnormally</i> (e.g. one IP making far more requests than any other),
 * and short bursts of activity — while treating individually-common errors as
 * normal. The AI is never consulted here; detection is entirely our own logic.
 *
 * <p>The class depends only on {@link LogView} and the JDK, so it is trivially
 * unit-testable without Spring or a database.
 */
public class AnomalyDetector {

    private static final java.util.Set<Integer> ERROR_CODES =
            java.util.Set.of(400, 401, 403, 404, 408, 429, 500, 502, 503, 504);

    private final DetectorConfig cfg;

    // ---- learned baselines (populated by fit) ----
    private int total;
    private final Map<String, Integer> locationCounts = new HashMap<>();
    private final Map<String, Integer> userAgentCounts = new HashMap<>();
    private final Map<String, Integer> ipCounts = new HashMap<>();
    private final Map<String, long[]> ipEpochSeconds = new HashMap<>(); // sorted per IP
    private double volumeLow;   // count at which source-volume score starts rising
    private double volumeHigh;  // count at which source-volume score saturates to 1
    private boolean fitted = false;

    public AnomalyDetector(DetectorConfig cfg) {
        this.cfg = cfg;
    }

    /** Learn baseline statistics from the full set of logs. */
    public void fit(List<? extends LogView> logs) {
        reset();
        this.total = logs.size();
        Map<String, List<Long>> ipTimes = new HashMap<>();

        for (LogView log : logs) {
            inc(locationCounts, norm(log.getLocation()));
            inc(userAgentCounts, norm(log.getUserAgent()));
            String ip = norm(log.getIpAddress());
            inc(ipCounts, ip);
            LocalDateTime ts = log.getTimestamp();
            if (ts != null) {
                ipTimes.computeIfAbsent(ip, k -> new ArrayList<>())
                       .add(ts.toEpochSecond(ZoneOffset.UTC));
            }
        }

        // freeze sorted per-IP timelines for O(log n) window queries
        for (var e : ipTimes.entrySet()) {
            long[] arr = e.getValue().stream().mapToLong(Long::longValue).sorted().toArray();
            ipEpochSeconds.put(e.getKey(), arr);
        }

        // robust per-source volume thresholds from mean & std of per-IP counts
        double[] counts = ipCounts.values().stream().mapToDouble(Integer::doubleValue).toArray();
        double mean = 0, std = 0;
        if (counts.length > 0) {
            for (double c : counts) mean += c;
            mean /= counts.length;
            double var = 0;
            for (double c : counts) var += (c - mean) * (c - mean);
            std = Math.sqrt(var / counts.length);
        }
        // guard against a degenerate std (all sources identical volume)
        double effectiveStd = Math.max(std, 0.5);
        this.volumeLow = mean + cfg.volumeSigmaLow * effectiveStd;
        this.volumeHigh = mean + cfg.volumeSigmaHigh * effectiveStd;
        if (volumeHigh <= volumeLow) volumeHigh = volumeLow + 1;

        this.fitted = true;
    }

    /** Score a single entry. {@link #fit(List)} must have been called first. */
    public DetectionResult score(LogView log) {
        if (!fitted) throw new IllegalStateException("AnomalyDetector.fit(...) must be called before score(...)");

        List<SignalContribution> signals = new ArrayList<>(4);
        signals.add(rarity(log));
        signals.add(sourceVolume(log));
        signals.add(severity(log));
        signals.add(burst(log));

        double sum = 0;
        for (SignalContribution s : signals) sum += s.weightedScore();
        double score = Math.min(1.0, sum);
        boolean anomaly = score >= cfg.threshold;

        String reason = buildReason(signals, anomaly);
        return new DetectionResult(anomaly, round(score), reason, signals);
    }

    // ------------------------------------------------------------------
    // Signals
    // ------------------------------------------------------------------

    /** Signal 1 — rarity of a categorical value (location / user-agent). */
    private SignalContribution rarity(LogView log) {
        String loc = norm(log.getLocation());
        String ua = norm(log.getUserAgent());
        double pLoc = probability(locationCounts, loc);
        double pUa = probability(userAgentCounts, ua);

        String field, value;
        double prob;
        if (pLoc <= pUa) { field = "location"; value = loc; prob = pLoc; }
        else { field = "user agent"; value = ua; prob = pUa; }

        double raw = raritySurprisal(prob);
        String detail = raw > 0
                ? String.format("Rare %s \"%s\" — seen in only %.2f%% of traffic", field, value, prob * 100)
                : "";
        return new SignalContribution("rarity", "Rare value", round(raw),
                cfg.weightRarity, round(raw * cfg.weightRarity), detail);
    }

    /** Signal 2 — abnormal request volume from a single source IP. */
    private SignalContribution sourceVolume(LogView log) {
        String ip = norm(log.getIpAddress());
        int count = ipCounts.getOrDefault(ip, 0);
        double raw = 0;
        if (count > volumeLow) {
            raw = clamp01((count - volumeLow) / (volumeHigh - volumeLow));
        }
        String detail = raw > 0
                ? String.format("Source IP %s produced %d requests — far above the per-source baseline (~%.0f)",
                        ip, count, (double) Math.max(1, Math.round(volumeLow)))
                : "";
        return new SignalContribution("source", "Source behaviour", round(raw),
                cfg.weightSource, round(raw * cfg.weightSource), detail);
    }

    /** Signal 3 — HTTP status severity (minor weight; 5xx worse than 4xx). */
    private SignalContribution severity(LogView log) {
        Integer code = log.getStatusCode();
        double raw = 0;
        String detail = "";
        if (code != null) {
            if (code >= 500) { raw = 1.0; detail = "Server error (HTTP " + code + ")"; }
            else if (code >= 400) { raw = 0.4; detail = "Client error (HTTP " + code + ")"; }
        }
        return new SignalContribution("severity", "Status severity", round(raw),
                cfg.weightSeverity, round(raw * cfg.weightSeverity), detail);
    }

    /** Signal 4 — burst of activity from the same source in a short window. */
    private SignalContribution burst(LogView log) {
        double raw = 0;
        int inWindow = 0;
        LocalDateTime ts = log.getTimestamp();
        long[] times = ipEpochSeconds.get(norm(log.getIpAddress()));
        if (ts != null && times != null && times.length >= cfg.burstMinEvents) {
            long t = ts.toEpochSecond(ZoneOffset.UTC);
            inWindow = countInWindow(times, t, cfg.burstWindowSeconds);
            if (inWindow >= cfg.burstMinEvents) {
                // scale so that a handful of extra same-source events saturates
                raw = clamp01((inWindow - (cfg.burstMinEvents - 1)) / 5.0);
            }
        }
        String detail = raw > 0
                ? String.format("Burst: %d requests from this IP within %d min",
                        inWindow, cfg.burstWindowSeconds / 60)
                : "";
        return new SignalContribution("burst", "Frequency burst", round(raw),
                cfg.weightBurst, round(raw * cfg.weightBurst), detail);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Normalized surprisal: 0 for common values, ramping to 1 for very rare ones. */
    private double raritySurprisal(double prob) {
        if (prob <= 0) prob = 1e-9;
        if (prob >= cfg.rarityCommonProb) return 0.0;
        double s = -log2(prob);
        double sLo = -log2(cfg.rarityCommonProb);
        double sHi = -log2(cfg.rarityRareProb);
        if (sHi <= sLo) return 1.0;
        return clamp01((s - sLo) / (sHi - sLo));
    }

    private double probability(Map<String, Integer> counts, String key) {
        if (total == 0) return 1.0;
        return counts.getOrDefault(key, 0) / (double) total;
    }

    /** Count events within +/- window seconds of t, using binary search on sorted times. */
    private static int countInWindow(long[] sortedTimes, long t, long window) {
        long lo = t - window, hi = t + window;
        int left = lowerBound(sortedTimes, lo);
        int right = upperBound(sortedTimes, hi);
        return right - left;
    }

    private static int lowerBound(long[] a, long key) {
        int lo = 0, hi = a.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (a[mid] < key) lo = mid + 1; else hi = mid; }
        return lo;
    }

    private static int upperBound(long[] a, long key) {
        int lo = 0, hi = a.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (a[mid] <= key) lo = mid + 1; else hi = mid; }
        return lo;
    }

    private static String buildReason(List<SignalContribution> signals, boolean anomaly) {
        List<String> parts = new ArrayList<>();
        for (SignalContribution s : signals) {
            if (s.rawScore() > 0 && s.detail() != null && !s.detail().isBlank()) {
                parts.add(s.detail());
            }
        }
        if (parts.isEmpty()) return anomaly ? "Flagged by combined signals" : "No anomalous signals";
        return String.join("; ", parts);
    }

    private void reset() {
        total = 0;
        locationCounts.clear();
        userAgentCounts.clear();
        ipCounts.clear();
        ipEpochSeconds.clear();
        fitted = false;
    }

    private static void inc(Map<String, Integer> m, String k) { m.merge(k, 1, Integer::sum); }
    private static String norm(String s) { return s == null ? "" : s.trim(); }
    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double log2(double v) { return Math.log(v) / Math.log(2); }
    private static double round(double v) { return Math.round(v * 1000.0) / 1000.0; }

    public boolean isErrorCode(Integer code) { return code != null && ERROR_CODES.contains(code); }
}
