package com.treloc.xtreloc.solver;

import java.io.IOException;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import edu.sc.seis.TauP.TauModelException;

/**
 * Hypocenter location using Markov Chain Monte Carlo (MCMC) method.
 * Implements Metropolis-Hastings algorithm for Bayesian hypocenter location.
 * 
 * @author K.M.
 * @version 0.1
 * @since 2025-12-26
 */
public class HypoMCMC extends SolverBase {
    private static final Logger logger = Logger.getLogger(HypoMCMC.class.getName());
    private double hypBottom;
    private int nSamples;
    private int burnIn;
    private double stepSize;
    private double stepSizeDepth; // Depth step size in km (separate from lat/lon step size)
    private double temperature;
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
     * Constructs a HypoMCMC object with the specified configuration.
     * 
     * @param appConfig the application configuration
     * @throws TauModelException if there is an error loading the TauP model
     */
    public HypoMCMC(AppConfig appConfig) throws TauModelException {
        super(appConfig);
        this.hypBottom = appConfig.hypBottom;
        
        // Load MCMC parameters from config
        JsonNode mcmcSolver = appConfig.solver != null ? appConfig.solver.get("MCMC") : null;
        if (mcmcSolver != null) {
            this.nSamples = mcmcSolver.has("nSamples") ? mcmcSolver.get("nSamples").asInt() : 1000;
            this.burnIn = mcmcSolver.has("burnIn") ? mcmcSolver.get("burnIn").asInt() : 200;
            this.stepSize = mcmcSolver.has("stepSize") ? mcmcSolver.get("stepSize").asDouble() : 0.1;
            // Depth step size in km (default: 1.0 km, which is approximately stepSize in degrees converted to km)
            this.stepSizeDepth = mcmcSolver.has("stepSizeDepth") ? mcmcSolver.get("stepSizeDepth").asDouble() : 1.0;
            this.temperature = mcmcSolver.has("temperature") ? mcmcSolver.get("temperature").asDouble() : 1.0;
        } else {
            // Default values
            this.nSamples = 1000;
            this.burnIn = 200;
            this.stepSize = 0.1;
            this.stepSizeDepth = 1.0; // Default depth step size: 1.0 km
            this.temperature = 1.0;
        }
        
        logger.info(String.format("MCMC parameters: nSamples=%d, burnIn=%d, stepSize=%.3f deg, stepSizeDepth=%.3f km, temperature=%.3f",
            nSamples, burnIn, stepSize, stepSizeDepth, temperature));
    }

