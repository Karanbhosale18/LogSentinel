package com.digiplus.loganalyzer.service;

import com.digiplus.loganalyzer.dto.AiAnalysisResponse;
import com.digiplus.loganalyzer.dto.LogResponse;
import com.digiplus.loganalyzer.dto.SignalDto;
import com.digiplus.loganalyzer.entity.AiAnalysis;
import com.digiplus.loganalyzer.entity.LogEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps JPA entities to API DTOs (including parsing the stored signal JSON). */
@Component
public class LogMapper {

    private final ObjectMapper mapper;

    public LogMapper(ObjectMapper mapper) { this.mapper = mapper; }

    public LogResponse toResponse(LogEntry e) {
        List<SignalDto> signals = parseSignals(e.getSignalBreakdown());
        AiAnalysis ai = e.getAiAnalysis();
        AiAnalysisResponse aiDto = ai == null ? null : new AiAnalysisResponse(
                ai.getExplanation(), ai.getRootCause(), ai.getNextStep(),
                ai.getProvider(), ai.getModel(), ai.getCreatedAt());

        return new LogResponse(
                e.getId(), e.getTimestamp(), e.getIpAddress(), e.getRequestType(),
                e.getStatusCode(), e.getUserAgent(), e.getSessionId(), e.getLocation(),
                e.getMessage(), e.isAnomaly(), e.getAnomalyScore(), e.getAnomalyReason(),
                signals, ai != null, aiDto);
    }

    private List<SignalDto> parseSignals(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<List<SignalDto>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }
}
