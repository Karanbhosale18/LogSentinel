package com.digiplus.loganalyzer.config;

import com.digiplus.loganalyzer.detector.DetectorConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds {@code app.detector.*} from application.yml into a {@link DetectorConfig}. */
@Component
@ConfigurationProperties(prefix = "app.detector")
public class DetectorProperties {

    private double threshold = 0.70;
    private double weightRarity = 0.85;
    private double weightSource = 0.85;
    private double weightSeverity = 0.30;
    private double weightBurst = 0.45;
    private double rarityCommonProb = 0.05;
    private double rarityRareProb = 0.001;
    private double volumeSigmaLow = 3.0;
    private double volumeSigmaHigh = 10.0;
    private long burstWindowSeconds = 600;
    private int burstMinEvents = 3;

    /** Build the framework-free config object consumed by the detector. */
    public DetectorConfig toConfig() {
        DetectorConfig c = new DetectorConfig();
        c.threshold = threshold;
        c.weightRarity = weightRarity;
        c.weightSource = weightSource;
        c.weightSeverity = weightSeverity;
        c.weightBurst = weightBurst;
        c.rarityCommonProb = rarityCommonProb;
        c.rarityRareProb = rarityRareProb;
        c.volumeSigmaLow = volumeSigmaLow;
        c.volumeSigmaHigh = volumeSigmaHigh;
        c.burstWindowSeconds = burstWindowSeconds;
        c.burstMinEvents = burstMinEvents;
        return c;
    }

    public void setThreshold(double v) { this.threshold = v; }
    public void setWeightRarity(double v) { this.weightRarity = v; }
    public void setWeightSource(double v) { this.weightSource = v; }
    public void setWeightSeverity(double v) { this.weightSeverity = v; }
    public void setWeightBurst(double v) { this.weightBurst = v; }
    public void setRarityCommonProb(double v) { this.rarityCommonProb = v; }
    public void setRarityRareProb(double v) { this.rarityRareProb = v; }
    public void setVolumeSigmaLow(double v) { this.volumeSigmaLow = v; }
    public void setVolumeSigmaHigh(double v) { this.volumeSigmaHigh = v; }
    public void setBurstWindowSeconds(long v) { this.burstWindowSeconds = v; }
    public void setBurstMinEvents(int v) { this.burstMinEvents = v; }
}
