package com.treloc.xtreloc.io;

import java.io.IOException;
import java.nio.file.Path;

public final class RunContextFactory {

    private RunContextFactory() {
    }

    public static RunContext fromCLI(
            String mode,
            AppConfig config) throws IOException {

        AppConfig.ModeConfig mc = config.modes.get(mode);
        if (mc == null) {
            throw new ConfigException("Unknown mode: " + mode);
        }

        // Validate required paths for batch processing modes (GRD, STD, MCMC)
        if (mode.equals("GRD") || mode.equals("STD") || mode.equals("MCMC")) {
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
