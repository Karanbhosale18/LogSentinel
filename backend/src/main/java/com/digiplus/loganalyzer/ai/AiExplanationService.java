package com.digiplus.loganalyzer.ai;

import com.digiplus.loganalyzer.entity.LogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns an already-flagged log entry into a plain-English explanation, likely
 * root cause, and recommended next step.
 *
 * <p><b>Boundary with detection:</b> the AI is invoked strictly AFTER our own
 * algorithm has decided an entry is anomalous. The prompt explicitly tells the
 * model not to re-judge whether the entry is anomalous — it only explains the
 * finding. If no API key is configured, we fall back to {@link OfflineExplainer}
 * so the application still runs end-to-end.
 */
@Service
public class AiExplanationService {

    private static final Logger log = LoggerFactory.getLogger(AiExplanationService.class);

    private static final String SYSTEM_PROMPT = """
            You are a site-reliability / security analyst assistant. You are given a single log
            entry that a SEPARATE detection algorithm has ALREADY flagged as anomalous, together
            with the score and the exact signals that fired. Do NOT re-decide whether it is
            anomalous and do NOT invent facts beyond the provided fields. Explain the finding for
            an on-call engineer.

            Respond with a STRICT JSON object and nothing else, using exactly these keys:
              "explanation": one or two sentences, plain English, describing what happened.
              "root_cause":  the single most likely cause given the signals.
              "next_step":   one concrete, actionable recommendation.
            Keep each value concise (<= 2 sentences).
            """;

    private final OpenAiClient openAiClient;
    private final OfflineExplainer offlineExplainer;
    private final ObjectMapper mapper;

    public AiExplanationService(OpenAiClient openAiClient, OfflineExplainer offlineExplainer, ObjectMapper mapper) {
        this.openAiClient = openAiClient;
        this.offlineExplainer = offlineExplainer;
        this.mapper = mapper;
    }

    public AiExplanation explain(LogEntry entry) {
        if (openAiClient.isConfigured()) {
            try {
                String content = openAiClient.chatJson(SYSTEM_PROMPT, buildUserPrompt(entry));
                JsonNode json = mapper.readTree(content);
                String explanation = text(json, "explanation");
                String rootCause = text(json, "root_cause");
                String nextStep = text(json, "next_step");
                if (!explanation.isBlank()) {
                    return new AiExplanation(explanation, rootCause, nextStep, "openai", openAiClient.model());
                }
                log.warn("OpenAI returned empty explanation; using offline fallback for log {}", entry.getId());
            } catch (Exception e) {
                log.warn("OpenAI call failed ({}); using offline fallback for log {}", e.getMessage(), entry.getId());
            }
        }
        return offlineExplainer.explain(entry);
    }

    private static String buildUserPrompt(LogEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Flagged log entry:\n");
        sb.append("- timestamp: ").append(e.getTimestamp()).append('\n');
        sb.append("- source_ip: ").append(nvl(e.getIpAddress())).append('\n');
        sb.append("- request_type: ").append(nvl(e.getRequestType())).append('\n');
        sb.append("- status_code: ").append(e.getStatusCode() == null ? "n/a" : e.getStatusCode()).append('\n');
        sb.append("- user_agent: ").append(nvl(e.getUserAgent())).append('\n');
        sb.append("- location: ").append(nvl(e.getLocation())).append('\n');
        if (e.getMessage() != null) sb.append("- message: ").append(e.getMessage()).append('\n');
        sb.append("\nDetector output (authoritative — do not override):\n");
        sb.append("- anomaly_score: ").append(e.getAnomalyScore()).append('\n');
        sb.append("- reason: ").append(nvl(e.getAnomalyReason())).append('\n');
        if (e.getSignalBreakdown() != null) {
            sb.append("- signals_json: ").append(e.getSignalBreakdown()).append('\n');
        }
        return sb.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText().trim();
    }

    private static String nvl(String s) { return s == null ? "n/a" : s; }
}
