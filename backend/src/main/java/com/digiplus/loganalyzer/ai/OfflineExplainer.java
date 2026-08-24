package com.digiplus.loganalyzer.ai;

import com.digiplus.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Component;

/**
 * Deterministic fallback used ONLY when no LLM is configured, so the app remains
 * fully runnable without an API key. It composes a short explanation from the
 * detector's own signals. This is intentionally a fallback: the primary,
 * spec-intended path is the real LLM in {@link OpenAiClient}. The UI labels the
 * provider ("offline" vs "openai") so it is always clear which was used.
 */
@Component
public class OfflineExplainer {

    public AiExplanation explain(LogEntry log) {
        String reason = log.getAnomalyReason() == null ? "combined anomaly signals" : log.getAnomalyReason();
        String where = describeTarget(log);

        String explanation = String.format(
                "This %s request from %s (status %s) was flagged with an anomaly score of %.2f. %s.",
                orUnknown(log.getRequestType()), orUnknown(log.getIpAddress()),
                log.getStatusCode() == null ? "n/a" : log.getStatusCode().toString(),
                log.getAnomalyScore() == null ? 0.0 : log.getAnomalyScore(),
                capitalize(reason));

        String rootCause;
        String nextStep;
        String r = reason.toLowerCase();
        if (r.contains("source ip") || r.contains("volume") || r.contains("burst")) {
            rootCause = "A single source (" + orUnknown(log.getIpAddress())
                    + ") is generating far more traffic than any other, which is consistent with automated scanning, a misbehaving client, or a scripted attack.";
            nextStep = "Inspect this IP's full request timeline, apply rate limiting, and block or challenge it if the pattern continues.";
        } else if (r.contains("rare")) {
            rootCause = "The request carries a value (" + where
                    + ") that almost never appears in normal traffic, suggesting an unusual or possibly spoofed origin.";
            nextStep = "Confirm whether traffic from this origin is expected; if not, add it to a watchlist and review related sessions.";
        } else if (log.getStatusCode() != null && log.getStatusCode() >= 500) {
            rootCause = "The server returned a 5xx error, indicating a backend failure or an unhealthy dependency for this endpoint.";
            nextStep = "Check the service and its downstream dependencies (database, upstream APIs) around this timestamp.";
        } else {
            rootCause = "The entry deviates from the learned baseline across one or more signals.";
            nextStep = "Review the surrounding logs for this source and endpoint to confirm impact.";
        }
        return new AiExplanation(explanation, rootCause, nextStep, "offline", "rule-based-fallback");
    }

    private static String describeTarget(LogEntry log) {
        if (log.getLocation() != null) return "location " + log.getLocation();
        if (log.getUserAgent() != null) return "user agent " + log.getUserAgent();
        return "an unusual attribute";
    }

    private static String orUnknown(String s) { return s == null || s.isBlank() ? "unknown" : s; }
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
