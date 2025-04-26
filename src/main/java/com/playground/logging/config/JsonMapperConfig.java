package com.playground.logging.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonMapperConfig {

    @Bean
    public ObjectMapper jsonMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 1. Exclude null fields
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 2. Support Java 8+ Date/Time types (LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());

        // 3. Read constructor parameter names if no @JsonProperty (optional, but clean)
        mapper.registerModule(new ParameterNamesModule());

        // 4. Serialize dates as ISO strings, not timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 5. Fail if unknown properties are sent (you can comment if you prefer flexible APIs)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }

}
