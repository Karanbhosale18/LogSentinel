package com.digiplus.loganalyzer.ingest;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CsvLogParserTest {

    private final CsvLogParser parser = new CsvLogParser();

    @Test
    void parsesProvidedSchema() throws Exception {
        String csv = "Timestamp,IP_Address,Request_Type,Status_Code,User_Agent,Session_ID,Location\n"
                + "2023-01-01 00:00:00,202.118.116.11,GET,403,Edge,4835,Brazil\n";
        var result = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, result.dataLineCount());
        var row = result.rows().get(0);
        assertEquals("202.118.116.11", row.get("ip"));
        assertEquals("403", row.get("status"));
        assertEquals("Brazil", row.get("location"));
    }

    @Test
    void parsesExampleSchemaSynonyms() throws Exception {
        String csv = "timestamp,IP Address,Status,Message\n"
                + "2026-08-20 09:15:10,10.0.0.55,500,POST /api/payment — internal server error\n";
        var result = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        var row = result.rows().get(0);
        assertEquals("10.0.0.55", row.get("ip"));
        assertEquals("500", row.get("status"));
        assertTrue(row.get("message").contains("payment"));
    }

    @Test
    void emptyFileYieldsNoRows() throws Exception {
        var result = parser.parse(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, result.dataLineCount());
    }

    @Test
    void handlesQuotedFieldWithComma() {
        var parts = CsvLogParser.splitCsv("a,\"b,c\",d");
        assertEquals(3, parts.size());
        assertEquals("b,c", parts.get(1));
    }
}
