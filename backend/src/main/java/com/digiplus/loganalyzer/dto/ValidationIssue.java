package com.digiplus.loganalyzer.dto;

public record ValidationIssue(int line, String error, String sample) { }
