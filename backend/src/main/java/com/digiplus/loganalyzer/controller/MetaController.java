package com.digiplus.loganalyzer.controller;

import com.digiplus.loganalyzer.ai.OpenAiClient;
import com.digiplus.loganalyzer.config.AiProperties;
import com.digiplus.loganalyzer.detector.DetectorConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Small metadata endpoint so the UI can show AI status and the detector threshold. */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final OpenAiClient openAiClient;
    private final AiProperties aiProperties;
    private final DetectorConfig detectorConfig;

    public MetaController(OpenAiClient openAiClient, AiProperties aiProperties, DetectorConfig detectorConfig) {
        this.openAiClient = openAiClient;
        this.aiProperties = aiProperties;
        this.detectorConfig = detectorConfig;
    }

    @GetMapping
    public Map<String, Object> meta() {
        boolean live = openAiClient.isConfigured();
        String provider = aiProperties.getProvider();
        return Map.of(
                "aiProvider", live ? provider : "offline",
                "aiLive", live,
                "aiModel", live ? openAiClient.model() : "rule-based-fallback",
                "detectorThreshold", detectorConfig.threshold
        );
    }
}

