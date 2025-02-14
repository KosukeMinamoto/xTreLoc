package com.treloc.hypotd;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.ml.clustering.Cluster;

/*
 * HypoTripleDiff
 * @author: K.M.
 * @date: 2025/02/14
 * @version: 0.1
 * @description: The class is used to calculate the triple difference.
 */

public class HypoTripleDiff extends HypoUtils {
	private String catalogFile;
	private double[][] stationTable;
	private String[] codeStrings;
	private SpatialClustering spatialCls;

	public HypoTripleDiff (ConfigLoader appConfig) {
		super(appConfig);
		catalogFile = appConfig.getCatalogFile();
		stationTable = appConfig.getStationTable();
		codeStrings = appConfig.getCodeStrings();
		spatialCls = new SpatialClustering(appConfig);
	}

	public void start () {
		int clusterId = 1;
		while (true) { // Loop for each cluster
			Cluster<Point> clsPts = spatialCls.loadPointsFromCatalog(catalogFile, false, clusterId);
			if (clsPts.getPoints().isEmpty()) {
				break;
			}
			List<Point> points = clsPts.getPoints();

			Cluster<Point> cluster = new Cluster<>();
			points.forEach(cluster::addPoint);
			Object[] tripDiff = readTripleDiff(clusterId);
			for (int i = 0; i < 10; i++) {
				Object[][] partialTbl = createPartialTblArray(cluster);
				Object[] dG = matrixDG(tripDiff, cluster, partialTbl, 100.0);
				double[] d = (double[]) dG[0];
				RealMatrix G = (RealMatrix) dG[1];

				OpenMapRealMatrix GOpenMap = (OpenMapRealMatrix) G;
				ScipyLSQR.LSQRResult result = ScipyLSQR.lsqr(GOpenMap, d, 0, 1000, 1e-10, 1e-10, 1000, true, false, null);

				// Update the cluster points
				double[] dm = result.x;
				int numPoints = points.size();
				int numDims = 3;
				for (int j = 0; j < numPoints; j++) {
					Point point = points.get(j);
					point.setLon(point.getLon() + dm[j * numDims]);
					point.setLat(point.getLat() + dm[j * numDims + 1]);
					point.setDep(point.getDep() + dm[j * numDims + 2]);
				}
				cluster = new Cluster<>();
				points.forEach(cluster::addPoint);
			}

			// spatialCls.writePointsToFile(points, catalogFile);
			PointsHandler pointsHandler = new PointsHandler();
			for (Point point : points) {
				pointsHandler.setMainPoint(point);
				// pointsHandler.writeDatFile(point.getFilePath(), codeStrings);
			}
			String outputFile = catalogFile.replace(".list", "_TRD.list");
			spatialCls.writePointsToFile(points, outputFile);
			clusterId++;
		}
	}

