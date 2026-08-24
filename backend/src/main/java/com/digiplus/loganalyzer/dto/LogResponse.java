package com.digiplus.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LogResponse(
        Long id,
        LocalDateTime timestamp,
        String ipAddress,
        String requestType,
        Integer statusCode,
        String userAgent,
        String sessionId,
        String location,
        String message,
        boolean anomaly,
        Double anomalyScore,
        String anomalyReason,
        List<SignalDto> signals,
        boolean analyzed,
        AiAnalysisResponse aiAnalysis
) { }
