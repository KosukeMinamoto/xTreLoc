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

        addTaupFileErrors(errors, config.taupFile, false);

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
        sb.append("  raytraceMethod: ")
            .append(config.raytraceMethod != null && !config.raytraceMethod.isEmpty()
                ? config.raytraceMethod : "layered")
            .append(" (1-D models only; tables/grids ignore this)").append("\n");
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

    /**
     * Validates {@code taupFile}: bundled 1-D name, on-disk {@code .nd}/{@code .tvel} (Vp and Vs),
     * WIN / hypomh structure ({@code .aob}, {@code struct.tbl}), or NonLinLoc travel-time grid directory.
     *
     * @param required when true, a missing value is an error (TUI execute); load-time validation is not required
     */
    public static void addTaupFileErrors(List<String> errors, String spec, boolean required) {
        if (spec == null || spec.isBlank()) {
            if (required) {
                errors.add("taupFile is required: a velocity model (1-D .nd/.tvel with Vp and Vs) OR a travel-time table (*.time.hdr).");
            }
            return;
        }
        String taupPath = spec.trim();
        if (VelocityModelCatalog.isBundledModelName(taupPath)
                || com.treloc.xtreloc.solver.table.TravelTimeTables.isTableSpec(taupPath)) {
            return;
        }
        Path p = Paths.get(taupPath);
        if (!Files.exists(p)) {
            errors.add("taupFile: No such file or directory: " + spec);
            return;
        }
        if (Files.isDirectory(p)) {
            errors.add("taupFile: Directory is not a travel-time grid (*.time.hdr): " + spec);
            return;
        }
        if (!Files.isRegularFile(p)) {
            errors.add("taupFile: Not a regular file: " + spec);
            return;
        }
        String name = p.getFileName().toString().toLowerCase();
        if (!name.endsWith(".nd") && !name.endsWith(".tvel") && !name.endsWith(".taup")
                && !name.endsWith(".time.hdr") && !name.endsWith(".aob") && !name.endsWith(".tbl")
                && !name.endsWith(".win") && !name.contains("struct")) {
            errors.add("taupFile: Expected .nd/.tvel (Vp and Vs), WIN struct (.aob/.tbl), or a grid directory (*.time.hdr): " + spec);
        }
    }
}
