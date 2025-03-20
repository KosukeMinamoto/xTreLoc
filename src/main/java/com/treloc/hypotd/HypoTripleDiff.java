package com.treloc.hypotd;

import java.nio.file.Path;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.IntStream;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.ml.clustering.Cluster;

import edu.sc.seis.TauP.TauModelException;

import java.util.ArrayList;
import java.util.Collections;

/**
 * HypoTripleDiff
 * This class is used to calculate the triple difference.
 * 
 * @author K.M.
 * @since 2025-02-26
 * @version 0.1
 */

public class HypoTripleDiff extends HypoUtils {
	private static final Logger logger = Logger.getLogger("com.treloc.hypotd");
	private String catalogFile;
	private double[][] stationTable;
	private double stnBottom;
	private double hypBottom;
	private String[] codeStrings;
	private SpatialClustering spatialCls;
	private boolean showLSQR;
	private Path outDir;
	private int[] iterNumArray;
	private int[] distKmArray;
	private int[] dampFactArray;

	public HypoTripleDiff (ConfigLoader appConfig) throws TauModelException {
		super(appConfig);
		catalogFile = appConfig.getCatalogFile(appConfig.getMode());
		stationTable = appConfig.getStationTable();
		codeStrings = appConfig.getCodeStrings();
		stnBottom = appConfig.getStnBottom();
		hypBottom = appConfig.getHypBottom();
		spatialCls = new SpatialClustering(appConfig);
		if (Level.INFO.intValue() <= Level.parse(appConfig.getLogLevel()).intValue()) {
			showLSQR = true;
		}

		iterNumArray = appConfig.getTrpIterNum();
		distKmArray = appConfig.getTrpDistKm();
		dampFactArray = appConfig.getTrpDampFact();
		if (iterNumArray.length != distKmArray.length || iterNumArray.length != dampFactArray.length) {
			throw new IllegalArgumentException("The length of iterNum, distKm, and dampFact must be the same.");
		}

		try {
			setUpOutputDirectory(appConfig);
		} catch (IOException e) {
			logger.severe("Failed to set up output directory: " + e.getMessage());
			throw new RuntimeException("Failed to set up output directory", e);
		}
		outDir = appConfig.getOutDir();
	}

	public void start () {
		String outputFile = catalogFile.replace(".list", "_trd.list");

		// Output noise points beforehand
		Cluster<Point> clsPts = spatialCls.loadPointsFromCatalog(catalogFile, false, -1);
		List<Point> relocatedPoints = clsPts.getPoints();

		// Clustered Points
		int clusterId = 0;
		while (true) { // Loop for each cluster
			clsPts = spatialCls.loadPointsFromCatalog(catalogFile, true, clusterId);
			if (clsPts.getPoints().isEmpty()) {
				break;
			}
			List<Point> points = clsPts.getPoints();

			// Map all event indices (including ref & err) to column indices in matrix G for
			// target events only
			// targetMap[i] returns the column index (divided by 3) in matrix G
			// for event i if it's a target, or -1 if event i is not a target
			int[] targMap = new int[points.size()];
			int numTarget = 0;
			for (int i = 0; i < points.size(); i++) {
				if (points.get(i).getType().equals("ERR") || points.get(i).getType().equals("REF")) {
					targMap[i] = -1;
				} else {
					targMap[i] = numTarget;
					numTarget++;
				}
			}

			Cluster<Point> cluster = new Cluster<>();
			points.forEach(cluster::addPoint);
			Object[] tripDiff = readTripleDiff(clusterId);
			for (int i = 0; i < iterNumArray.length; i++) { // Loop for each iteration
				int distKm = distKmArray[i];
				int iterNum = iterNumArray[i];
				int dampFact = dampFactArray[i];
				Object[] filteredTripDiff = filterTripDiffByDistance(tripDiff, distKm);

				for (int j = 0; j < iterNum; j++) { // Loop in each iteration
					double[][][] partialTbl = createPartialTblArray(cluster);
					Object[] dG = matrixDG(filteredTripDiff, cluster, partialTbl, distKm, targMap);
					double[] d = (double[]) dG[0];
					OpenMapRealMatrix G = (OpenMapRealMatrix) dG[1];

					// SparseMatrixGenerator.saveMatrixToCSV(G, "matrix_G.csv");
					// SparseMatrixGenerator.saveVectorToCSV(d, "vector_d.csv");

					ScipyLSQR.LSQRResult result = ScipyLSQR.lsqr(
						G,
						d,
						dampFact,
						1e-6,
						1e-6,
						1e8,
						1000,
						showLSQR,
						false,
						null);

					// Update the cluster points
					double[] dm = result.x;

					// Calculate median adjustments
					List<Double> dlonList = new ArrayList<>();
					List<Double> dlatList = new ArrayList<>();
					List<Double> ddepList = new ArrayList<>();
					for (int k = 0; k < numTarget; k++) {
						dlonList.add(dm[k * 3]);
						dlatList.add(dm[k * 3 + 1]);
						ddepList.add(dm[k * 3 + 2]);
					}
					double medianDlon = calculateMedian(dlonList);
					double medianDlat = calculateMedian(dlatList);
					double medianDdep = calculateMedian(ddepList);

					for (int k = 0; k < points.size(); k++) {
						if (targMap[k] == -1) {
							continue;
						}
						Point point = points.get(k);
						double newLon = point.getLon() + dm[targMap[k] * 3] 	- medianDlon;
						double newLat = point.getLat() + dm[targMap[k] * 3 + 1] - medianDlat;
						double newDep = point.getDep() + dm[targMap[k] * 3 + 2] - medianDdep;

						if (newDep < stnBottom || newDep > hypBottom) {
							point.setType("ERR");
							targMap[k] = -1;
						} else {
							point.setLon(newLon);
							point.setLat(newLat);
							point.setDep(newDep);
						}
					}
					cluster = new Cluster<>();
					points.forEach(cluster::addPoint);
				}
			}

			for (Point point : points) {
				PointsHandler pointsHandler = new PointsHandler();
				pointsHandler.setMainPoint(point);
				if (!point.getType().equals("ERR") && !point.getType().equals("REF")) {
					point.setType("TRD");
				}
				String outFilePath = outDir.resolve(point.getFileName()).toString();
				pointsHandler.writeDatFile(outFilePath, codeStrings);
				relocatedPoints.add(point);
			}
			spatialCls.writePointsToFile(relocatedPoints, outputFile);
			clusterId++;
		}
	}

