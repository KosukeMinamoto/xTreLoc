package com.treloc.xtreloc.io;

/**
 * Exception thrown when configuration loading or validation fails.
 */
public class ConfigException extends RuntimeException {
    /** @param message error description */
    public ConfigException(String message) {
        super(message);
    }

    /** @param message error description; @param cause wrapped exception */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
