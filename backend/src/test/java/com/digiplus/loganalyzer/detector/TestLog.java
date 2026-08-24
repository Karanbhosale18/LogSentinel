package com.digiplus.loganalyzer.detector;

import java.time.LocalDateTime;

/** Minimal LogView implementation for unit tests. */
record TestLog(LocalDateTime timestamp, String ipAddress, Integer statusCode,
               String requestType, String userAgent, String location) implements LogView {
    @Override public LocalDateTime getTimestamp() { return timestamp; }
    @Override public String getIpAddress() { return ipAddress; }
    @Override public Integer getStatusCode() { return statusCode; }
    @Override public String getRequestType() { return requestType; }
    @Override public String getUserAgent() { return userAgent; }
    @Override public String getLocation() { return location; }
}
