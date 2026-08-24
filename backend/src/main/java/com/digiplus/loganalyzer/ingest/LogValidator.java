package com.digiplus.loganalyzer.ingest;

import com.digiplus.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Validates a {@link ParsedRow} and, when valid, builds a {@link LogEntry}.
 * Enforces the basic validation rules called for by the problem statement:
 * missing timestamps and malformed entries are rejected with a clear reason.
 */
@Component
public class LogValidator {

    /** Result of validating one row: either a built entry, or a human-readable error. */
    public record Result(boolean valid, LogEntry entry, String error) {
        static Result ok(LogEntry e) { return new Result(true, e, null); }
        static Result fail(String msg) { return new Result(false, null, msg); }
    }

    private static final List<DateTimeFormatter> TS_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME
    );

    public Result validate(ParsedRow row) {
        String tsRaw = row.get("timestamp");
        if (isBlank(tsRaw)) {
            return Result.fail("Missing timestamp");
        }
        LocalDateTime ts = parseTimestamp(tsRaw);
        if (ts == null) {
            return Result.fail("Malformed timestamp: \"" + tsRaw + "\"");
        }

        // A usable log needs at least a source or an event/status to reason about.
        String ip = row.get("ip");
        String status = row.get("status");
        String requestType = row.get("requestType");
        if (isBlank(ip) && isBlank(status) && isBlank(requestType)) {
            return Result.fail("Malformed entry: no source, status, or request type present");
        }

        Integer statusCode = null;
        if (!isBlank(status)) {
            statusCode = parseStatus(status);
            if (statusCode == null) {
                return Result.fail("Malformed status code: \"" + status + "\"");
            }
        }

        LogEntry e = new LogEntry();
        e.setTimestamp(ts);
        e.setIpAddress(nullIfBlank(ip));
        e.setRequestType(nullIfBlank(requestType));
        e.setStatusCode(statusCode);
        e.setUserAgent(nullIfBlank(row.get("userAgent")));
        e.setSessionId(nullIfBlank(row.get("sessionId")));
        e.setLocation(nullIfBlank(row.get("location")));
        e.setMessage(nullIfBlank(row.get("message")));
        return Result.ok(e);
    }

    private static LocalDateTime parseTimestamp(String raw) {
        String s = raw.trim();
        for (DateTimeFormatter f : TS_FORMATS) {
            try { return LocalDateTime.parse(s, f); } catch (Exception ignored) { }
        }
        // last resort: date-only
        try {
            return java.time.LocalDate.parse(s).atStartOfDay();
        } catch (Exception ignored) { }
        return null;
    }

    /** Accepts a plain HTTP code ("500") or a severity word mapped to a pseudo-code. */
    private static Integer parseStatus(String raw) {
        String s = raw.trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) { }
        return switch (s.toUpperCase()) {
            case "INFO", "OK", "SUCCESS" -> 200;
            case "WARN", "WARNING" -> 400;
            case "ERROR", "CRITICAL", "FATAL" -> 500;
            default -> null;
        };
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String nullIfBlank(String s) { return isBlank(s) ? null : s.trim(); }
}
