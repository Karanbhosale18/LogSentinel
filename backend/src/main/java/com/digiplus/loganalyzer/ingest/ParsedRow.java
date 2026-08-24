package com.digiplus.loganalyzer.ingest;

import java.util.Map;

/** A raw CSV data row mapped to canonical field names, with its source line number. */
public record ParsedRow(int lineNumber, Map<String, String> fields, String rawLine) {
    public String get(String canonical) { return fields.get(canonical); }
}
