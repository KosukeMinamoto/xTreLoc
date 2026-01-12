package com.treloc.xtreloc.solver;

import java.io.IOException;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
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

    public HypoGridSearch(AppConfig appConfig) throws TauModelException {
        super(appConfig);
        this.hypBottom = appConfig.hypBottom;
        
        JsonNode grdSolver = appConfig.solver.get("GRD");
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
        
        logger.info(String.format("Grid search parameters: totalGrids=%d, numFocus=%d, gridsPerFocus=%d",
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
        logger.info("Starting grid search for: " + fileName);
        System.out.println("Starting grid search for: " + fileName);
        
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
            logger.severe(errorMsg.toString());
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
        double initialRes = standardDeviation(travelTimeResidual);
        
        if (res <= 0.0 || res >= 999.0) {
            res = initialRes;
            logger.info(String.format("Initial residual calculated: %.6f (input res was %.6f)", res, point.getRes()));
        } else {
            res = initialRes;
            logger.info(String.format("Initial residual calculated: %.6f (input res was %.6f, using calculated value)", res, point.getRes()));
        }
        for (int focus = 0; focus < numFocus; focus++) {
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Grid search interrupted by user");
                throw new RuntimeException("Grid search was interrupted");
            }
            
            double rangeFactor = Math.pow(0.5, focus);
            double[] latGrids = generageRandomGrid(
                lat - latRange * rangeFactor, 
                lat + latRange * rangeFactor, 
                gridsPerFocus);
            double[] lonGrids = generageRandomGrid(
                lon - lonRange * rangeFactor, 
                lon + lonRange * rangeFactor, 
                gridsPerFocus);
            double[] depGrids = generageRandomGrid(
                Math.max(stnBottom, dep - depRange * rangeFactor), 
                Math.min(hypBottom, dep + depRange * rangeFactor), 
                gridsPerFocus);

            for (int i = 0; i < gridsPerFocus; i++) {
                // Check for interruption periodically
                if (i % 100 == 0 && Thread.currentThread().isInterrupted()) {
                    logger.info("Grid search interrupted by user");
                    throw new RuntimeException("Grid search was interrupted");
                }
                Point pointNew = new Point(time, latGrids[i], lonGrids[i], depGrids[i], 
                    0, 0, 0, 0, "", "", -999);
                double[] newSWaveTravelTime = travelTime(this.stationTable, usedIdx, pointNew);
                travelTimeResidual = differentialTravelTimeResidual(lagTable, newSWaveTravelTime);
                double res2 = standardDeviation(travelTimeResidual);
                if (res2 < res) {
                    res = res2;
                    lon = pointNew.getLon();
                    lat = pointNew.getLat();
                    dep = pointNew.getDep();
                }
            }
        }

        Point bestPoint = new Point(time, lat, lon, dep, 0, 0, 0, 0, "", "", -999);
        double[] bestSWaveTravelTime = travelTime(this.stationTable, usedIdx, bestPoint);
        travelTimeResidual = differentialTravelTimeResidual(lagTable, bestSWaveTravelTime);
        res = standardDeviation(travelTimeResidual);
        
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
            logger.info("Grid search completed for: " + fileName);
            System.out.println("Grid search completed for: " + fileName);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to write output file in GRD mode:\n");
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
    private static double[] generageRandomGrid(double minVal, double maxVal, int nGrids) {
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

    /**
     * Calculate the standard deviation of an array of values.
     *
     * @param data the array of values to calculate the standard deviation of
     * @return the standard deviation of the array
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
}

