package com.treloc.xtreloc.io;

import java.nio.file.Path;

/**
 * Runtime execution context for CLI / GUI
 */
public final class RunContext {

    private final String mode;
    private final Path[] datFiles;
    private final Path outDir;

    public RunContext(String mode, Path[] datFiles, Path outDir) {
        this.mode = mode;
        this.datFiles = datFiles;
        this.outDir = outDir;
    }

    public String getMode() {
        return mode;
    }

    public Path[] getDatFiles() {
        return datFiles;
    }

    public Path getOutDir() {
        return outDir;
    }
}
