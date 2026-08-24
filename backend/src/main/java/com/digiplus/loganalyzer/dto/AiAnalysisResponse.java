package com.digiplus.loganalyzer.dto;

import java.time.Instant;

public record AiAnalysisResponse(String explanation, String rootCause, String nextStep,
                                 String provider, String model, Instant createdAt) { }
