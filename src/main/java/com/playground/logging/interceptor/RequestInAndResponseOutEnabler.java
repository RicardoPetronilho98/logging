package com.playground.logging.interceptor;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.playground.logging.config.LoggingProperties;
import com.playground.logging.domain.LogContext;
import com.playground.logging.domain.LogContextHolder;
import com.playground.logging.domain.LogEntry;
import com.playground.logging.domain.LoggingHttpHeaders;
import com.playground.logging.mapper.LoggingMapper;
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
    private final LoggingProperties loggingProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        try  {
            long startTime = System.nanoTime();
            ContentCachingRequestWrapper wrappedReq = new ContentCachingRequestWrapper(req);
            ContentCachingResponseWrapper wrappedRes = new ContentCachingResponseWrapper(res);

            LogContext context = mapper.toLogContext(wrappedReq, startTime, loggingProperties);
            LogContextHolder.set(context);

            MDC.put("transaction-id", context.getIdentifiers().getTransactionId());
            MDC.put("trace-id", context.getIdentifiers().getTraceId());

            LogEntry requestIn = mapper.toRequestIn(context);
            log.info("{}", jsonMapper.writeValueAsString(requestIn));

            chain.doFilter(wrappedReq, wrappedRes);

            wrappedRes.setHeader(LoggingHttpHeaders.TRANSACTION_ID.getValue(), context.getIdentifiers().getTransactionId());
            wrappedRes.setHeader(LoggingHttpHeaders.TRACE_ID.getValue(), context.getIdentifiers().getTraceId());

            LogEntry responseOut = mapper.toResponseOut(context, wrappedRes, loggingProperties);
            log.info("{}", jsonMapper.writeValueAsString(responseOut));

            wrappedRes.copyBodyToResponse();
        } finally {
            MDC.clear();
        }
    }

}