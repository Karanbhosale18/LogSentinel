package com.digiplus.loganalyzer.ingest;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Tolerant CSV parser. It maps a variety of column headers to canonical field
 * names (so it accepts both the provided dataset schema and the example schema
 * from the problem statement), and understands basic double-quoted fields.
 *
 * Canonical fields: timestamp, ip, requestType, status, userAgent, sessionId,
 * location, message.
 */
@Component
public class CsvLogParser {

    /** Maps normalized header tokens -> canonical field name. */
    private static final Map<String, String> SYNONYMS = new HashMap<>();
    static {
        put("timestamp", "timestamp", "time", "datetime", "date", "eventtime");
        put("ip", "ipaddress", "ip", "source", "src", "sourceip", "host", "client");
        put("requestType", "requesttype", "request", "method", "eventtype", "event", "type", "verb");
        put("status", "statuscode", "status", "code", "httpstatus", "responsecode", "severity");
        put("userAgent", "useragent", "agent", "ua", "browser");
        put("sessionId", "sessionid", "session", "sid");
        put("location", "location", "country", "geo", "region", "geolocation");
        put("message", "message", "msg", "detail", "description", "note", "path", "endpoint", "url");
    }
    private static void put(String canonical, String... tokens) {
        for (String t : tokens) SYNONYMS.put(t, canonical);
    }

    public record ParseResult(List<ParsedRow> rows, List<String> recognizedColumns, int dataLineCount) { }

    public ParseResult parse(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = readNonEmptyLine(reader);
            if (headerLine == null) {
                return new ParseResult(List.of(), List.of(), 0);
            }
            List<String> headers = splitCsv(headerLine);

            // Build index -> canonical mapping
            Map<Integer, String> colMap = new LinkedHashMap<>();
            List<String> recognized = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                String canonical = SYNONYMS.get(normalizeHeader(headers.get(i)));
                if (canonical != null && !colMap.containsValue(canonical)) {
                    colMap.put(i, canonical);
                    recognized.add(canonical);
                }
            }

            List<ParsedRow> rows = new ArrayList<>();
            String line;
            int lineNo = 1; // header was line 1
            int dataLines = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                dataLines++;
                List<String> values = splitCsv(line);
                Map<String, String> fields = new HashMap<>();
                for (var e : colMap.entrySet()) {
                    int idx = e.getKey();
                    String val = idx < values.size() ? values.get(idx).trim() : null;
                    if (val != null && !val.isEmpty()) fields.put(e.getValue(), val);
                }
                rows.add(new ParsedRow(lineNo, fields, line));
            }
            return new ParseResult(rows, recognized, dataLines);
        }
    }

    private static String readNonEmptyLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) return line;
        }
        return null;
    }

    private static String normalizeHeader(String h) {
        return h == null ? "" : h.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** Minimal RFC-4180-ish splitter: handles double-quoted fields with embedded commas/quotes. */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuotes = false;
                } else cur.append(c);
            } else {
                if (c == '"') inQuotes = true;
                else if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
                else cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