	private Object[] readTripleDiff(int clusterId) {
		String filePath = "triple_diff_" + clusterId + ".csv";
		int fileLength = 0;
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			while (reader.readLine() != null) {
				fileLength++;
			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + e.getMessage());
		}
		int[] ev0 = new int[fileLength];
		int[] ev1 = new int[fileLength];
		int[] st0 = new int[fileLength];
		int[] st1 = new int[fileLength];
		double[] diff = new double[fileLength];
		double[] dist = new double[fileLength];
		// int[] cid = new int[fileLength];

		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			for (int i = 0; i < fileLength; i++) {
				if (i==0) { // Skip header
					br.readLine();
					continue;
				}

				line = br.readLine();
				String[] parts = line.split(",");
				ev0[i] = Integer.parseInt(parts[0]);
				ev1[i] = Integer.parseInt(parts[1]);
				st0[i] = Integer.parseInt(parts[2]);
				st1[i] = Integer.parseInt(parts[3]);
				diff[i] = Double.parseDouble(parts[4]);
				dist[i] = Double.parseDouble(parts[5]);
				i++;
			}
		} catch (IOException e) {
			System.err.println("Error reading triple diff file: " + e.getMessage());
		}
		return new Object[]{ev0, ev1, st0, st1, diff, dist};
	}

	/**
	 * Generate the matrix DG
	 * @param trpDiff The triple difference
	 * @param cluster The cluster of points
	 * @param partialTbl The partial table
	 * @param distanceThreshold The distance threshold in km
	 * @return The matrix DG
	 */
	private Object[] matrixDG(Object[] trpDiff, Cluster<Point> cluster, Object[][] partialTbl, double distanceThreshold) {
		int M = cluster.getPoints().size(); // The number of triple-difference
		int N = partialTbl.length;			// The number of events
		double[] d = new double[M];
		OpenMapRealMatrix G = new OpenMapRealMatrix(M, 3 * N);

		int[] eids0 = (int[]) trpDiff[0];
		int[] eids1 = (int[]) trpDiff[1];
		int[] stns0 = (int[]) trpDiff[2];
		int[] stns1 = (int[]) trpDiff[3];
		double[] diffs = (double[]) trpDiff[4];
		double[] dists = (double[]) trpDiff[5];
		// List<Point> points = cluster.getPoints();
		for (int m = 0; m < M; m++) {
			if (dists[m] > distanceThreshold) {
				continue;
			}

			try {
				int eve0 = eids0[m];
				int eve1 = eids1[m];
				int stnk = stns0[m];
				int stnl = stns1[m];

				// double[][] lagTbl0 = (double[][]) points.get(eve1).getLagTable();
				// int[] idx0 = findIndices(lagTbl0, stnk, stnl);
				// double[][] lagTbl1 = (double[][]) points.get(eve2).getLagTable();
				// int[] idx1 = findIndices(lagTbl1, stnk, stnl);

				// if (idx0.length == 0 || idx1.length == 0) {
				// 	continue;
				// } else {
				double[][][] partialTblArray = (double[][][]) partialTbl;
				double cal0 = partialTblArray[eve0][stnl][3] - partialTblArray[eve0][stnk][3];
				double cal1 = partialTblArray[eve1][stnl][3] - partialTblArray[eve1][stnk][3];
				double lagCal = cal1 - cal0;
				double lagObs = diffs[m];
				d[m] = lagObs - lagCal;

				// Partial derivatives matrix
				G.setEntry(m, 3 * eve1, partialTblArray[eve1][stnl][0] - partialTblArray[eve1][stnk][0]);
				G.setEntry(m, 3 * eve1 + 1, partialTblArray[eve1][stnl][1] - partialTblArray[eve1][stnk][1]);
				G.setEntry(m, 3 * eve1 + 2, partialTblArray[eve1][stnl][2] - partialTblArray[eve1][stnk][2]);
				G.setEntry(m, 3 * eve0, -(partialTblArray[eve0][stnl][0] - partialTblArray[eve0][stnk][0]));
				G.setEntry(m, 3 * eve0 + 1, -(partialTblArray[eve0][stnl][1] - partialTblArray[eve0][stnk][1]));
				G.setEntry(m, 3 * eve0 + 2, -(partialTblArray[eve0][stnl][2] - partialTblArray[eve0][stnk][2]));
				System.out.println("G[" + m + "]: " + G.getEntry(m, 3 * eve0) + ", " + G.getEntry(m, 3 * eve0 + 1) + ", " + G.getEntry(m, 3 * eve0 + 2));
			} catch (Exception e) {
				System.out.println("> Error processing line: " + e.getMessage());
				continue;
			}
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
					partialTbl[i][j][0] = dtdr[j][0];
					partialTbl[i][j][1] = dtdr[j][1];
					partialTbl[i][j][2] = dtdr[j][2];
					partialTbl[i][j][3] = trvTime[j];
				}
			} catch (Exception e) {
				System.out.println("> Error creating partial table: " + e.getMessage());
			} finally {
				i++;
			}
		}
		return partialTbl;
	}
}
