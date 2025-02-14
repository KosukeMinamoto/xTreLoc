package com.treloc.hypotd;

import edu.sc.seis.TauP.TauModelException;

/*
 * HypoGridSearch.java
 * @author: K.M.
 * @date: 2025/02/14
 * @version: 0.1
 * @description: The class is used to calculate the hypocenter location 
 * using grid search algorithm.
 */

public class HypoGridSearch extends HypoUtils {
	private double[][] stationTable;
	private String[] codeStrings;
	private double hypBottom, stnBottom, threshold;
	private int numGrid;

	public HypoGridSearch (ConfigLoader appConfig) {
		super(appConfig);
		this.codeStrings = appConfig.getCodeStrings();
		this.stationTable = appConfig.getStationTable();
		this.numGrid = appConfig.getNumGrid();
		this.hypBottom = appConfig.getHypBottom();
		this.stnBottom = appConfig.getStnBottom();
		this.threshold = appConfig.getThreshold();
	}

	public void start( String datFile, String outFile ) throws TauModelException {
		PointsHandler dataHandler = new PointsHandler();
		dataHandler.readDatFile(datFile, codeStrings, threshold);

		Point point = dataHandler.getMainPoint();
		double[][] lagTable = point.getLagTable();
		int[] usedIdx = point.getUsedIdx();
		double lat = point.getLat();
		double lon = point.getLon();
		double dep = point.getDep();
		double res = point.getRes();

		double[] latLim = getMinMax(stationTable, 0); // latitude
		double[] lonLim = getMinMax(stationTable, 1); // longitude
		// 3 stages of focused random search
		double[] dtt = new double[0];
		for (int stage = 0; stage < 3; stage++) {
			double rangeFactor = Math.pow(0.5, stage);
			double[] latGrids = generageRandomGrid(latLim[0] - (latLim[1] - latLim[0]) * rangeFactor, latLim[1] - (latLim[1] - latLim[0]) * rangeFactor, numGrid);
			double[] lonGrids = generageRandomGrid(lonLim[0] - (lonLim[1] - lonLim[0]) * rangeFactor, lonLim[1] - (lonLim[1] - lonLim[0]) * rangeFactor, numGrid);
			double[] depGrids = generageRandomGrid(Math.max(stnBottom, dep - 10 * rangeFactor), Math.min(hypBottom, dep + 10 * rangeFactor), numGrid);

			for (int i = 0; i < numGrid; i++) {
				double[] hyp = new double[]{lonGrids[i], latGrids[i], depGrids[i]};
				double[] tts = travelTime(stationTable, usedIdx, hyp);
				dtt = differentialTravelTimeResidual(lagTable, tts);
				double res2 = standardDeviation(dtt);
				if (res2 < res) {
					res = res2;
					lon = hyp[0];
					lat = hyp[1];
					dep = hyp[2];
				}
			}
		}

		double[] weight = residual2weight(dtt);
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
	}

	/**
	 * Get the minimum and maximum values of a specific row in a 2D array.
	 *
	 * @param data the 2D array to analyze
	 * @param numRow the index of the row to analyze
	 * @return an array containing the minimum and maximum values of the specified row
	 */
	private static double[] getMinMax(double[][] data, int numRow) {
		double max = -Double.MAX_VALUE;
		double min = Double.MAX_VALUE;
		for (int i = 0; i < data.length; i++) {
			double tmp = data[i][numRow];
			if (tmp > max) {
				max = tmp;
			}
			if (tmp < min) {
				min = tmp;
			}
		}
		return new double[]{ min, max };
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
	 * @param tts the travel times
	 * @return an array containing the differential travel time residuals
	 */
	private static double[] differentialTravelTimeResidual(double[][] lagTable, double[] tts) {
		double[] diffTime = new double[lagTable.length];
		for (int i = 0; i < lagTable.length; i++) {
			double sTimeK = tts[(int) lagTable[i][0]];
			double sTimeL = tts[(int) lagTable[i][1]];
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
