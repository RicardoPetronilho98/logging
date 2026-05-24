package com.playground.logging.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEntry {

    private LocalDateTime executedAt;
    private LogPoint logPoint;
    private String transactionId; // entire end-to-end business ID
    private String traceId; // OTel trace-id from traceparent, or fallback UUID
    @Nullable
    private String spanId; // OTel parent-span-id from traceparent; null when traceparent is absent
    private long internalExecutionElapsedTimeNanos;
    private long externalExecutionElapsedTimeNanos;
    private long totalExecutionElapsedTimeNanos;
    private String method;
    private String uri;
    @Nullable
    private Map<String, List<String>> headers;
    @Nullable
    @JsonRawValue // payload will be injected as true JSON inside the log
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

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static LogPoint fromValue(String value) {
            return Arrays.stream(LogPoint.values())
                    .filter(lp -> lp.value.equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid LogPoint value: " + value));
        }
    }

}
