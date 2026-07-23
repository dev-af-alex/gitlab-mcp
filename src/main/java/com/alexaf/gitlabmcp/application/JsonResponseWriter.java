package com.alexaf.gitlabmcp.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonResponseWriter {

    private final ObjectMapper objectMapper;

    public JsonResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize MCP response", e);
        }
    }
}