    /**
     * Performs hypocenter location using MCMC method.
     * Reads input data from a .dat file and writes the result to an output file.
     * 
     * @param datFile the input .dat file path
     * @param outFile the output .dat file path
     * @throws TauModelException if there is an error in the Tau model
     * @throws RuntimeException if file I/O fails
     */
    public void start(String datFile, String outFile) throws TauModelException {
        String fileName = new java.io.File(datFile).getName();
        logger.info("Starting MCMC location for: " + fileName);
        System.out.println("Starting MCMC location for: " + fileName);
        
        Point point;
        try {
            point = loadPointFromDatFile(datFile);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to read dat file in MCMC mode:\n");
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
        
        // Initial hypocenter
        double lat = point.getLat();
        double lon = point.getLon();
        double dep = point.getDep();
        
        // Ensure initial depth is within bounds
        dep = Math.max(stnBottom, Math.min(hypBottom, dep));
        
        // Calculate initial likelihood
        double currentLikelihood = calculateLikelihood(lat, lon, dep, lagTable, usedIdx);
        
        // MCMC sampling
        int nEffective = nSamples - burnIn;
        if (nEffective <= 0) {
            throw new IllegalArgumentException("nSamples must be greater than burnIn");
        }
        double[] latSamples = new double[nEffective];
        double[] lonSamples = new double[nEffective];
        double[] depSamples = new double[nEffective];
        double[] likelihoodSamples = new double[nEffective];
        
        int accepted = 0;
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < nSamples; i++) {
            // Check for interruption periodically
            if (i % 100 == 0 && Thread.currentThread().isInterrupted()) {
                logger.info("MCMC sampling interrupted by user");
                throw new RuntimeException("MCMC sampling was interrupted");
            }
            
            // Propose new hypocenter
            double newLon = lon + random.nextGaussian() * stepSize;
            double newLat = lat + random.nextGaussian() * stepSize;
            // Use separate step size for depth (in km)
            double newDep = dep + random.nextGaussian() * stepSizeDepth;
            
            // Ensure depth is within bounds
            newDep = Math.max(stnBottom, Math.min(hypBottom, newDep));
            
            // Calculate new likelihood
            double newLikelihood = calculateLikelihood(newLat, newLon, newDep, lagTable, usedIdx);
            
            // Metropolis acceptance criterion
            double acceptanceRatio = Math.exp((newLikelihood - currentLikelihood) / temperature);
            boolean accept = random.nextDouble() < acceptanceRatio;
            
            if (accept) {
                lat = newLat;
                lon = newLon;
                dep = newDep;
                currentLikelihood = newLikelihood;
                accepted++;
            }
            
            // Report convergence information
            if (convergenceCallback != null) {
                // Calculate residual from likelihood (simplified)
                double residual = Math.sqrt(-currentLikelihood);
                convergenceCallback.onResidualUpdate(i, residual);
                convergenceCallback.onLikelihoodUpdate(i, currentLikelihood);
            }
            
            // Store sample (after burn-in)
            if (i >= burnIn) {
                int idx = i - burnIn;
                latSamples[idx] = lat;
                lonSamples[idx] = lon;
                depSamples[idx] = dep;
                likelihoodSamples[idx] = currentLikelihood;
            }
        }
        
        double acceptanceRate = (double) accepted / nSamples;
        logger.info(String.format("MCMC acceptance rate: %.2f%%", acceptanceRate * 100));
        logger.info(String.format("Stored %d samples (after burn-in of %d)", nEffective, burnIn));
        
        // Calculate statistics from samples (after burn-in)
        double meanLat = calculateMean(latSamples, nEffective);
        double meanLon = calculateMean(lonSamples, nEffective);
        double meanDep = calculateMean(depSamples, nEffective);
        
        double stdLat = calculateStd(latSamples, meanLat, nEffective);
        double stdLon = calculateStd(lonSamples, meanLon, nEffective);
        double stdDep = calculateStd(depSamples, meanDep, nEffective);
        
        logger.info(String.format("Sample statistics: mean=(%.6f, %.6f, %.3f), std=(%.6f, %.6f, %.3f)",
            meanLat, meanLon, meanDep, stdLat, stdLon, stdDep));
        
        // Find best sample (maximum likelihood)
        int bestIdx = 0;
        double bestLikelihood = likelihoodSamples[0];
        for (int i = 1; i < nEffective; i++) {
            if (likelihoodSamples[i] > bestLikelihood) {
                bestLikelihood = likelihoodSamples[i];
                bestIdx = i;
            }
        }
        
        // Use best sample as final hypocenter
        double finalLat = latSamples[bestIdx];
        double finalLon = lonSamples[bestIdx];
        double finalDep = depSamples[bestIdx];
        
        logger.info(String.format("Best sample (idx=%d): (%.6f, %.6f, %.3f), likelihood=%.3f",
            bestIdx, finalLat, finalLon, finalDep, bestLikelihood));
        
        // Calculate final residual
        Point finalPoint = new Point(point.getTime(), finalLat, finalLon, finalDep,
            0, 0, 0, 0, point.getFilePath(), "MCMC", point.getCid());
        double[] sWaveTravelTime = travelTime(stationTable, usedIdx, finalPoint);
        double[] travelTimeResidual = differentialTravelTimeResidual(lagTable, sWaveTravelTime);
        double res = standardDeviation(travelTimeResidual);
        
        // Calculate weights from residuals (inverse of absolute residual)
        double[] weight = HypoUtils.residual2weight(travelTimeResidual);
        for (int i = 0; i < weight.length; i++) {
            lagTable[i][3] = weight[i];
        }
        
        // Set results
        point.setLat(finalLat);
        point.setLon(finalLon);
        point.setDep(finalDep);
        point.setResidual(travelTimeResidual); // Store residuals for each differential travel time data
        point.setLagTable(lagTable); // Update lagTable with calculated weights
        // Convert stdLat and stdLon from degrees to km for error bar display
        // elat and elon should be in km (xerr, yerr in MapView are in km)
        double latRad = Math.toRadians(finalLat);
        double stdLatKm = stdLat * getDeg2Km();
        double stdLonKm = stdLon * getDeg2Km() * Math.cos(latRad);
        point.setElat(stdLatKm);
        point.setElon(stdLonKm);
        point.setEdep(stdDep); // stdDep is already in km
        point.setRes(res);
        point.setType("MCMC");
        
        // Update pointsHandler with the modified point (ensure reference is updated)
        pointsHandler.setMainPoint(point);
        
        // Verify point values before writing
        logger.info(String.format("Point values before write: lat=%.6f, lon=%.6f, dep=%.3f, elat=%.3f, elon=%.3f, edep=%.3f, res=%.3f",
            point.getLat(), point.getLon(), point.getDep(), point.getElat(), point.getElon(), point.getEdep(), point.getRes()));
        
        try {
            pointsHandler.writeDatFile(outFile, codeStrings);
            logger.info("MCMC location completed for: " + fileName);
            System.out.println("MCMC location completed for: " + fileName);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to write output file in MCMC mode:\n");
            errorMsg.append("  Output file: ").append(outFile).append("\n");
            errorMsg.append("  Input file: ").append(datFile).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
            logger.severe(errorMsg.toString());
            throw new RuntimeException("Failed to write output file: " + outFile, e);
        }

        logger.info(String.format("%s %.3f %.3f %.3f %.3f %.3f %.3f %.3f", 
            point.getTime(), finalLon, finalLat, finalDep, stdLon, stdLat, stdDep, res));
    }

