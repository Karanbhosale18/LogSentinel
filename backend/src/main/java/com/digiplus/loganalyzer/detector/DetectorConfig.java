package com.digiplus.loganalyzer.detector;

/**
 * Plain-old-Java configuration for {@link AnomalyDetector}. Kept framework-free
 * (no Spring annotations) so the detector can be constructed and unit-tested in
 * isolation. Defaults match src/main/resources/application.yml.
 */
public class DetectorConfig {

    public double threshold = 0.70;

    // Signal weights. Rarity and source-volume are strong enough that either can
    // independently push an entry over the threshold; severity is deliberately
    // small so that common HTTP errors are NOT flagged on their own.
    public double weightRarity = 0.85;
    public double weightSource = 0.85;
    public double weightSeverity = 0.30;
    public double weightBurst = 0.45;

    // Rarity is measured as normalized "surprisal" (-log2 p) of a categorical value.
    public double rarityCommonProb = 0.05;   // p >= this  -> not rare (score 0)
    public double rarityRareProb = 0.001;     // p <= this  -> maximally rare (score 1)

    // Source-volume score rises between (mean + low*sigma) and (mean + high*sigma).
    public double volumeSigmaLow = 3.0;
    public double volumeSigmaHigh = 10.0;

    // Burst detection: many events from the same source inside a short window.
    public long burstWindowSeconds = 600;     // 10 minutes
    public int burstMinEvents = 3;

    public DetectorConfig() { }
}
