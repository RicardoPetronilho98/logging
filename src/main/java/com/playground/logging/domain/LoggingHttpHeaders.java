package com.playground.logging.domain;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Getter
@ToString
@RequiredArgsConstructor
public enum LoggingHttpHeaders {
    TRANSACTION_ID("transaction-id"),
    TRACE_ID("trace-id"),
    TRACEPARENT("traceparent");

    private final String value;

    public static String getTransactionId(HttpServletRequest req) {
        return getHeader(req, TRANSACTION_ID);
    }

    public static String getTraceId(HttpServletRequest req) {
        TraceContext ctx = TraceContext.from(req);
        return ctx != null
                ? ctx.traceId()
                : getHeader(req, TRACE_ID);
    }

    // Returns the parent-span-id from traceparent, or null if traceparent is absent/malformed.
    public static String getSpanId(HttpServletRequest req) {
        TraceContext ctx = TraceContext.from(req);
        return ctx != null
                ? ctx.parentId()
                : null;
    }

    private static String getHeader(HttpServletRequest req, LoggingHttpHeaders header) {
        return Optional.ofNullable(req.getHeader(header.getValue()))
                .filter(StringUtils::hasText)
                .orElse(UUID.randomUUID().toString());
    }

}