	private Object[] readTripleDiff(int clusterId) {
		// While distances in triple_diff.csv are in ascending order,
		// the neighborhood distances for triple difference get smaller,
		// so we use reversed distKmArray for comparison btwn these two.
		int[] invDistKmArray = Arrays.copyOf(distKmArray, distKmArray.length);
		Arrays.sort(invDistKmArray);
		int[] invLimitIdxArray = new int[invDistKmArray.length];

		String filePath = "triple_diff_" + clusterId + ".csv";
		List<Integer> ev0List = new ArrayList<>();
		List<Integer> ev1List = new ArrayList<>();
		List<Integer> st0List = new ArrayList<>();
		List<Integer> st1List = new ArrayList<>();
		List<Double> diffSecList = new ArrayList<>();
		List<Double> distKmList = new ArrayList<>();

		int limIdx = 0;
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			boolean isFirstLine = true;
			while ((line = br.readLine()) != null) {
				if (isFirstLine) { // Skip header
					isFirstLine = false;
					continue;
				}

				String[] parts = line.split(",");
				ev0List.add(Integer.parseInt(parts[0]));
				ev1List.add(Integer.parseInt(parts[1]));
				st0List.add(Integer.parseInt(parts[2]));
				st1List.add(Integer.parseInt(parts[3]));
				diffSecList.add(Double.parseDouble(parts[4]));
				double dist = Double.parseDouble(parts[5]);
				distKmList.add(dist);
				if (dist > invDistKmArray[limIdx]) {
					invLimitIdxArray[limIdx] = ev0List.size() - 1;
					limIdx++;
					if (limIdx == invLimitIdxArray.length) {
						break;
					}
				}
			}
		} catch (IOException e) {
			logger.severe("Error: Reading " + filePath + ": " + e.getMessage());
		}

