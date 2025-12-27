package com.treloc.xtreloc.solver;

import java.io.IOException;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.AppConfig;
import edu.sc.seis.TauP.TauModelException;

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

    /**
     * Constructs a HypoStationPairDiff object with the specified configuration.
     * Loads station data from the configuration file.
     *
     * @param appConfig the application configuration
     * @throws TauModelException if there is an error loading the TauP model
     * @throws RuntimeException if station data cannot be loaded
     */
    public HypoStationPairDiff(AppConfig appConfig) throws TauModelException {
        super(appConfig);
        this.hypBottom = appConfig.hypBottom;
        
        // Load LM optimization parameters from config
        if (appConfig.solver != null && appConfig.solver.containsKey("STD")) {
            var stdSolver = appConfig.solver.get("STD");
            if (stdSolver.has("initialStepBoundFactor")) {
                this.initialStepBoundFactor = stdSolver.get("initialStepBoundFactor").asDouble();
            }
            if (stdSolver.has("costRelativeTolerance")) {
                this.costRelativeTolerance = stdSolver.get("costRelativeTolerance").asDouble();
            }
            if (stdSolver.has("parRelativeTolerance")) {
                this.parRelativeTolerance = stdSolver.get("parRelativeTolerance").asDouble();
            }
            if (stdSolver.has("orthoTolerance")) {
                this.orthoTolerance = stdSolver.get("orthoTolerance").asDouble();
            }
            if (stdSolver.has("maxEvaluations")) {
                this.maxEvaluations = stdSolver.get("maxEvaluations").asInt();
            }
            if (stdSolver.has("maxIterations")) {
                this.maxIterations = stdSolver.get("maxIterations").asInt();
            }
        }
        
        logger.info(String.format("LM optimization parameters: initialStepBoundFactor=%.1f, costRelativeTolerance=%.2e, parRelativeTolerance=%.2e, orthoTolerance=%.2e, maxEvaluations=%d, maxIterations=%d",
            initialStepBoundFactor, costRelativeTolerance, parRelativeTolerance, orthoTolerance, maxEvaluations, maxIterations));
    }

    /**
     * Performs hypocenter location using the Levenberg-Marquardt algorithm.
     * Reads input data from a .dat file and writes the result to an output file.
     *
     * @param datFile the input .dat file path
     * @param outFile the output .dat file path
     * @throws TauModelException if there is an error in the Tau model
     * @throws RuntimeException if file I/O fails
     */
    public void start(String datFile, String outFile) throws TauModelException {
        String fileName = new java.io.File(datFile).getName();
        logger.info("Starting station-pair double difference location for: " + fileName);
        System.out.println("Starting station-pair double difference location for: " + fileName);
        
        Point point;
        try {
            point = loadPointFromDatFile(datFile);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to read dat file in STD mode:\n");
            errorMsg.append("  Input file: ").append(datFile).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
            logger.severe(errorMsg.toString());
            throw new RuntimeException("Failed to read dat file: " + datFile, e);
        }
        
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
            target.setEntry(i, lagTable[i][2]);
        }

        boolean hasOutlier = false;
        boolean[] isOutlier = new boolean[numPhase];
        int nIter = 0;
        int nEval = 0;
        for (int n = 0; n < 10; n++) {
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
                            double dep = params.getEntry(2);
                            if (dep <= stnBottom) {
                                params.setEntry(2, Math.random() * hypBottom);
                            }
                            if (dep > hypBottom) {
                                params.setEntry(2, hypBottom * 0.9);
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

            RealVector resDiffTime = optimum.getResiduals();
            double sumOfSquares = 0;
            int okCount = 0;
            for (int i = 0; i < numPhase; i++) {
                if (!isOutlier[i]) {
                    sumOfSquares += Math.pow(resDiffTime.getEntry(i), 2);
                    okCount++;
                }
            }
            res = Math.sqrt(sumOfSquares / okCount);

            for (int i = 0; i < numPhase; i++) {
                if (!isOutlier[i]) {
                    if (Math.abs(resDiffTime.getEntry(i)) > 2 * res) {
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
            
            lon = newLon;
            lat = newLat;
            dep = newDep;
            
            if (dep <= stnBottom) {
                dep = Math.random() * hypBottom;
                logger.warning("Depth too shallow, reset to: " + dep);
            }
            if (dep > hypBottom) {
                dep = hypBottom * 0.9;
                logger.warning("Depth too deep, limited to: " + dep);
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
                        lagTable[i][3] = 1.0 / Math.abs(resDiffTime.getEntry(i));
                    }
                }
                
                if (dep > hypBottom) {
                    method = "ERR";
                } else {
                    method = "STD";
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
            logger.info("Station-pair double difference location completed for: " + fileName);
            System.out.println("Station-pair double difference location completed for: " + fileName);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to write output file in STD mode:\n");
            errorMsg.append("  Output file: ").append(outFile).append("\n");
            errorMsg.append("  Input file: ").append(datFile).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
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
                    Point point = new Point("", hypoVector.getEntry(1), hypoVector.getEntry(0), hypoVector.getEntry(2),
                        0, 0, 0, 0, "", "", -999);
                    Object[] tmp = partialDerivativeMatrix(stationTable, usedIdx, point);
                    double[][] dtdr = (double[][]) tmp[0];
                    double[] trvTime = (double[]) tmp[1];

                    for (int i = 0; i < lagTable.length; i++) {
                        int nstnk = (int) lagTable[i][0];
                        int nstnl = (int) lagTable[i][1];
                        if ((int) lagTable[i][3] == 0) {
                            value.setEntry(i, 0);
                            jacobian.setEntry(i, 0, 0);
                            jacobian.setEntry(i, 1, 0);
                            jacobian.setEntry(i, 2, 0);
                        } else {
                            value.setEntry(i, trvTime[nstnl] - trvTime[nstnk]);
                            jacobian.setEntry(i, 0, dtdr[nstnl][0] - dtdr[nstnk][0]);
                            jacobian.setEntry(i, 1, dtdr[nstnl][1] - dtdr[nstnk][1]);
                            jacobian.setEntry(i, 2, dtdr[nstnl][2] - dtdr[nstnk][2]);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Error when calculating partial derivative: " + e.getMessage());
                    value.setEntry(0, 0);
                    jacobian.setEntry(0, 0, 0);
                    jacobian.setEntry(0, 1, 0);
                    jacobian.setEntry(0, 2, 0);
                }
                return new Pair<RealVector, RealMatrix>(value, jacobian);
            }
        };
    }
}

