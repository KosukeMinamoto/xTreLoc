package com.treloc.xtreloc.solver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.StationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.treloc.xtreloc.util.BatchExecutorFactory;
import com.treloc.xtreloc.util.SolverLogger;
import edu.sc.seis.TauP.TauModelException;

/**
 * HypoGridSearch
 * This class is used to calculate the hypocenter location 
 * using grid search algorithm.
 * 
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 */
public class HypoGridSearch extends SolverBase {
    private static final Logger logger = Logger.getLogger(HypoGridSearch.class.getName());
    private double hypBottom;
    private int totalGrids;
    private int numFocus;

    private ConvergenceCallback convergenceCallback;

    /**
     * Sets the convergence callback for reporting residual updates to the GUI.
     *
     * @param callback the callback to use, or null to disable
     */
    public void setConvergenceCallback(ConvergenceCallback callback) {
        this.convergenceCallback = callback;
    }

    public HypoGridSearch(AppConfig appConfig) throws TauModelException {
        super(appConfig);
        this.hypBottom = appConfig.hypBottom;
        initGrdParams(appConfig);
    }

    /**
     * Constructs with pre-loaded station data (for parallel batch to avoid concurrent file I/O).
     */
    public HypoGridSearch(AppConfig appConfig, StationRepository stationRepo) throws TauModelException {
        super(appConfig, stationRepo);
        this.hypBottom = appConfig.hypBottom;
        initGrdParams(appConfig);
    }

    private void initGrdParams(AppConfig appConfig) {
        JsonNode grdSolver = appConfig.getParams() != null ? appConfig.getParams().get("GRD") : null;
        if (grdSolver != null) {
            if (grdSolver.has("totalGrids") && grdSolver.has("numFocus")) {
                this.totalGrids = grdSolver.get("totalGrids").asInt();
                this.numFocus = grdSolver.get("numFocus").asInt();
            } else if (grdSolver.has("numGrid")) {
                this.totalGrids = grdSolver.get("numGrid").asInt();
                this.numFocus = 1;
            } else {
                this.totalGrids = 300;
                this.numFocus = 3;
            }
        } else {
            this.totalGrids = 300;
            this.numFocus = 3;
        }
        if (this.totalGrids < 1) {
            logger.warning("GRD totalGrids must be >= 1; was " + this.totalGrids + ", using 300.");
            SolverLogger.warning("GRD: totalGrids invalid; using 300.");
            this.totalGrids = 300;
        }
        if (this.numFocus < 1) {
            logger.warning("GRD numFocus must be >= 1; was " + this.numFocus + ", using 1.");
            SolverLogger.warning("GRD: numFocus invalid; using 1.");
            this.numFocus = 1;
        }
        SolverLogger.fine(String.format("GRD: Grid search parameters: totalGrids=%d, numFocus=%d, gridsPerFocus=%d",
            totalGrids, numFocus, totalGrids / numFocus));
    }

