package com.digiplus.loganalyzer.detector;

import java.util.List;

/**
 * Outcome of scoring a single log entry.
 *
 * @param anomaly whether the entry is flagged (score >= threshold)
 * @param score   combined anomaly score in [0,1]
 * @param reason  short human-readable reason built from the firing signals
 * @param signals per-signal breakdown (all signals, including non-firing ones)
 */
public record DetectionResult(
        boolean anomaly,
        double score,
        String reason,
        List<SignalContribution> signals
) { }
