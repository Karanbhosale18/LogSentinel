package com.digiplus.loganalyzer.dto;

import java.util.Map;

public record StatsResponse(
        long totalLogs,
        long anomalies,
        double anomalyRate,
        long serverErrors,
        long clientErrors,
        long distinctIps,
        Map<String, Long> statusDistribution
) { }
