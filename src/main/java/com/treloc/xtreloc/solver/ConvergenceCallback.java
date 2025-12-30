package com.treloc.xtreloc.solver;

/**
 * Callback interface for reporting convergence information during solver execution.
 * Used to provide real-time updates to GUI components.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public interface ConvergenceCallback {
    /**
     * Called when residual is updated during iteration.
     * 
     * @param iteration iteration number (0-based)
     * @param residual current residual value
     */
    void onResidualUpdate(int iteration, double residual);
    
    /**
     * Called when log-likelihood is updated (for MCMC mode).
     * 
     * @param sample sample number
     * @param logLikelihood current log-likelihood value
     */
    default void onLikelihoodUpdate(int sample, double logLikelihood) {
        // Optional: default implementation does nothing
    }
    
    /**
     * Called when cluster-specific residual is updated (for TRD mode).
     * 
     * @param clusterId cluster ID
     * @param iteration iteration number within the cluster
     * @param residual current residual value
     */
    default void onClusterResidualUpdate(int clusterId, int iteration, double residual) {
        // Optional: default implementation does nothing
    }
    
    /**
     * Called when iteration information is updated (for STD mode).
     * 
     * @param iteration iteration number
     * @param evaluations number of function evaluations
     * @param residual current residual
     * @param parameterChanges array of parameter changes [lon, lat, dep]
     */
    default void onIterationUpdate(int iteration, int evaluations, double residual, 
                                   double[] parameterChanges) {
        // Optional: default implementation calls onResidualUpdate
        onResidualUpdate(iteration, residual);
    }
}

