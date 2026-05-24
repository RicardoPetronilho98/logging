package com.playground.logging.domain;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Parsed representation of the W3C traceparent header.
 *
 * <p>Format: {@code <version>-<traceId>-<parentId>-<traceFlags>}
 * <p>Example: {@code 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}
 *
 * @param version     2-hex-char format version. Currently always {@code "00"}. A value of
 *                    {@code "ff"} is invalid per the spec and will cause {@link #from} to
 *                    return {@code null}.
 * @param traceId     32-hex-char (128-bit) identifier shared by all spans in a distributed
 *                    trace. The same value propagates across every service in the call chain,
 *                    making it the primary key for log-to-trace correlation.
 * @param parentId    16-hex-char (64-bit) identifier of the span that sent this request —
 *                    i.e., the calling service's active span at the moment it made the call.
 *                    Also referred to as "span-id" of the upstream hop.
 * @param traceFlags  2-hex-char sampling and control flags. The only currently defined flag
 *                    is the least-significant bit: {@code 01} = sampled (record this trace),
 *                    {@code 00} = not sampled (may be dropped by the tracing backend).
 */
public record TraceContext(
        String version,
        String traceId,
        String parentId,
        String traceFlags
) {

    private static final int EXPECTED_SEGMENT_COUNT = 4;
    private static final int VERSION_INDEX     = 0;
    private static final int TRACE_ID_INDEX    = 1;
    private static final int PARENT_ID_INDEX   = 2;
    private static final int TRACE_FLAGS_INDEX = 3;

    private static final int VERSION_LENGTH     = 2;
    private static final int TRACE_ID_LENGTH    = 32;
    private static final int PARENT_ID_LENGTH   = 16;
    private static final int TRACE_FLAGS_LENGTH = 2;
    private static final String INVALID_VERSION = "ff";
    private static final Pattern HEX_PATTERN    = Pattern.compile("^[0-9a-f]+$");

    @Nullable
    public static TraceContext from(HttpServletRequest req) {
        String header = req.getHeader(LoggingHttpHeaders.TRACEPARENT.getValue());
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String[] parts = header.split("-");
        if (parts.length != EXPECTED_SEGMENT_COUNT
                || parts[VERSION_INDEX].length()     != VERSION_LENGTH
                || parts[TRACE_ID_INDEX].length()    != TRACE_ID_LENGTH
                || parts[PARENT_ID_INDEX].length()   != PARENT_ID_LENGTH
                || parts[TRACE_FLAGS_INDEX].length() != TRACE_FLAGS_LENGTH
                || INVALID_VERSION.equals(parts[VERSION_INDEX])
                || !HEX_PATTERN.matcher(parts[TRACE_ID_INDEX]).matches()
                || !HEX_PATTERN.matcher(parts[PARENT_ID_INDEX]).matches()) {
            return null;
        }
        return new TraceContext(
                parts[VERSION_INDEX],
                parts[TRACE_ID_INDEX],
                parts[PARENT_ID_INDEX],
                parts[TRACE_FLAGS_INDEX]
        );
    }

}
