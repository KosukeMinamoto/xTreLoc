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
        java.io.File file = jsonFile.toFile();
        if (!file.exists()) {
            throw new IOException("Configuration file not found: " + jsonFile + 
                "\nPlease ensure the file exists and the path is correct.");
        }
        if (!file.isFile()) {
            throw new IOException("Configuration path is not a file: " + jsonFile);
        }
        
        ObjectMapper mapper = new ObjectMapper();
        // Register Path deserializer module
        com.fasterxml.jackson.databind.module.SimpleModule module = 
            new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addDeserializer(Path.class, new PathDeserializer());
        mapper.registerModule(module);
        this.config = mapper.readValue(file, AppConfig.class);
    }

    public AppConfig getConfig() {
        return config;
    }
}
