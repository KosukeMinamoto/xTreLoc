package com.treloc.hypotd;

import java.io.IOException;
import java.util.logging.Logger;

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

public class HypoGridSearch extends HypoUtils {
	private static final Logger logger = Logger.getLogger("com.treloc.hypotd");
	private double[][] stationTable;
	private String[] codeStrings;
	private double hypBottom, stnBottom, threshold;
	private int numGrid;

	public HypoGridSearch (ConfigLoader appConfig) throws TauModelException {
		super(appConfig);
		this.codeStrings = appConfig.getCodeStrings();
		this.stationTable = appConfig.getStationTable();
		this.numGrid = appConfig.getNumGrid();
		this.hypBottom = appConfig.getHypBottom();
		this.stnBottom = appConfig.getStnBottom();
		this.threshold = appConfig.getThreshold();
		try {
			setUpOutputDirectory(appConfig);
		} catch (IOException e) {
			logger.severe("Failed to set up output directory: " + e.getMessage());
			throw new RuntimeException("Failed to set up output directory", e);
		}
	}

	/**
	 * Start the grid search algorithm.
	 * @param datFile the input data file
	 * @param outFile the output data file
	 * @throws TauModelException if an error occurs during the calculation
	 */
	public void start( String datFile, String outFile ) throws TauModelException {
		PointsHandler dataHandler = new PointsHandler();
		dataHandler.readDatFile(datFile, codeStrings, threshold);

		Point point = dataHandler.getMainPoint();
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

		double[] travelTimeResidual = new double[0];
		for (int stage = 0; stage < 3; stage++) {
			double rangeFactor = Math.pow(0.5, stage);
			double[] latGrids = generageRandomGrid(lat - latRange * rangeFactor, lat + latRange * rangeFactor, numGrid);
			double[] lonGrids = generageRandomGrid(lon - lonRange * rangeFactor, lon + lonRange * rangeFactor, numGrid);
			double[] depGrids = generageRandomGrid(Math.max(stnBottom, dep - depRange * rangeFactor), Math.min(hypBottom, dep + depRange * rangeFactor), numGrid);

			for (int i = 0; i < numGrid; i++) {
				Point pointNew = new Point(time, latGrids[i], lonGrids[i], depGrids[i], 0, 0, 0, 0, "", "", -999);
				double[] sWaveTravelTime = travelTime(stationTable, usedIdx, pointNew);
				travelTimeResidual = differentialTravelTimeResidual(lagTable, sWaveTravelTime);
				double res2 = standardDeviation(travelTimeResidual);
				if (res2 < res) {
					res = res2;
					lon = pointNew.getLon();
					lat = pointNew.getLat();
					dep = pointNew.getDep();
				}
			}
		}

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
		dataHandler.writeDatFile(outFile, codeStrings);

		logger.info(String.format("%s %.3f %.3f %.3f %.3f %.3f %.3f %.3f", time, lon, lat, dep, elon, elat, edep, res));
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