		return new Object[]{
			ev0List.stream().mapToInt(Integer::intValue).toArray(),
			ev1List.stream().mapToInt(Integer::intValue).toArray(),
			st0List.stream().mapToInt(Integer::intValue).toArray(),
			st1List.stream().mapToInt(Integer::intValue).toArray(),
			diffSecList.stream().mapToDouble(Double::doubleValue).toArray(),
			distKmList.stream().mapToDouble(Double::doubleValue).toArray()
		};
	}

	/**
	 * Generate the matrix DG
	 * @param trpDiff The triple difference
	 * @param cluster The cluster of points
	 * @param partialTbl The partial table
	 * @param distanceThreshold The distance threshold in km
	 * @param targMap The map of target points
	 * @return The matrix DG
	 */
	private Object[] matrixDG(Object[] trpDiff, Cluster<Point> cluster, double[][][] partialTbl, double distanceThreshold, int[] targMap) {
		int[] eids0 = (int[]) trpDiff[0];
		int[] eids1 = (int[]) trpDiff[1];
		int[] stns0 = (int[]) trpDiff[2];
		int[] stns1 = (int[]) trpDiff[3];
		double[] diffs = (double[]) trpDiff[4];
		// double[] dists = (double[]) trpDiff[5];

		int M = eids0.length; 								 // The number of triple-difference
		int N = Arrays.stream(targMap).max().getAsInt() + 1; // The number of target points

		double[] d = new double[M];
		OpenMapRealMatrix G = new OpenMapRealMatrix(M, 3 * N);

		for (int m = 0; m < M; m++) {
			int eve0 = eids0[m];
			int eve1 = eids1[m];
			int stnk = stns0[m];
			int stnl = stns1[m];

			// Matrix G
			int nCol0 = targMap[eve0];
			int nCol1 = targMap[eve1];
			if (nCol0 == -1 && nCol1 == -1) {
				continue;
			}

			if (nCol1 != -1) {
				G.setEntry(m, 3 * nCol1,	 partialTbl[eve1][stnl][0] - partialTbl[eve1][stnk][0]);
				G.setEntry(m, 3 * nCol1 + 1, partialTbl[eve1][stnl][1] - partialTbl[eve1][stnk][1]);
				G.setEntry(m, 3 * nCol1 + 2, partialTbl[eve1][stnl][2] - partialTbl[eve1][stnk][2]);
			}

			if (nCol0 != -1) {
				G.setEntry(m, 3 * nCol0,	 -(partialTbl[eve0][stnl][0] - partialTbl[eve0][stnk][0]));
				G.setEntry(m, 3 * nCol0 + 1, -(partialTbl[eve0][stnl][1] - partialTbl[eve0][stnk][1]));
				G.setEntry(m, 3 * nCol0 + 2, -(partialTbl[eve0][stnl][2] - partialTbl[eve0][stnk][2]));
			}

			// Vector d
			double cal0 = partialTbl[eve0][stnl][3] - partialTbl[eve0][stnk][3];
			double cal1 = partialTbl[eve1][stnl][3] - partialTbl[eve1][stnk][3];
			double lagCal = cal1 - cal0;
			double lagObs = diffs[m];
			d[m] = lagObs - lagCal;
		}

		return new Object[] { d, G };
	}

	/**
	 * Generate the partial table for the cluster
	 * @param cluster The cluster of points
	 * @return The partial table. 
	 * The first dimension is the event, the second is the station,
	 * and the third is the partial derivatives of x, y, z, and travel time.
	 */
	private double[][][] createPartialTblArray(Cluster<Point> cluster) {
		List<Point> points = cluster.getPoints();
		int numEvents = points.size();
		int numStations = stationTable.length;
		double[][][] partialTbl = new double[numEvents][numStations][4];

		int i = 0;
		for (Point point : points) {
			int[] usedIdx = IntStream.range(0, numStations).toArray();
			RealVector hypoVector = point.getVector();
			try {
				Object[] tmp = partialDerivativeMatrix(stationTable, usedIdx, hypoVector);
				double[][] dtdr = (double[][]) tmp[0];
				double[] trvTime = (double[]) tmp[1];
				for (int j = 0; j < numStations; j++) {
					partialTbl[i][j][0] = dtdr[j][0]; // lon
					partialTbl[i][j][1] = dtdr[j][1]; // lat
					partialTbl[i][j][2] = dtdr[j][2]; // dep
					partialTbl[i][j][3] = trvTime[j]; // trvTime
				}
			} catch (Exception e) {
				logger.severe("Error: Creating partial table: " + e.getMessage());
				points.get(i).setType("ERR");
			} finally {
				i++;
			}
		}
		return partialTbl;
	}

	/**
	 * Calculate the median of a list of values
	 * @param values The list of values
	 * @return The median of the values
	 */
	private double calculateMedian(List<Double> values) {
		Collections.sort(values);
		int size = values.size();
		if (size % 2 == 0) {
			return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
		} else {
			return values.get(size / 2);
		}
	}

	/**
	 * Filter the triple difference by distance
	 * @param tripDiff The triple difference
	 * @param distKm The distance threshold in km
	 * @return The filtered triple difference
	 */
	private Object[] filterTripDiffByDistance(Object[] tripDiff, int distKm) {
		int[] ev0 = (int[]) tripDiff[0];
		int[] ev1 = (int[]) tripDiff[1];
		int[] st0 = (int[]) tripDiff[2];
		int[] st1 = (int[]) tripDiff[3];
		double[] diffSec = (double[]) tripDiff[4];
		double[] distKmArray = (double[]) tripDiff[5];

		List<Integer> ev0List = new ArrayList<>();
		List<Integer> ev1List = new ArrayList<>();
		List<Integer> st0List = new ArrayList<>();
		List<Integer> st1List = new ArrayList<>();
		List<Double> diffSecList = new ArrayList<>();
		List<Double> distKmList = new ArrayList<>();

		for (int i = 0; i < distKmArray.length; i++) {
			if (distKmArray[i] < distKm) {
				ev0List.add(ev0[i]);
				ev1List.add(ev1[i]);
				st0List.add(st0[i]);
				st1List.add(st1[i]);
				diffSecList.add(diffSec[i]);
				distKmList.add(distKmArray[i]);
			}
		}

		return new Object[]{
			ev0List.stream().mapToInt(Integer::intValue).toArray(),
			ev1List.stream().mapToInt(Integer::intValue).toArray(),
			st0List.stream().mapToInt(Integer::intValue).toArray(),
			st1List.stream().mapToInt(Integer::intValue).toArray(),
			diffSecList.stream().mapToDouble(Double::doubleValue).toArray(),
			distKmList.stream().mapToDouble(Double::doubleValue).toArray()
		};
	}

	public boolean hasMoreClusters(int clusterId) {
		Cluster<Point> clsPts = spatialCls.loadPointsFromCatalog(catalogFile, true, clusterId);
		return !clsPts.getPoints().isEmpty();
	}
}
