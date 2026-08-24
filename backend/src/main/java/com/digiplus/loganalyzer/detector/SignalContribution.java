package com.digiplus.loganalyzer.detector;

/**
 * One signal's contribution to the overall anomaly score.
 *
 * @param name          machine name of the signal (e.g. "rarity")
 * @param label         human label (e.g. "Rare value")
 * @param rawScore      the signal's own 0..1 assessment
 * @param weight        the weight applied to this signal
 * @param weightedScore rawScore * weight (what actually feeds the total)
 * @param detail        human-readable explanation of why this signal fired
 */
public record SignalContribution(
        String name,
        String label,
        double rawScore,
        double weight,
        double weightedScore,
        String detail
) { }
