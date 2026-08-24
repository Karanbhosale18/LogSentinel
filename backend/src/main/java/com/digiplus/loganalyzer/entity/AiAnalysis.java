package com.digiplus.loganalyzer.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * AI-generated, plain-English explanation for a flagged log entry.
 * Produced only for entries our detector has already flagged as anomalous.
 */
@Entity
@Table(name = "ai_analysis")
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_entry_id", nullable = false, unique = true)
    private LogEntry logEntry;

    @Column(name = "explanation", length = 4000)
    private String explanation;

    @Column(name = "root_cause", length = 4000)
    private String rootCause;

    @Column(name = "next_step", length = 4000)
    private String nextStep;

    /** "openai" or "offline" — lets the UI show whether a real LLM was used. */
    @Column(name = "provider")
    private String provider;

    @Column(name = "model")
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AiAnalysis() { }

    public Long getId() { return id; }
    public LogEntry getLogEntry() { return logEntry; }
    public void setLogEntry(LogEntry logEntry) { this.logEntry = logEntry; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getNextStep() { return nextStep; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
