package com.digiplus.loganalyzer.entity;

import com.digiplus.loganalyzer.detector.LogView;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A single persisted log record plus the results of anomaly detection.
 * Implements {@link LogView} so the detector can score it directly.
 */
@Entity
@Table(name = "log_entries", indexes = {
        @Index(name = "idx_log_anomaly", columnList = "is_anomaly"),
        @Index(name = "idx_log_timestamp", columnList = "timestamp"),
        @Index(name = "idx_log_ip", columnList = "ip_address")
})
public class LogEntry implements LogView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "request_type")
    private String requestType;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "location")
    private String location;

    /** Optional free-text message/endpoint (present in some log formats). */
    @Column(name = "message", length = 1000)
    private String message;

    // ---- Detection results (populated by our own algorithm, never by the AI) ----
    @Column(name = "is_anomaly", nullable = false)
    private boolean anomaly = false;

    @Column(name = "anomaly_score")
    private Double anomalyScore;

    @Column(name = "anomaly_reason", length = 2000)
    private String anomalyReason;

    /** Per-signal score breakdown, stored as JSON for the detail view. */
    @Column(name = "signal_breakdown", length = 4000)
    private String signalBreakdown;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToOne(mappedBy = "logEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private AiAnalysis aiAnalysis;

    public LogEntry() { }

    // ---- LogView contract ----
    @Override public LocalDateTime getTimestamp() { return timestamp; }
    @Override public String getIpAddress() { return ipAddress; }
    @Override public Integer getStatusCode() { return statusCode; }
    @Override public String getRequestType() { return requestType; }
    @Override public String getUserAgent() { return userAgent; }
    @Override public String getLocation() { return location; }

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setLocation(String location) { this.location = location; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isAnomaly() { return anomaly; }
    public void setAnomaly(boolean anomaly) { this.anomaly = anomaly; }
    public Double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(Double anomalyScore) { this.anomalyScore = anomalyScore; }
    public String getAnomalyReason() { return anomalyReason; }
    public void setAnomalyReason(String anomalyReason) { this.anomalyReason = anomalyReason; }
    public String getSignalBreakdown() { return signalBreakdown; }
    public void setSignalBreakdown(String signalBreakdown) { this.signalBreakdown = signalBreakdown; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public AiAnalysis getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(AiAnalysis aiAnalysis) { this.aiAnalysis = aiAnalysis; }
}
