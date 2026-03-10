package com.treloc.xtreloc.io;

import java.nio.file.Path;

/**
 * Runtime execution context for a single CLI or GUI run.
 * Holds the selected solver mode, input .dat files, and output directory.
 */
public final class RunContext {

    private final String mode;
    private final Path[] datFiles;
    private final Path outDir;

    /**
     * Creates a run context.
     *
     * @param mode solver mode (e.g. GRD, LMO)
     * @param datFiles input .dat file paths (may be empty for some modes)
     * @param outDir output directory for results
     */
    public RunContext(String mode, Path[] datFiles, Path outDir) {
        this.mode = mode;
        this.datFiles = datFiles;
        this.outDir = outDir;
    }

    /** Solver mode (e.g. GRD, LMO, MCMC). */
    public String getMode() {
        return mode;
    }

    /** Input .dat file paths for this run. */
    public Path[] getDatFiles() {
        return datFiles;
    }

    /** Output directory for catalog and .dat results. */
    public Path getOutDir() {
        return outDir;
    }
}
