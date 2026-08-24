package com.digiplus.loganalyzer.detector;

import java.time.LocalDateTime;

/**
 * Minimal read-only view of a log entry that the {@link AnomalyDetector} needs.
 * The detector depends on this interface only, not on JPA/Spring, so the detection
 * logic is pure, unit-testable, and reusable outside the web app.
 */
public interface LogView {
    LocalDateTime getTimestamp();
    String getIpAddress();
    Integer getStatusCode();
    String getRequestType();
    String getUserAgent();
    String getLocation();
}
