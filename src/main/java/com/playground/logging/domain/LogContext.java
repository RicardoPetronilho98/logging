package com.playground.logging.domain;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class LogContext {

    private Identifiers identifiers;
    private Timers timers;
    private RequestInData requestInData;
    private Map<String, String> attributes; // allow dynamic attributes

    @Data
    @Builder
    public static class Identifiers {
        private String transactionId; // entire end-to-end ID
        private String traceId; // a per-call ID to distinguish individual parallel or downstream executions
    }

    @Data
    @Builder
    public static class Timers {
        private long internalExecutionStartTime;
        private long externalElapsedTimeNanos;

        public long getInternalElapsedTimeNanos() {
            return System.nanoTime() - internalExecutionStartTime;
        }

        public long getTotalElapsedTimeNanos() {
            return getInternalElapsedTimeNanos() + externalElapsedTimeNanos;
        }
    }

    @Data
    @Builder
    public static class RequestInData {
        private LocalDateTime executedAt;
        private String method;
        private String uri;
        @Nullable
        private Map<String, List<String>> headers;
        @Nullable
        private String payload;
    }

}
