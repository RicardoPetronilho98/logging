package com.playground.logging.redaction;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.playground.logging.config.LoggingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogRedactService {

    private static final Configuration config = Configuration.defaultConfiguration();
    private final LoggingProperties properties;

    public String redact(String json) {
        if (properties == null) {
            log.trace("Logging properties are not set. Skipping redaction. Returning original log.");
            return json;
        }

        if (!StringUtils.hasText(json)) {
            return json;
        }

        if (!json.trim().startsWith("{") && !json.trim().startsWith("[")) {
            log.trace("Not a JSON log. Skipping redaction. Returning original log.");
            return json;
        }

        try {
            DocumentContext context = JsonPath.using(config).parse(json);
            mask(context);
            hide(context);
            return context.jsonString(); // re-serialize back to JSON (redacted)
        } catch (Exception e) {
            log.error("Could not parse JSON log. Skipping redaction. Returning original log. Reason: {}", e.getMessage());
            return json;
        }

    }

    private void mask(DocumentContext context) {
        if (properties.getMask() != null && !CollectionUtils.isEmpty(properties.getMask().getFields())) {
            for (String path : properties.getMask().getFields()) {
                try {
                    context.set(path, properties.getMask().getTag());
                } catch (Exception e) {
                    log.trace("Could not find log path {}. Skipping field masking. Reason: {}", path, e.getMessage());
                }
            }
        }
    }

    private void hide(DocumentContext context) {
        if (properties.getHide() != null && !CollectionUtils.isEmpty(properties.getHide().getFields())) {
            for (String path : properties.getHide().getFields()) {
                try {
                    context.delete(path);
                } catch (Exception e) {
                    log.trace("Could not find log path {}. Skipping field hiding. Reason: {}", path, e.getMessage());
                }
            }
        }
    }

}
