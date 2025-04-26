package com.playground.logging.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "logging")
public class LoggingProperties {

    private Payload payload;

    @Data
    public static class Payload {
        private int maxLength;
    }

}
