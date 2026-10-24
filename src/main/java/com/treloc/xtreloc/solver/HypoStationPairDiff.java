package com.treloc.xtreloc.solver;

import java.io.IOException;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.StationRepository;
import com.treloc.xtreloc.util.SolverLogger;
import com.treloc.xtreloc.io.VelocityModelLoadException;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.Pair;
import org.apache.commons.math3.util.Precision;

/**
 * Hypocenter location using Levenberg-Marquardt algorithm
 * based on station pair differential travel times.
 * Implements the method described in Ide (2010) and Ohta et al. (2019).
 * 
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 */
public class HypoStationPairDiff extends SolverBase {
    private static final Logger logger = Logger.getLogger(HypoStationPairDiff.class.getName());
    private double hypBottom;
    
    // LM optimization parameters
    private double initialStepBoundFactor = 100.0;
    private double costRelativeTolerance = 1e-6;
    private double parRelativeTolerance = 1e-6;
    private double orthoTolerance = 1e-6;
    private int maxEvaluations = 1000;
    private int maxIterations = 1000;
    
    // Cache for partial derivative matrix (for same initial value in outlier removal loop)
    private static class PartialDerivativeCache {
        double lon;
        double lat;
        double dep;
        double[][] dtdrP;
        double[][] dtdrS;
        double[] trvP;
        double[] trvS;
        int[] usedIdx;
        
        boolean matches(double lon, double lat, double dep, int[] usedIdx) {
            if (this.usedIdx == null || this.usedIdx.length != usedIdx.length) {
                return false;
            }
            if (Math.abs(this.lon - lon) > 1e-9 || 
                Math.abs(this.lat - lat) > 1e-9 || 
                Math.abs(this.dep - dep) > 1e-6) {
                return false;
            }
            for (int i = 0; i < usedIdx.length; i++) {
                if (this.usedIdx[i] != usedIdx[i]) {
                    return false;
                }
            }
            return true;
        }
    }
    
    private PartialDerivativeCache partialDerivativeCache = null;
    private ConvergenceCallback convergenceCallback;

    /**
     * Sets the convergence callback for reporting convergence information.
     * 
     * @param callback the convergence callback
     */
    public void setConvergenceCallback(ConvergenceCallback callback) {
        this.convergenceCallback = callback;
    }

    /**
     * Constructs a HypoStationPairDiff object with the specified configuration.
     * Loads station data from the configuration file.
     *
     * @param appConfig the application configuration
     * @throws VelocityModelLoadException if there is an error loading the velocity model
     * @throws RuntimeException if station data cannot be loaded
     */
    public HypoStationPairDiff(AppConfig appConfig) throws VelocityModelLoadException {
        super(appConfig);
        this.hypBottom = appConfig.hypBottom;
        initLmoParams(appConfig);
    }

    /**
     * Constructs with pre-loaded station data (for parallel batch to avoid concurrent file I/O).
     */
    public HypoStationPairDiff(AppConfig appConfig, StationRepository stationRepo) throws VelocityModelLoadException {
        super(appConfig, stationRepo);
        this.hypBottom = appConfig.hypBottom;
        initLmoParams(appConfig);
    }

    private void initLmoParams(AppConfig appConfig) {
        if (appConfig.getParams() != null && appConfig.getParams().containsKey("LMO")) {
            var lmoSolver = appConfig.getParams().get("LMO");
            if (lmoSolver.has("initialStepBoundFactor")) {
                this.initialStepBoundFactor = lmoSolver.get("initialStepBoundFactor").asDouble();
            }
            if (lmoSolver.has("costRelativeTolerance")) {
                this.costRelativeTolerance = lmoSolver.get("costRelativeTolerance").asDouble();
            }
            if (lmoSolver.has("parRelativeTolerance")) {
                this.parRelativeTolerance = lmoSolver.get("parRelativeTolerance").asDouble();
            }
            if (lmoSolver.has("orthoTolerance")) {
                this.orthoTolerance = lmoSolver.get("orthoTolerance").asDouble();
            }
            if (lmoSolver.has("maxEvaluations")) {
                this.maxEvaluations = lmoSolver.get("maxEvaluations").asInt();
            }
            if (lmoSolver.has("maxIterations")) {
                this.maxIterations = lmoSolver.get("maxIterations").asInt();
            }
        }
        
        SolverLogger.info(String.format("LMO: Parameters: initialStepBoundFactor=%.1f, costTol=%.2e, parTol=%.2e, orthoTol=%.2e, maxEval=%d, maxIter=%d",
            initialStepBoundFactor, costRelativeTolerance, parRelativeTolerance, orthoTolerance, maxEvaluations, maxIterations));
        logger.info(String.format("LM optimization parameters: initialStepBoundFactor=%.1f, costRelativeTolerance=%.2e, parRelativeTolerance=%.2e, orthoTolerance=%.2e, maxEvaluations=%d, maxIterations=%d",
            initialStepBoundFactor, costRelativeTolerance, parRelativeTolerance, orthoTolerance, maxEvaluations, maxIterations));
    }

