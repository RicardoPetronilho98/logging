package com.playground.logging.interceptor;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playground.logging.config.LoggingProperties;
import com.playground.logging.domain.LogContext;
import com.playground.logging.domain.LogContextHolder;
import com.playground.logging.domain.LogEntry;
import com.playground.logging.domain.LoggingHttpHeaders;
import com.playground.logging.mapper.LoggingMapper;
import com.playground.logging.redaction.LogRedactService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class RequestInAndResponseOutEnabler extends OncePerRequestFilter {

    private final LoggingMapper mapper;
    private final ObjectMapper jsonMapper;
    private final LoggingProperties properties;
    private final LogRedactService redactService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        try  {
            long startTime = System.nanoTime();
            ContentCachingRequestWrapper wrappedReq = new ContentCachingRequestWrapper(req);
            ContentCachingResponseWrapper wrappedRes = new ContentCachingResponseWrapper(res);

            LogContext context = createLogContext(wrappedReq, startTime);
            logRequestIn(context);

            chain.doFilter(wrappedReq, wrappedRes);

            enrichHttpResponse(context, wrappedRes);
            logResponseOut(context, wrappedRes);
            wrappedRes.copyBodyToResponse();
        } finally {
            MDC.clear();
        }
    }

    private LogContext createLogContext(ContentCachingRequestWrapper req, long startTime) {
        LogContext context = mapper.toLogContext(req, startTime, properties);
        LogContextHolder.set(context);

        MDC.put("transaction-id", context.getIdentifiers().getTransactionId());
        MDC.put("trace-id", context.getIdentifiers().getTraceId());

        return context;
    }

    private void logRequestIn(LogContext context) throws JsonProcessingException {
        LogEntry requestIn = mapper.toRequestIn(context);
        String requestInJson = jsonMapper.writeValueAsString(requestIn);
        String redactedRequestInJson = redactService.redact(requestInJson);
        log.info("{}", redactedRequestInJson);
    }

    private void logResponseOut(LogContext context, ContentCachingResponseWrapper res) throws JsonProcessingException {
        LogEntry responseOut = mapper.toResponseOut(context, res, properties);
        String responseOutJson = jsonMapper.writeValueAsString(responseOut);
        String redactedResponseOutJson = redactService.redact(responseOutJson);
        log.info("{}", redactedResponseOutJson);
    }

    private static void enrichHttpResponse(LogContext context, ContentCachingResponseWrapper res) {
        res.setHeader(LoggingHttpHeaders.TRANSACTION_ID.getValue(), context.getIdentifiers().getTransactionId());
        res.setHeader(LoggingHttpHeaders.TRACE_ID.getValue(), context.getIdentifiers().getTraceId());
    }

}