package com.playground.logging.domain;

public class LogContextHolder {

    private static final ThreadLocal<LogContext> threadLogContext = new ThreadLocal<>();

    public static void clear() {
        threadLogContext.remove();
    }

    public static LogContext get() {
        return threadLogContext.get();
    }

    public static void set(LogContext context) {
        clear();
        threadLogContext.set(context);
    }

}