    /**
     * Calculates the log-likelihood for a given hypocenter location.
     * Uses negative sum of squared residuals as the likelihood.
     * 
     * @param lat the latitude in degrees
     * @param lon the longitude in degrees
     * @param dep the depth in km
     * @param lagTable the lag time table
     * @param usedIdx the indices of stations to use
     * @return the log-likelihood value
     */
    private double calculateLikelihood(double lat, double lon, double dep,
                                      double[][] lagTable, int[] usedIdx) {
        try {
            Point testPoint = new Point("", lat, lon, dep, 0, 0, 0, 0, "", "", -999);
            double[] sWaveTravelTime = travelTime(stationTable, usedIdx, testPoint);
            double[] residual = differentialTravelTimeResidual(lagTable, sWaveTravelTime);
            
            // Calculate sum of squared residuals
            double sumSqResidual = 0.0;
            for (double r : residual) {
                sumSqResidual += r * r;
            }
            
            // Return negative sum (higher likelihood for smaller residuals)
            return -sumSqResidual;
        } catch (TauModelException e) {
            logger.warning("Failed to calculate travel time: " + e.getMessage());
            return Double.NEGATIVE_INFINITY;
        }
    }

    /**
     * Calculates the differential travel time residual between observed and calculated travel times.
     * 
     * @param lagTable the lag table containing the observed travel times
     * @param sWaveTravelTime the calculated travel times
     * @return an array containing the differential travel time residuals
     */
    private static double[] differentialTravelTimeResidual(double[][] lagTable, double[] sWaveTravelTime) {
        double[] diffTime = new double[lagTable.length];
        for (int i = 0; i < lagTable.length; i++) {
            double sTimeK = sWaveTravelTime[(int) lagTable[i][0]];
            double sTimeL = sWaveTravelTime[(int) lagTable[i][1]];
            double calcTime = sTimeL - sTimeK;
            diffTime[i] = lagTable[i][2] - calcTime; // O-C time
        }
        return diffTime;
    }

    /**
     * Calculates the standard deviation of an array of values.
     * 
     * @param data the array of values
     * @return the standard deviation
     */
    private static double standardDeviation(double[] data) {
        if (data.length == 0) return 0.0;
        
        double sum = 0.0, standardDeviation = 0.0;
        int length = data.length;

        for (double num : data) {
            sum += num;
        }
        double mean = sum / length;

        for (double num : data) {
            standardDeviation += Math.pow(num - mean, 2);
        }
        return Math.sqrt(standardDeviation / length);
    }

    /**
     * Calculates the mean of an array.
     * 
     * @param data the array of values
     * @param n the number of elements to use
     * @return the mean value
     */
    private static double calculateMean(double[] data, int n) {
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += data[i];
        }
        return sum / n;
    }

    /**
     * Calculates the standard deviation of an array.
     * 
     * @param data the array of values
     * @param mean the mean value
     * @param n the number of elements to use
     * @return the standard deviation
     */
    private static double calculateStd(double[] data, double mean, int n) {
        double sumSq = 0.0;
        for (int i = 0; i < n; i++) {
            double diff = data[i] - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / n);
    }
}

