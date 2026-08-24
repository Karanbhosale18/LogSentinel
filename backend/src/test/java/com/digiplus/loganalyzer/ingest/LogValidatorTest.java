package com.digiplus.loganalyzer.ingest;

import com.digiplus.loganalyzer.entity.LogEntry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogValidatorTest {

    private final LogValidator validator = new LogValidator();

    private ParsedRow row(Map<String, String> fields) {
        return new ParsedRow(2, fields, "raw");
    }

    @Test
    void acceptsWellFormedRow() {
        LogValidator.Result r = validator.validate(row(Map.of(
                "timestamp", "2023-01-01 00:00:00",
                "ip", "202.118.116.11",
                "requestType", "GET",
                "status", "403",
                "userAgent", "Edge",
                "location", "Brazil")));
        assertTrue(r.valid());
        LogEntry e = r.entry();
        assertEquals(403, e.getStatusCode());
        assertEquals("Brazil", e.getLocation());
        assertNotNull(e.getTimestamp());
    }

    @Test
    void rejectsMissingTimestamp() {
        LogValidator.Result r = validator.validate(row(Map.of("ip", "1.1.1.1", "status", "200")));
        assertFalse(r.valid());
        assertTrue(r.error().toLowerCase().contains("timestamp"));
    }

    @Test
    void rejectsMalformedTimestamp() {
        LogValidator.Result r = validator.validate(row(Map.of(
                "timestamp", "not-a-date", "ip", "1.1.1.1", "status", "200")));
        assertFalse(r.valid());
        assertTrue(r.error().toLowerCase().contains("malformed"));
    }

    @Test
    void rejectsMalformedStatus() {
        LogValidator.Result r = validator.validate(row(Map.of(
                "timestamp", "2023-01-01 00:00:00", "ip", "1.1.1.1", "status", "abc")));
        assertFalse(r.valid());
        assertTrue(r.error().toLowerCase().contains("status"));
    }

    @Test
    void mapsSeverityWordsToCodes() {
        LogValidator.Result r = validator.validate(row(Map.of(
                "timestamp", "2023-01-01 00:00:00", "ip", "1.1.1.1", "status", "ERROR")));
        assertTrue(r.valid());
        assertEquals(500, r.entry().getStatusCode());
    }

    @Test
    void parsesIsoTimestamp() {
        LogValidator.Result r = validator.validate(row(Map.of(
                "timestamp", "2026-08-20T09:14:02", "ip", "192.168.1.14", "status", "200")));
        assertTrue(r.valid());
    }
}
