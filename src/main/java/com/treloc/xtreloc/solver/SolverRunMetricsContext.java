package com.treloc.xtreloc.solver;

/**
 * Holds per-thread {@link SolverRunMetrics} from the last successful solver {@code start()} completion.
 * Parallel batch workers must clear at run start so stale values cannot leak across tasks.
 */
public final class SolverRunMetricsContext {

    private static final ThreadLocal<SolverRunMetrics> LAST = new ThreadLocal<>();

    private SolverRunMetricsContext() {
    }

    public static void clear() {
        LAST.remove();
    }

    public static void set(SolverRunMetrics metrics) {
        LAST.set(metrics);
    }

    /**
     * Returns metrics set by the solver on this thread and clears the slot.
     */
    public static SolverRunMetrics getAndClear() {
        SolverRunMetrics m = LAST.get();
        LAST.remove();
        return m;
    }
}
