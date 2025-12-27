package com.treloc.xtreloc.io;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Load config.json into AppConfig
 */
public final class ConfigLoader {

    private final AppConfig config;

    public ConfigLoader(String jsonFile) throws IOException {
        this(Path.of(jsonFile));
    }

    public ConfigLoader(Path jsonFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // Register Path deserializer module
        com.fasterxml.jackson.databind.module.SimpleModule module = 
            new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addDeserializer(Path.class, new PathDeserializer());
        mapper.registerModule(module);
        this.config = mapper.readValue(jsonFile.toFile(), AppConfig.class);
    }

    public AppConfig getConfig() {
        return config;
    }
}