    /**
     * Performs hypocenter location using the Levenberg-Marquardt algorithm.
     * Reads input data from a .dat file and writes the result to an output file.
     *
     * @param datFile the input .dat file path
     * @param outFile the output .dat file path
     * @throws VelocityModelLoadException if there is an error in the velocity model
     * @throws RuntimeException if file I/O fails
     */
    public void start(String datFile, String outFile) throws VelocityModelLoadException {
        SolverRunMetricsContext.clear();
        String fileName = new java.io.File(datFile).getName();
        SolverLogger.info("LMO: Starting. File=" + fileName);
        logger.info("Starting Levenberg-Marquardt optimization for: " + fileName);
        
        Point point;
        try {
            point = loadPointFromDatFile(datFile);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to read dat file in LMO mode:\n");
            errorMsg.append("  Input file: ").append(datFile).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
            SolverLogger.severe("LMO: " + errorMsg.toString());
            logger.severe(errorMsg.toString());
            throw new RuntimeException("Failed to read dat file: " + datFile, e);
        }
        
        long wallT0 = System.nanoTime();

        PointsHandler pointsHandler = new PointsHandler();
        pointsHandler.setMainPoint(point);

        double[][] lagTable = point.getLagTable();
        int[] usedIdx = point.getUsedIdx();
        int numPhase = lagTable.length;
        double lat = point.getLat();
        double lon = point.getLon();
        double dep = point.getDep();
        double eLat = point.getElat();
        double eLon = point.getElon();
        double eDep = point.getEdep();
        double res = point.getRes();
        String method = point.getType();

        RealVector hypvec = MatrixUtils.createRealVector(new double[] {lon, lat, dep});
        RealVector target = MatrixUtils.createRealVector(new double[numPhase]);
        for (int i = 0; i < numPhase; i++) {
            target.setEntry(i, Math.sqrt(HypoUtils.phaseWeight(lagTable[i], pWeight)) * lagTable[i][2]);
        }

        boolean hasOutlier = false;
        boolean[] isOutlier = new boolean[numPhase];
        int nIter = 0;
        int nEval = 0;
        final double startLon = lon;
        final double startLat = lat;
        // Keep the search inside a local box. Absolute lon/lat plus a failed-ray residual
        // used to send LM to the other hemisphere; MCMC/GRD never take that Newton step.
        final double maxShiftDeg = 0.5;
        
        partialDerivativeCache = null;
        
        for (int n = 0; n < 10; n++) {
            // Check for interruption
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Levenberg-Marquardt optimization interrupted by user");
                SolverLogger.info("LMO: Interrupted by user");
                throw new RuntimeException("Levenberg-Marquardt optimization was interrupted");
            }
            
            LeastSquaresProblem problem = new LeastSquaresBuilder()
                    .start(hypvec)
                    .target(target)
                    .model(getPartialDerivativeFunction(lagTable, usedIdx))
                    .lazyEvaluation(false)
                    .maxEvaluations(maxEvaluations)
                    .maxIterations(maxIterations)
                    .parameterValidator(new ParameterValidator() {
                        @Override
                        public RealVector validate(RealVector params) {
                            double pLon = params.getEntry(0);
                            double pLat = params.getEntry(1);
                            double pDep = params.getEntry(2);
                            if (pLon < startLon - maxShiftDeg) {
                                params.setEntry(0, startLon - maxShiftDeg);
                            } else if (pLon > startLon + maxShiftDeg) {
                                params.setEntry(0, startLon + maxShiftDeg);
                            }
                            if (pLat < startLat - maxShiftDeg) {
                                params.setEntry(1, startLat - maxShiftDeg);
                            } else if (pLat > startLat + maxShiftDeg) {
                                params.setEntry(1, startLat + maxShiftDeg);
                            }
                            if (pDep < stnBottom) {
                                params.setEntry(2, stnBottom);
                            } else if (pDep > hypBottom) {
                                params.setEntry(2, hypBottom);
                            }
                            return params;
                        }
                    })
                    .build();
            LeastSquaresOptimizer.Optimum optimum = new LevenbergMarquardtOptimizer(
                initialStepBoundFactor,
                costRelativeTolerance,
                parRelativeTolerance,
                orthoTolerance,
                Precision.SAFE_MIN
            ).optimize(problem);

            double[] xmin0 = optimum.getPoint().toArray();
            Point fitted = new Point(point.getTime(), xmin0[1], xmin0[0], xmin0[2], 0, 0, 0, 0, "", "", -999);
            double[] pairRes = pairResiduals(stationTable, usedIdx, fitted, lagTable);
            res = HypoUtils.weightedRms(pairRes, lagTable, pWeight);

            for (int i = 0; i < numPhase; i++) {
                if (!isOutlier[i]) {
                    if (Double.isFinite(pairRes[i]) && Math.abs(pairRes[i]) > 2 * res) {
                        lagTable[i][3] = 0;
                        isOutlier[i] = true;
                        hasOutlier = true;
                    }
                }
            }

            double[] xmin = optimum.getPoint().toArray();
            double newLon = xmin[0];
            double newLat = xmin[1];
            double newDep = xmin[2];
            
            double deltaLon = Math.abs(newLon - lon);
            double deltaLat = Math.abs(newLat - lat);
            double deltaDep = Math.abs(newDep - dep);
            logger.info(String.format("Iteration %d: delta=(%.6f deg, %.6f deg, %.3f km), new=(%.6f, %.6f, %.3f)", 
                n, deltaLon, deltaLat, deltaDep, newLon, newLat, newDep));
            
            // Report convergence information
            if (convergenceCallback != null) {
                double[] paramChanges = new double[]{deltaLon, deltaLat, deltaDep};
                convergenceCallback.onIterationUpdate(n, nEval, res, paramChanges);
            }
            
            lon = newLon;
            lat = newLat;
            dep = newDep;
            
            if (dep < stnBottom) {
                dep = stnBottom;
                logger.warning("Depth too shallow, clamped to: " + dep);
                SolverLogger.warning("LMO: Depth too shallow, clamped to " + String.format("%.3f", dep));
            }
            if (dep > hypBottom) {
                dep = hypBottom;
                logger.warning("Depth too deep, limited to: " + dep);
                SolverLogger.warning("LMO: Depth too deep, limited to " + String.format("%.3f", dep));
            }
            
            hypvec = MatrixUtils.createRealVector(new double[] {lon, lat, dep});
            
            if (hasOutlier) {
                hasOutlier = false;
                continue;
            } else {
                for (int i = 0; i < numPhase; i++) {
                    if (isOutlier[i]) {
                        lagTable[i][3] = 0;
                    } else {
                        lagTable[i][3] = Double.isFinite(pairRes[i])
                            ? 1.0 / Math.abs(pairRes[i] + 1e-10)
                            : 0;
                    }
                }
                
                if (dep > hypBottom || !isSourceInRange(lat, lon, dep)) {
                    method = "ERR";
                } else {
                    method = "LMO";
                }

                boolean success = false;
                RealVector sigma = new ArrayRealVector(3);
                try {
                    RealMatrix fjac = optimum.getJacobian();
                    RealMatrix rtr = fjac.transpose().multiply(fjac);
                    LUDecomposition luDecomposition = new LUDecomposition(rtr);
                    RealMatrix err = luDecomposition.getSolver().getInverse().scalarMultiply(res * res);
                    for (int i = 0; i < 3; i++) {
                        sigma.setEntry(i, Math.sqrt(err.getEntry(i, i)));
                    }
                    success = true;
                } catch (SingularMatrixException e) {
                    logger.warning("Singular matrix exception when error estimation: " + e.getMessage());
                    SolverLogger.warning("LMO: Singular matrix when error estimation.");
                }

                if (!success) {
                    try {
                        RealVector tmp = optimum.getSigma(1e-10);
                        for (int i = 0; i < 3; i++) {
                            sigma.setEntry(i, tmp.getEntry(i));
                        }
                    } catch (SingularMatrixException e) {
                        for (int i = 0; i < 3; i++) {
                            sigma.setEntry(i, 999);
                        }
                    }
                }

                eLon = sigma.getEntry(0) * getDeg2Km() * Math.cos(Math.toRadians(lat));
                eLat = sigma.getEntry(1) * getDeg2Km();
                eDep = sigma.getEntry(2);

                nIter = optimum.getIterations();
                nEval = optimum.getEvaluations();
                break;
            }
        }