    /**
     * Start the grid search algorithm.
     * @param datFile the input data file
     * @param outFile the output data file
     * @throws TauModelException if an error occurs during the calculation
     */
    public void start(String datFile, String outFile) throws TauModelException {
        String fileName = new java.io.File(datFile).getName();
        SolverLogger.info("GRD: Starting. File=" + fileName);
        
        Point point;
        try {
            point = loadPointFromDatFile(datFile);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to read dat file in GRD mode:\n");
            errorMsg.append("  Input file: ").append(datFile).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
            SolverLogger.severe("GRD: " + errorMsg.toString());
            throw new RuntimeException("Failed to read dat file: " + datFile, e);
        }
        
        PointsHandler dataHandler = new PointsHandler();
        dataHandler.setMainPoint(point);
        double[][] lagTable = point.getLagTable();
        int[] usedIdx = point.getUsedIdx();
        String time = point.getTime();
        double lat = point.getLat();
        double lon = point.getLon();
        double dep = point.getDep();
        double elat = point.getElat();
        double elon = point.getElon();
        double edep = point.getEdep();
        double res = point.getRes();

        double latRange = 1; // degree
        double lonRange = 1; // degree
        double depRange = 10; // km

        int gridsPerFocus = totalGrids / numFocus;
        
        double[] sWaveTravelTime = travelTime(this.stationTable, usedIdx, point);
        double[] travelTimeResidual = differentialTravelTimeResidual(lagTable, sWaveTravelTime);
        double initialRes = HypoUtils.standardDeviation(travelTimeResidual);
        
        if (res <= 0.0 || res >= 999.0) {
            res = initialRes;
            SolverLogger.fine(String.format("Initial residual calculated: %.6f (input res was %.6f)", res, point.getRes()));
        } else {
            res = initialRes;
            SolverLogger.fine(String.format("Initial residual calculated: %.6f (input res was %.6f, using calculated value)", res, point.getRes()));
        }
        if (convergenceCallback != null) {
            convergenceCallback.onResidualUpdate(0, res);
        }
        int nThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = BatchExecutorFactory.newFixedThreadPoolBounded(
                nThreads, BatchExecutorFactory.suggestedQueueCapacity(totalGrids));
        for (int focus = 0; focus < numFocus; focus++) {
            if (Thread.currentThread().isInterrupted()) {
                executor.shutdownNow();
                SolverLogger.info("GRD: Interrupted by user");
                throw new RuntimeException("Grid search was interrupted");
            }
            
            double rangeFactor = Math.pow(0.5, focus);
            double[] latGrids = generateRandomGrid(
                lat - latRange * rangeFactor, 
                lat + latRange * rangeFactor, 
                gridsPerFocus);
            double[] lonGrids = generateRandomGrid(
                lon - lonRange * rangeFactor, 
                lon + lonRange * rangeFactor, 
                gridsPerFocus);
            double[] depGrids = generateRandomGrid(
                Math.max(stnBottom, dep - depRange * rangeFactor), 
                Math.min(hypBottom, dep + depRange * rangeFactor), 
                gridsPerFocus);

            final double[][] stnTableRef = this.stationTable;
            final int[] usedIdxRef = usedIdx;
            final String timeRef = time;
            List<Callable<double[]>> tasks = new ArrayList<>(gridsPerFocus);
            for (int i = 0; i < gridsPerFocus; i++) {
                final int idx = i;
                tasks.add(() -> {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new RuntimeException("Grid search was interrupted");
                    }
                    Point pointNew = new Point(timeRef, latGrids[idx], lonGrids[idx], depGrids[idx],
                        0, 0, 0, 0, "", "", -999);
                    double[] newSWaveTravelTime = travelTime(stnTableRef, usedIdxRef, pointNew);
                    double[] trLocal = differentialTravelTimeResidual(lagTable, newSWaveTravelTime);
                    double res2 = HypoUtils.standardDeviation(trLocal);
                    return new double[]{ res2, pointNew.getLat(), pointNew.getLon(), pointNew.getDep(), idx };
                });
            }
            List<Future<double[]>> futures;
            try {
                futures = executor.invokeAll(tasks);
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                SolverLogger.info("GRD: Interrupted by user");
                throw new RuntimeException("Grid search was interrupted", e);
            }
            for (int i = 0; i < futures.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    executor.shutdownNow();
                    SolverLogger.info("GRD: Interrupted by user");
                    throw new RuntimeException("Grid search was interrupted");
                }
                double[] row;
                try {
                    row = futures.get(i).get();
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Grid search was interrupted", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    throw new RuntimeException("Grid search task failed", cause);
                }
                double res2 = row[0];
                if (res2 < res) {
                    res = res2;
                    lat = row[1];
                    lon = row[2];
                    dep = row[3];
                    if (convergenceCallback != null) {
                        convergenceCallback.onResidualUpdate(focus * gridsPerFocus + (int) row[4], res);
                    }
                }
            }
        }
        executor.shutdown();

        Point bestPoint = new Point(time, lat, lon, dep, 0, 0, 0, 0, "", "", -999);
        double[] bestSWaveTravelTime = travelTime(this.stationTable, usedIdx, bestPoint);
        travelTimeResidual = differentialTravelTimeResidual(lagTable, bestSWaveTravelTime);
        res = HypoUtils.standardDeviation(travelTimeResidual);
        
        double[] weight = residual2weight(travelTimeResidual);
        for (int i = 0; i < weight.length; i++) {
            lagTable[i][3] = weight[i];
        }

        point.setLat(lat);
        point.setLon(lon);
        point.setDep(dep);
        point.setRes(res);
        point.setElat(0);
        point.setElon(0);
        point.setEdep(0);
        point.setType("GRD");
        
        try {
            dataHandler.writeDatFile(outFile, codeStrings);
            SolverLogger.info("GRD: Completed. File=" + fileName);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to write output file in GRD mode:\n");
            errorMsg.append("  Output file: ").append(outFile).append("\n");
            errorMsg.append("  Input file: ").append(datFile).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
            SolverLogger.severe("GRD: " + errorMsg.toString());
            throw new RuntimeException("Failed to write output file: " + outFile, e);
        }

        SolverLogger.fine(String.format("GRD: %s %.3f %.3f %.3f %.3f %.3f %.3f %.3f", 
            time, lon, lat, dep, elon, elat, edep, res));
    }

    /**
     * Generate a random grid of values between a minimum and maximum value.
     *
     * @param minVal the minimum value of the grid
     * @param maxVal the maximum value of the grid
     * @param nGrids the number of grids to generate
     * @return an array containing the generated grid values
     */
    private static double[] generateRandomGrid(double minVal, double maxVal, int nGrids) {
        double[] grid = new double[nGrids];
        for (int i = 0; i < nGrids; i++) {
            grid[i] = minVal + (maxVal - minVal) * Math.random();
        }
        return grid;
    }

    /**
     * Calculate the differential travel time residual between observed and calculated travel times.
     *
     * @param lagTable the lag table containing the observed travel times
     * @param sWaveTravelTime the travel times
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
}

