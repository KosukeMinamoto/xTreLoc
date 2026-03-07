package com.treloc.xtreloc.io;

import java.io.IOException;
import java.nio.file.Path;

public final class RunContextFactory {

    private RunContextFactory() {
    }

    public static RunContext fromCLI(
            String mode,
            AppConfig config) throws IOException {

        if (config == null) {
            throw new ConfigException("Config must not be null.");
        }
        if (config.getModes() == null || config.getModes().isEmpty()) {
            throw new ConfigException("Config has no \"io\" / \"params\" section. Add at least one mode in config.json.");
        }
        if (!config.getModes().containsKey(mode)) {
            throw new ConfigException("Unknown mode: " + mode + ". Add \"" + mode + "\" to the \"io\" section in config.json.");
        }

        AppConfig.ModeConfig mc = ModeConfigResolver.getModeConfigWithFallback(config, mode);
        if (mc == null) {
            throw new ConfigException("Unknown mode: " + mode + ". Add \"" + mode + "\" to the \"io\" section in config.json.");
        }
        // When DE has no config entry, default outDirectory to dat-de under parent of datDirectory
        if ("DE".equals(mode) && config.getModes().get("DE") == null && mc.datDirectory != null && mc.outDirectory == null) {
            mc.outDirectory = mc.datDirectory.getParent().resolve("dat-de");
        }

        // Validate required paths for batch processing modes (GRD, LMO, MCMC, DE)
        if (mode.equals("GRD") || mode.equals("LMO") || mode.equals("MCMC") || mode.equals("DE")) {
            if (mc.datDirectory == null) {
                throw new ConfigException(
                    String.format("Mode %s requires datDirectory to be specified in config", mode));
            }
            if (mc.outDirectory == null) {
                throw new ConfigException(
                    String.format("Mode %s requires outDirectory to be specified in config", mode));
            }
            // Validate that datDirectory exists
            if (!java.nio.file.Files.exists(mc.datDirectory)) {
                throw new ConfigException(
                    String.format("Target directory does not exist: %s\n" +
                        "  Please check the datDirectory path in config.json for mode %s",
                        mc.datDirectory, mode));
            }
            if (!java.nio.file.Files.isDirectory(mc.datDirectory)) {
                throw new ConfigException(
                    String.format("Target path is not a directory: %s\n" +
                        "  Please check the datDirectory path in config.json for mode %s",
                        mc.datDirectory, mode));
            }
        }

        Path[] datFiles = (mc.datDirectory != null) 
            ? FileScanner.scan(mc.datDirectory)
            : new Path[0];
        return new RunContext(mode, datFiles, mc.outDirectory);
    }
}