        point.setLat(lat);
        point.setLon(lon);
        point.setDep(dep);
        point.setElat(eLat);
        point.setElon(eLon);
        point.setEdep(eDep);
        point.setRes(res);
        point.setType(method);
        point.setLagTable(lagTable);
        
        try {
            pointsHandler.writeDatFile(outFile, codeStrings);
            long ms = (System.nanoTime() - wallT0) / 1_000_000L;
            String excludedPairsNote = formatExcludedStationPairsNote(isOutlier, lagTable, codeStrings);
            SolverRunMetricsContext.set(new SolverRunMetrics(nIter, nEval, ms, res, excludedPairsNote));
            SolverLogger.info("LMO: Completed. File=" + fileName);
            logger.info("Levenberg-Marquardt optimization completed for: " + fileName);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to write output file in LMO mode:\n");
            errorMsg.append("  Output file: ").append(outFile).append("\n");
            errorMsg.append("  Input file: ").append(datFile).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
            SolverLogger.severe("LMO: " + errorMsg.toString());
            logger.severe(errorMsg.toString());
            throw new RuntimeException("Failed to write output file: " + outFile, e);
        }

        logger.info(String.format("%.3f %.3f %.3f %.3f %.3f %.3f %.3f", lon, lat, dep, eLon, eLat, eDep, res));
        logger.info(String.format("    (Evaluations: %d, Iterations: %d)", nEval, nIter));
    }

    /**
     * Creates a MultivariateJacobianFunction for the Levenberg-Marquardt optimizer.
     * Computes the residual vector and Jacobian matrix for station pair differential travel times.
     * Units: jacobian[i][0] = dt/dlon [s/deg], jacobian[i][1] = dt/dlat [s/deg], jacobian[i][2] = dt/ddep [s/km]
     *
     * @param lagTable the lag time table with columns [station_k_idx, station_l_idx, lag_time, weight]
     * @param usedIdx  the index list of stations to use
     * @return a MultivariateJacobianFunction that computes residuals and Jacobian matrix
     */
    public MultivariateJacobianFunction getPartialDerivativeFunction(double[][] lagTable, int[] usedIdx) {
        return new MultivariateJacobianFunction() {
            public Pair<RealVector, RealMatrix> value(RealVector hypoVector) {
                RealVector value = new ArrayRealVector(lagTable.length);
                RealMatrix jacobian = new Array2DRowRealMatrix(lagTable.length, 3);

                try {
                    double currentLon = hypoVector.getEntry(0);
                    double currentLat = hypoVector.getEntry(1);
                    double currentDep = hypoVector.getEntry(2);
                    
                    double[][] dtdrP;
                    double[][] dtdrS;
                    double[] trvP;
                    double[] trvS;
                    
                    if (partialDerivativeCache != null && 
                        partialDerivativeCache.matches(currentLon, currentLat, currentDep, usedIdx)) {
                        dtdrP = partialDerivativeCache.dtdrP;
                        dtdrS = partialDerivativeCache.dtdrS;
                        trvP = partialDerivativeCache.trvP;
                        trvS = partialDerivativeCache.trvS;
                    } else {
                        Point point = new Point("", currentLat, currentLon, currentDep,
                            0, 0, 0, 0, "", "", -999);
                        boolean[] ph = DatPair.phasesPresent(lagTable);
                        boolean needP = ph[0];
                        boolean needS = ph[1];
                        dtdrP = dtdrS = null;
                        trvP = trvS = null;
                        if (needP) {
                            Object[] tmpP = partialDerivativeMatrix(stationTable, usedIdx, point,
                                com.treloc.xtreloc.solver.table.TravelTimeTable.Phase.P);
                            dtdrP = (double[][]) tmpP[0];
                            trvP = (double[]) tmpP[1];
                        }
                        if (needS) {
                            Object[] tmpS = partialDerivativeMatrix(stationTable, usedIdx, point,
                                com.treloc.xtreloc.solver.table.TravelTimeTable.Phase.S);
                            dtdrS = (double[][]) tmpS[0];
                            trvS = (double[]) tmpS[1];
                        }
                        if (dtdrP == null) {
                            dtdrP = dtdrS;
                            trvP = trvS;
                        }
                        if (dtdrS == null) {
                            dtdrS = dtdrP;
                            trvS = trvP;
                        }
                        
                        partialDerivativeCache = new PartialDerivativeCache();
                        partialDerivativeCache.lon = currentLon;
                        partialDerivativeCache.lat = currentLat;
                        partialDerivativeCache.dep = currentDep;
                        partialDerivativeCache.dtdrP = dtdrP;
                        partialDerivativeCache.dtdrS = dtdrS;
                        partialDerivativeCache.trvP = trvP;
                        partialDerivativeCache.trvS = trvS;
                        partialDerivativeCache.usedIdx = usedIdx.clone();
                    }

                    for (int i = 0; i < lagTable.length; i++) {
                        int nstnk = (int) lagTable[i][0];
                        int nstnl = (int) lagTable[i][1];
                        double[] t = DatPair.timesFor(lagTable[i], trvP, trvS);
                        if ((int) lagTable[i][3] == 0 || !DatPair.finitePair(t, nstnk, nstnl)) {
                            value.setEntry(i, 0);
                            jacobian.setEntry(i, 0, 0);
                            jacobian.setEntry(i, 1, 0);
                            jacobian.setEntry(i, 2, 0);
                            continue;
                        }
                        double[][] dtdr = DatPair.dtdrFor(lagTable[i], dtdrP, dtdrS);
                        double sw = Math.sqrt(HypoUtils.phaseWeight(lagTable[i], pWeight));
                        value.setEntry(i, sw * (t[nstnl] - t[nstnk]));
                        jacobian.setEntry(i, 0, sw * (dtdr[nstnl][0] - dtdr[nstnk][0]));
                        jacobian.setEntry(i, 1, sw * (dtdr[nstnl][1] - dtdr[nstnk][1]));
                        jacobian.setEntry(i, 2, sw * (dtdr[nstnl][2] - dtdr[nstnk][2]));
                    }
                } catch (Exception e) {
            logger.warning("Error when calculating partial derivative: " + e.getMessage());
            SolverLogger.warning("LMO: Error calculating partial derivative.");
                    value.setEntry(0, 0);
                    jacobian.setEntry(0, 0, 0);
                    jacobian.setEntry(0, 1, 0);
                    jacobian.setEntry(0, 2, 0);
                }
                return new Pair<RealVector, RealMatrix>(value, jacobian);
            }
        };
    }

    /**
     * Lag-table rows (1-based index among differential-time pairs in order stored) excluded as outliers
     * during LM iterations. Format: {@code row:stationCodeK-stationCodeL, ...}
     */
    private static String formatExcludedStationPairsNote(boolean[] isOutlier, double[][] lagTable, String[] codeStrings) {
        if (isOutlier == null || lagTable == null || codeStrings == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lagTable.length; i++) {
            if (i >= isOutlier.length || !isOutlier[i]) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            int ik = (int) lagTable[i][0];
            int il = (int) lagTable[i][1];
            sb.append(i + 1).append(':')
                .append(stationCodeAt(codeStrings, ik)).append('-').append(stationCodeAt(codeStrings, il))
                .append('(').append(DatPair.label(lagTable[i])).append(')');
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String stationCodeAt(String[] codeStrings, int idx) {
        if (idx >= 0 && idx < codeStrings.length) {
            return codeStrings[idx];
        }
        return "?" + idx;
    }
}

