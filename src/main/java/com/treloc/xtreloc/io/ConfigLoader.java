package com.treloc.xtreloc.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.util.JsonMapperHolder;

/**
 * Loads config.json into {@link AppConfig}.
 * <p>
 * Path parameters may be missing or invalid at load time; such cases are logged as WARNING
 * and loading continues. Paths are validated at execute time for the selected mode.
 */
public final class ConfigLoader {

    private static final Logger logger = Logger.getLogger(ConfigLoader.class.getName());
    private final AppConfig config;

    /**
     * Loads configuration from a JSON file.
     *
     * @param jsonFile path to the config file (e.g. config.json)
     * @throws IOException if the file is not found, not a file, or JSON parsing fails
     */
    public ConfigLoader(String jsonFile) throws IOException {
        this(Path.of(jsonFile));
    }

    /**
     * Loads configuration from a JSON file.
     *
     * @param jsonFile path to the config file
     * @throws IOException if the file is not found, not a file, or JSON parsing fails
     */
    public ConfigLoader(Path jsonFile) throws IOException {
        java.io.File file = jsonFile.toFile();
        if (!file.exists()) {
            throw new IOException("Configuration file not found: " + jsonFile + 
                "\nPlease ensure the file exists and the path is correct.");
        }
        if (!file.isFile()) {
            throw new IOException("Configuration path is not a file: " + jsonFile);
        }
        this.config = JsonMapperHolder.getMapper().readValue(file, AppConfig.class);
        Path configDir = jsonFile.toAbsolutePath().getParent();
        if (configDir != null) {
            this.config.resolveRelativePaths(configDir);
        }
        List<String> pathWarnings = ConfigValidator.validate(this.config);
        if (!pathWarnings.isEmpty()) {
            for (String w : pathWarnings) {
                logger.log(Level.WARNING, "Config path: " + w);
            }
            logger.log(Level.FINE, "Config summary:\n" + ConfigValidator.formatConfigSummary(this.config));
        }
    }

    /**
     * Returns the loaded configuration. Relative paths are resolved against the config file's directory.
     */
    public AppConfig getConfig() {
        return config;
    }
}
