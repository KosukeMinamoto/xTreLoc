package com.treloc.xtreloc.app.util;

import com.fasterxml.jackson.databind.JsonNode;

import com.treloc.xtreloc.util.JsonMapperHolder;

import java.io.File;

/**
 * Reads {@code launcherMode} from {@code ~/.xtreloc/settings.json} without depending on GUI-module classes,
 * so the main launcher behaves consistently in full JAR and CLI-only JAR.
 */
public final class LauncherModePreference {

    public enum StartupMode {
        /** Show ASCII logo then prompt 0/1/2 (CLI/TUI/GUI). */
        SELECT,
        CLI,
        TUI,
        GUI;

        /** Normalized persisted value ({@code select}, {@code cli}, {@code tui}, {@code gui}). */
        public String toJsonValue() {
            switch (this) {
                case SELECT:
                    return "select";
                case CLI:
                    return "cli";
                case TUI:
                    return "tui";
                case GUI:
                    return "gui";
            }
            throw new AssertionError(this);
        }

        public static StartupMode fromJson(String raw) {
            if (raw == null || raw.isBlank()) {
                return SELECT;
            }
            switch (raw.trim().toLowerCase()) {
                case "cli":
                case "cmd":
                case "console":
                    return CLI;
                case "tui":
                case "text":
                    return TUI;
                case "gui":
                case "swing":
                    return GUI;
                case "select":
                case "chooser":
                case "interactive":
                case "prompt":
                    return SELECT;
                default:
                    return SELECT;
            }
        }
    }

    public static final StartupMode DEFAULT = StartupMode.SELECT;

    private LauncherModePreference() {}

    public static StartupMode readPreferredStartupMode() {
        File settings = new File(System.getProperty("user.home"), ".xtreloc/settings.json");
        if (!settings.isFile()) {
            return DEFAULT;
        }
        try {
            JsonNode root = JsonMapperHolder.getMapper().readTree(settings);
            if (root != null && root.hasNonNull("launcherMode")) {
                return StartupMode.fromJson(root.get("launcherMode").asText());
            }
        } catch (Exception ignored) {
            // fall through
        }
        return DEFAULT;
    }
}
