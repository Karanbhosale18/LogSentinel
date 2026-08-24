package com.digiplus.loganalyzer.ai;

/** Structured result of an AI (or offline-fallback) explanation. */
public record AiExplanation(String explanation, String rootCause, String nextStep,
                            String provider, String model) { }
