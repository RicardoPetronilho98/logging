package com.playground.logging.mapper;

import com.playground.logging.config.LoggingProperties;
import com.playground.logging.domain.LogContext;
import com.playground.logging.domain.LogEntry;
import com.playground.logging.domain.LoggingHttpHeaders;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.http.MediaType;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        imports = {LoggingHttpHeaders.class, LocalDateTime.class, HashMap.class, LogEntry.LogPoint.class}
)
public interface LoggingMapper {

    // --- LogContext

    @Mapping(target = "identifiers", source = "req")
    @Mapping(target = "timers", source = "startTime")
    @Mapping(target = "requestInData", source = "req")
    @Mapping(target = "attributes", expression = "java(new HashMap<>())")
    LogContext toLogContext(ContentCachingRequestWrapper req, Long startTime, @Context LoggingProperties loggingProperties);

    @Mapping(target = "transactionId", expression = "java(LoggingHttpHeaders.getTransactionId(req))")
    @Mapping(target = "traceId", expression = "java(LoggingHttpHeaders.getTraceId(req))")
    LogContext.Identifiers toIdentifiers(ContentCachingRequestWrapper req);

    @Mapping(target = "internalExecutionStartTime", expression = "java(startTime)")
    @Mapping(target = "externalElapsedTimeNanos", expression = "java(0L)")
    LogContext.Timers toTimers(Long startTime);

    @Mapping(target = "executedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "method", expression = "java(req.getMethod())")
    @Mapping(target = "uri", expression = "java(req.getRequestURI())")
    @Mapping(target = "headers", source = "req", qualifiedByName = "getRequestHttpHeaders")
    @Mapping(target = "payload", source = "req", qualifiedByName = "getRequestPayload")
    LogContext.RequestInData toRequestInData(ContentCachingRequestWrapper req, @Context LoggingProperties loggingProperties);

    // --- LogEntry REQUEST_IN

    @Mapping(target = "executedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "logPoint", expression = "java(LogPoint.REQUEST_IN)")
    @Mapping(target = "transactionId", source = "context.identifiers.transactionId")
    @Mapping(target = "traceId", source = "context.identifiers.traceId")
    @Mapping(target = "internalExecutionElapsedTimeNanos", source = "context.timers.internalElapsedTimeNanos")
    @Mapping(target = "externalExecutionElapsedTimeNanos", source = "context.timers.externalElapsedTimeNanos")
    @Mapping(target = "totalExecutionElapsedTimeNanos", source = "context.timers.totalElapsedTimeNanos")
    @Mapping(target = "method", source = "context.requestInData.method")
    @Mapping(target = "uri", source = "context.requestInData.uri")
    @Mapping(target = "headers", source = "context.requestInData.headers")
    @Mapping(target = "payload", source = "context.requestInData.payload")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "attributes", source = "context.attributes")
    LogEntry toRequestIn(LogContext context);

    // --- LogEntry RESPONSE_OUT

    @Mapping(target = "executedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "logPoint", expression = "java(LogPoint.RESPONSE_OUT)")
    @Mapping(target = "transactionId", source = "context.identifiers.transactionId")
    @Mapping(target = "traceId", source = "context.identifiers.traceId")
    @Mapping(target = "internalExecutionElapsedTimeNanos", source = "context.timers.internalElapsedTimeNanos")
    @Mapping(target = "externalExecutionElapsedTimeNanos", source = "context.timers.externalElapsedTimeNanos")
    @Mapping(target = "totalExecutionElapsedTimeNanos", source = "context.timers.totalElapsedTimeNanos")
    @Mapping(target = "method", source = "context.requestInData.method")
    @Mapping(target = "uri", source = "context.requestInData.uri")
    @Mapping(target = "headers", source = "res", qualifiedByName = "getResponseHttpHeaders")
    @Mapping(target = "payload", source = "res", qualifiedByName = "getResponsePayload")
    @Mapping(target = "status", source = "res.status")
    @Mapping(target = "attributes", source = "context.attributes")
    LogEntry toResponseOut(LogContext context, ContentCachingResponseWrapper res, @Context LoggingProperties loggingProperties);

    // ---

    @Named("getRequestHttpHeaders")
    default Map<String, List<String>> getResponseHttpHeaders(ContentCachingRequestWrapper req) {
        return Collections.list(req.getHeaderNames())
                .stream()
                .collect(
                        Collectors.toMap(
                                Function.identity(),
                                header -> Collections.list(req.getHeaders(header))
                        )
                );
    }

    @Named("getRequestPayload")
    default String getRequestPayload(ContentCachingRequestWrapper req, @Context LoggingProperties loggingProperties) {
        String payload = MediaType.APPLICATION_JSON_VALUE.equals(req.getContentType())
                ? new String(req.getContentAsByteArray(), StandardCharsets.UTF_8)
                : null;
        return truncatePayload(payload, loggingProperties);
    }

    @Named("getResponseHttpHeaders")
    default Map<String, List<String>> getResponseHttpHeaders(ContentCachingResponseWrapper res) {
        return res.getHeaderNames()
                .stream()
                .collect(
                        Collectors.toMap(
                                Function.identity(),
                                header -> new ArrayList<>(res.getHeaders(header))
                        )
                );
    }

    @Named("getResponsePayload")
    default String getResponsePayload(ContentCachingResponseWrapper res, @Context LoggingProperties loggingProperties) {
        String payload = MediaType.APPLICATION_JSON_VALUE.equals(res.getContentType())
                ? new String(res.getContentAsByteArray(), StandardCharsets.UTF_8)
                : null;
        return truncatePayload(payload, loggingProperties);
    }

    @Named("truncatePayload")
    default String truncatePayload(String payload, @Context LoggingProperties loggingProperties) {
        if (payload == null) {
            return null;
        }
        return payload.length() > loggingProperties.getPayload().getMaxLength()
                ? payload.substring(0, loggingProperties.getPayload().getMaxLength()) + "...[TRUNCATED]"
                : payload;
    }

}
