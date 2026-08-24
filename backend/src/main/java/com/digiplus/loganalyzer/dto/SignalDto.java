package com.digiplus.loganalyzer.dto;

public record SignalDto(String name, String label, double rawScore,
                        double weight, double weightedScore, String detail) { }
