package com.treloc.xtreloc.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Path;

/**
 * Shared {@link ObjectMapper} instance for JSON (de)serialization.
 * Thread-safe; reuse instead of creating new ObjectMapper().
 */
public final class JsonMapperHolder {

    private static final ObjectMapper MAPPER = createMapper();

    private JsonMapperHolder() {
    }

    /**
     * Returns the shared ObjectMapper (with Path deserializer registered).
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        com.fasterxml.jackson.databind.module.SimpleModule module =
            new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addDeserializer(Path.class, new com.treloc.xtreloc.io.PathDeserializer());
        module.addSerializer(Path.class, new com.treloc.xtreloc.io.PathSerializer());
        mapper.registerModule(module);
        return mapper;
    }
}
