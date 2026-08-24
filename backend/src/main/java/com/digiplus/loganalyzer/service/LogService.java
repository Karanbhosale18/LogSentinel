package com.digiplus.loganalyzer.service;

import com.digiplus.loganalyzer.ai.AiExplanation;
import com.digiplus.loganalyzer.ai.AiExplanationService;
import com.digiplus.loganalyzer.dto.*;
import com.digiplus.loganalyzer.entity.AiAnalysis;
import com.digiplus.loganalyzer.entity.LogEntry;
import com.digiplus.loganalyzer.exception.NotFoundException;
import com.digiplus.loganalyzer.repository.AiAnalysisRepository;
import com.digiplus.loganalyzer.repository.LogEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read/query operations, statistics, on-demand AI analysis, and deletion. */
@Service
public class LogService {

    private final LogEntryRepository logRepository;
    private final AiAnalysisRepository aiRepository;
    private final AiExplanationService aiExplanationService;
    private final LogMapper mapper;

    public LogService(LogEntryRepository logRepository, AiAnalysisRepository aiRepository,
                      AiExplanationService aiExplanationService, LogMapper mapper) {
        this.logRepository = logRepository;
        this.aiRepository = aiRepository;
        this.aiExplanationService = aiExplanationService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<LogResponse> search(Boolean anomaly, Integer status, String q,
                                            int page, int size, String sort, String dir) {
        String query = (q == null || q.isBlank()) ? null : q.trim().toLowerCase();
        Sort sortSpec = buildSort(sort, dir);
        Page<LogEntry> result = logRepository.search(anomaly, status, query,
                PageRequest.of(page, size, sortSpec));
        return PageResponse.of(result, mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public LogResponse getById(Long id) {
        LogEntry e = logRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Log entry " + id + " not found"));
        return mapper.toResponse(e);
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        long total = logRepository.count();
        long anomalies = logRepository.countByAnomalyTrue();
        double rate = total == 0 ? 0.0 : Math.round((anomalies * 10000.0 / total)) / 100.0;
        Map<String, Long> dist = new LinkedHashMap<>();
        for (Object[] row : logRepository.statusDistribution()) {
            dist.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return new StatsResponse(total, anomalies, rate,
                logRepository.countServerErrors(), logRepository.countClientErrors(),
                logRepository.countDistinctIps(), dist);
    }

    /**
     * Generate (or regenerate) the AI explanation for a flagged entry and persist it.
     * The AI only explains an entry our detector already flagged.
     */
    @Transactional
    public AiAnalysisResponse analyze(Long id) {
        LogEntry entry = logRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Log entry " + id + " not found"));

        AiExplanation ex = aiExplanationService.explain(entry);

        AiAnalysis analysis = aiRepository.findByLogEntryId(id).orElseGet(AiAnalysis::new);
        analysis.setLogEntry(entry);
        analysis.setExplanation(ex.explanation());
        analysis.setRootCause(ex.rootCause());
        analysis.setNextStep(ex.nextStep());
        analysis.setProvider(ex.provider());
        analysis.setModel(ex.model());
        aiRepository.save(analysis);

        return new AiAnalysisResponse(ex.explanation(), ex.rootCause(), ex.nextStep(),
                ex.provider(), ex.model(), analysis.getCreatedAt());
    }

    @Transactional
    public long deleteAll() {
        long count = logRepository.count();
        logRepository.deleteAll();
        return count;
    }

    private static Sort buildSort(String sort, String dir) {
        String field = switch (sort == null ? "" : sort.toLowerCase()) {
            case "score" -> "anomalyScore";
            case "status" -> "statusCode";
            case "ip" -> "ipAddress";
            case "timestamp", "time" -> "timestamp";
            default -> "timestamp";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Secondary sort by id for stable ordering
        return Sort.by(new Sort.Order(direction, field), Sort.Order.asc("id"));
    }
}
