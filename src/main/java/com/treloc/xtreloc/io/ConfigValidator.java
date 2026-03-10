package com.treloc.xtreloc.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates path parameters in AppConfig.
 * Used at load time to produce WARNINGs (config still loads); at Execute time the same
 * checks are used per mode to produce Errors and block execution if incomplete.
 */
public final class ConfigValidator {

    private ConfigValidator() {}

    /**
     * Validates path parameters in the config. Each parameter is checked individually.
     * Used at config load: results are logged as WARNINGs and loading continues.
     *
     * @param config loaded config (after resolveRelativePaths)
     * @return list of warning/error messages, e.g. "stationFile: No such file or directory: /path/to/station.tbl"
     */
    public static List<String> validate(AppConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) return errors;

        if (config.stationFile != null && !config.stationFile.isEmpty()) {
            Path p = Paths.get(config.stationFile);
            if (!Files.exists(p)) {
                errors.add("stationFile: No such file or directory: " + config.stationFile);
            } else if (!Files.isRegularFile(p)) {
                errors.add("stationFile: Not a regular file: " + config.stationFile);
            }
        }

        if (config.taupFile != null && !config.taupFile.trim().isEmpty()) {
            String taupPath = config.taupFile.trim();
            if (!isBuiltinTaupModel(taupPath)) {
                Path p = Paths.get(taupPath);
                if (!Files.exists(p)) {
                    errors.add("taupFile: No such file or directory: " + config.taupFile);
                } else if (!Files.isRegularFile(p)) {
                    errors.add("taupFile: Not a regular file: " + config.taupFile);
                }
            }
        }

        if (config.io != null) {
            for (Map.Entry<String, AppConfig.ModeIOConfig> e : config.io.entrySet()) {
                String mode = e.getKey();
                AppConfig.ModeIOConfig m = e.getValue();
                if (m == null) continue;

                if (m.datDirectory != null) {
                    Path p = m.datDirectory;
                    if (!Files.exists(p)) {
                        errors.add("io." + mode + ".datDirectory: No such file or directory: " + p);
                    } else if (!Files.isDirectory(p)) {
                        errors.add("io." + mode + ".datDirectory: Not a directory: " + p);
                    }
                }

                if (m.outDirectory != null) {
                    Path p = m.outDirectory;
                    if (!Files.exists(p)) {
                        Path parent = p.getParent();
                        if (parent == null || !Files.exists(parent)) {
                            errors.add("io." + mode + ".outDirectory: No such file or directory (parent missing): " + p);
                        }
                        // else: output dir can be created, no error
                    } else if (!Files.isDirectory(p)) {
                        errors.add("io." + mode + ".outDirectory: Not a directory: " + p);
                    }
                }

                if (m.catalogFile != null && !m.catalogFile.isEmpty()) {
                    Path p = Paths.get(m.catalogFile);
                    if (!Files.exists(p)) {
                        errors.add("io." + mode + ".catalogFile: No such file or directory: " + m.catalogFile);
                    } else if (!Files.isRegularFile(p)) {
                        errors.add("io." + mode + ".catalogFile: Not a regular file: " + m.catalogFile);
                    }
                }
            }
        }

        return errors;
    }

    /**
     * Formats a short summary of path-related config values for log/debug output.
     *
     * @param config the config to summarize (may be null)
     * @return a multi-line string with stationFile, taupFile, and io.* paths
     */
    public static String formatConfigSummary(AppConfig config) {
        if (config == null) return "(config is null)";
        StringBuilder sb = new StringBuilder();
        sb.append("  stationFile: ").append(config.stationFile != null ? config.stationFile : "(not set)").append("\n");
        sb.append("  taupFile: ").append(config.taupFile != null ? config.taupFile : "(not set)").append("\n");
        if (config.io != null) {
            for (Map.Entry<String, AppConfig.ModeIOConfig> e : config.io.entrySet()) {
                String mode = e.getKey();
                AppConfig.ModeIOConfig m = e.getValue();
                if (m == null) continue;
                sb.append("  io.").append(mode).append(".datDirectory: ")
                    .append(m.datDirectory != null ? m.datDirectory : "(not set)").append("\n");
                sb.append("  io.").append(mode).append(".outDirectory: ")
                    .append(m.outDirectory != null ? m.outDirectory : "(not set)").append("\n");
                sb.append("  io.").append(mode).append(".catalogFile: ")
                    .append(m.catalogFile != null ? m.catalogFile : "(not set)").append("\n");
            }
        }
        return sb.toString();
    }

    private static boolean isBuiltinTaupModel(String name) {
        if (name == null || name.isEmpty()) return false;
        String n = name.trim().toLowerCase();
        return "prem".equals(n) || "iasp91".equals(n) || "ak135".equals(n) || "ak135f".equals(n);
    }
}
