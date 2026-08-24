package com.digiplus.loganalyzer.dto;

import java.util.List;

public record UploadResponse(
        int received,
        int inserted,
        int skipped,
        long totalAnomaliesInDataset,
        List<String> recognizedColumns,
        List<ValidationIssue> issues,
        String message
) { }
