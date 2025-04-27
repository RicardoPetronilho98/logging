package com.playground.logging.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "logging")
public class LoggingProperties {

    private Payload payload;
    private Hide hide;
    private Mask mask;

    @Data
    public static class Payload {
        private Integer maxLength;
    }

    @Data
    public static class Hide {
        private List<String> fields;
    }

    @Data
    public static class Mask {
        private String tag;
        private List<String> fields;
    }

}
