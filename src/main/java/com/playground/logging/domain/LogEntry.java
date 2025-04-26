package com.playground.logging.domain;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class LogEntry {

    private LocalDateTime executedAt;
    private LogPoint logPoint;
    private String transactionId; // entire end-to-end ID
    private String traceId; // a per-call ID to distinguish individual parallel or downstream executions
    private long internalExecutionElapsedTimeNanos;
    private long externalExecutionElapsedTimeNanos;
    private long totalExecutionElapsedTimeNanos;
    private String method;
    private String uri;
    @Nullable
    private Map<String, List<String>> headers;
    @Nullable
    private String payload;
    @Nullable
    private Integer status;
    private Map<String, String> attributes; // allow dynamic attributes


    @Getter
    @ToString
    @RequiredArgsConstructor
    public enum LogPoint {
        REQUEST_IN("request-in"),
        REQUEST_OUT("request-out"),
        RESPONSE_IN("response-in"),
        RESPONSE_OUT("response-out");

        private final String value;
    }

}
