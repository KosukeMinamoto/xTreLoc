package com.treloc.xtreloc.solver;

/**
 * Numeric outcome of a single-event solver run (wall time, iteration counts, final RMS).
 * Published via {@link SolverRunMetricsContext} for GUI batch summaries.
 */
public final class SolverRunMetrics {

    /** Meaning varies by solver (LM iterations, MCMC samples, DE generations, GRD grid points). */
    public final int iterations;
    /** Function evaluations (where applicable). */
    public final int evaluations;
    public final long wallTimeMs;
    /** Final travel-time residual RMS (s) after the run. */
    public final double finalRms;
    /** Optional short summary for batch TSV note column (e.g. LMO excluded station pairs). */
    public final String note;

    public SolverRunMetrics(int iterations, int evaluations, long wallTimeMs, double finalRms) {
        this(iterations, evaluations, wallTimeMs, finalRms, null);
    }

    public SolverRunMetrics(int iterations, int evaluations, long wallTimeMs, double finalRms, String note) {
        this.iterations = iterations;
        this.evaluations = evaluations;
        this.wallTimeMs = wallTimeMs;
        this.finalRms = finalRms;
        this.note = note;
    }
}
