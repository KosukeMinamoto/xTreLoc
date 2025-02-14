package com.treloc.hypotd;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.stat.descriptive.rank.Median;

/*
 * SpatialClustering
 * @author: K.M.
 * @date: 2025/02/14
 * @version: 0.1
 * @description: The class is used to perform the spatial clustering.
 */

public class SpatialClustering extends HypoUtils {
	private Point refPoint;
	private int minPts;
	private double eps;
	private String[] codeStrings;
	private double threshold;
	private String catalogFile;

	public SpatialClustering (ConfigLoader appConfig) {
		super(appConfig);
		this.catalogFile = appConfig.getCatalogFile();
	
		double[][] stationTable = appConfig.getStationTable();
		double refLat = new Median().evaluate(stationTable[0]);
		double refLon = new Median().evaluate(stationTable[1]);
		this.refPoint = new Point("", refLat, refLon, 0, 0, 0, 0, 0, "", "REF", -999);
	
		this.threshold = appConfig.getThreshold();
		this.codeStrings = appConfig.getCodeStrings();

		this.minPts = appConfig.getClsPts();
		this.eps = appConfig.getClsEps();

		Cluster<Point> cluster = loadPointsFromCatalog(catalogFile, false);
		boolean hasCid = cluster.getPoints().stream().anyMatch(p -> p.getCid() != -1);

		if (hasCid) {
			System.out.println("> No clustering performed");
			System.out.println("> The number of defined cls.: " + cluster.getPoints().stream().map(Point::getCid).distinct().count());
		} else {
			System.out.println("> CID not set for any point. Proceeding with clustering...");
			List<Cluster<Point>> clusters = runClustering(cluster, refPoint);

			List<Point> points = new ArrayList<>();
			for (Cluster<Point> cls : clusters) {
				points.addAll(cls.getPoints());
			}
			writePointsToFile(points, catalogFile);
		}

		int clusterId = 1;
		while (true) {
			Cluster<Point> clsPts = loadPointsFromCatalog(catalogFile, true, clusterId);
			if (clsPts.getPoints().isEmpty()) {
				break;
			}

			Cluster<Point> cls = new Cluster<>();
			clsPts.getPoints().forEach(cls::addPoint);

			List<Object[]> trpDiff = calcTripleDifferences(cls, clusterId);
			saveTripleDifferences(trpDiff, clusterId);
			clusterId++;
		}
	}

	/**
	 * Runs the clustering process using DBSCAN with distances in kilometers.
	 *
	 * @param catalogFile the path to the catalog file
	 * @param minPts      the minimum number of points to form a cluster
	 * @param refPoints   the list of reference points
	 */
	public List<Cluster<Point>> runClustering(Cluster<Point> clsPts, Point refPoint) {
		List<Point> points = clsPts.getPoints();
		for (Point p : points) {
			p.setKmLat((p.getLat() - refPoint.getLat()) * App.deg2km);
			p.setKmLon((p.getLon() - refPoint.getLon()) * App.deg2km * Math.cos(Math.toRadians(p.getLat())));
		}

		if (eps <= 0) {
			System.out.println("> Negative 'clsEps' (=" + eps + ") is set. Estimating eps...");
			List<Double> kDistances = computeKDistance(points, minPts);
			double estimatedEps = findElbowWithDist(kDistances);
			System.out.println("> Estimated epsilon: " + estimatedEps + " km & min samples: " + minPts);

			KDistanceGraph.displayKDistanceGraph(kDistances, estimatedEps);
			eps = estimatedEps;
		} else {
			System.out.println("> Given epsilon: " + eps + " km & min samples: " + minPts);
		}

		DBSCANClusterer<Point> clusterer = new DBSCANClusterer<>(eps, minPts, new EuclideanDistance());
		List<Cluster<Point>> clusters = clusterer.cluster(points);

		int clusterId = 1;
		for (Cluster<Point> cluster : clusters) {
			for (Point point : cluster.getPoints()) {
				point.setCid(clusterId);
			}
			clusterId++;
		}
		System.out.println("> Number of clusters: " + clusters.size());
		return clusters;
	}

	/**
	 * Loads the points from the specified catalog file.
	 *
	 * @param catalogFile  the path to the catalog file
	 * @param withLagTable if true, lagTables are also read
	 * @return the list of loaded points
	 */
	public Cluster<Point> loadPointsFromCatalog(String catalogFile, boolean withLagTable) {
		Cluster<Point> cluster = new Cluster<>();
		try (BufferedReader br = new BufferedReader(new FileReader(catalogFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\\s+");
				PointsHandler pointsHandler = new PointsHandler();

				if (withLagTable) {
					pointsHandler.readDatFile(parts[8], codeStrings, threshold);
					if (pointsHandler.getMainPoint().getLagTable().length < 4) {
						System.err.println("> Not enough data (< 4 pks.) to read in: " + parts[8]);
						continue;
					}
				}
				
				Point point = pointsHandler.getMainPoint();
				point.setTime(parts[0]);
				point.setLat(Double.parseDouble(parts[1]));
				point.setLon(Double.parseDouble(parts[2]));
				point.setDep(Double.parseDouble(parts[3]));
				point.setElat(Double.parseDouble(parts[4]));
				point.setElon(Double.parseDouble(parts[5]));
				point.setEdep(Double.parseDouble(parts[6]));
				point.setRes(Double.parseDouble(parts[7]));
				point.setFilePath(parts[8]);
				point.setType(parts[9]);

				int cid = -1;
				if (parts.length > 10) {
					cid = Integer.parseInt(parts[10]);
				}
				point.setCid(cid);
				cluster.addPoint(point);
			}
		} catch (IOException e) {
			System.err.println("> Error reading file: " + e.getMessage());
		}
		return cluster;
	}

	/**
	 * Loads the points from the specified catalog file.
	 *
	 * @param catalogFile the path to the catalog file
	 * @param withLagTable if true, lagTables are also read
	 * @param clusterId cluster id of events to read in
	 * @return the list of loaded points
	 */
	public Cluster<Point> loadPointsFromCatalog(String catalogFile, boolean withLagTable, int clusterId) {
		Cluster<Point> cluster = new Cluster<>();
		try (BufferedReader br = new BufferedReader(new FileReader(catalogFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\\s+");

				int cid = Integer.parseInt(parts[10]);
				if (cid != clusterId) {
					continue;
				}

				PointsHandler pointsHandler = new PointsHandler();

				if (withLagTable) {
					pointsHandler.readDatFile(parts[8], codeStrings, threshold);
					if (pointsHandler.getMainPoint().getLagTable().length < 4) {
						System.err.println("> Not enough data (< 4 pks.) to read in: " + parts[8]);
						continue;
					}
				}

				Point point = pointsHandler.getMainPoint();
				point.setTime(parts[0]);
				point.setLat(Double.parseDouble(parts[1]));
				point.setLon(Double.parseDouble(parts[2]));
				point.setDep(Double.parseDouble(parts[3]));
				point.setElat(Double.parseDouble(parts[4]));
				point.setElon(Double.parseDouble(parts[5]));
				point.setEdep(Double.parseDouble(parts[6]));
				point.setRes(Double.parseDouble(parts[7]));
				point.setFilePath(parts[8]);
				point.setType(parts[9]);
				point.setCid(cid);

				cluster.addPoint(point);
			}
		} catch (IOException e) {
			System.err.println("> Error reading file: " + e.getMessage());
		}
		return cluster;
	}

	/**
	 * Output points obj. to the given outputFile
	 *
	 * @param points     points list of to out
	 * @param outputFile the path of the output file
	 */
	public void writePointsToFile(List<Point> points, String outputFile) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
			for (Point point : points) {
				writer.printf("%s %f %f %f %f %f %f %f %s %s %d%n", 
					point.getTime(),
					point.getLat(),
					point.getLon(),
					point.getDep(),
					point.getElat(),
					point.getElon(),
					point.getEdep(),
					point.getRes(),
					point.getFilePath(),
					point.getType(),
					point.getCid()
					);
			}
			System.out.println("> Points written to " + outputFile);
		} catch (IOException e) {
			System.err.println("> Error writing points to file: " + e.getMessage());
		}
	}

	private static List<Double> computeKDistance(List<Point> points, int k) {
		EuclideanDistance distance = new EuclideanDistance();
		List<Double> kDistances = new ArrayList<>();

		for (Point p : points) {
			List<Double> distances = points.stream()
					.filter(other -> other != p) // Exclude itself
					.map(other -> distance.compute(p.getPoint(), other.getPoint()))
					.sorted()
					.collect(Collectors.toList());

			if (distances.size() >= k) {
				kDistances.add(distances.get(k - 1));
			}
		}
		kDistances.sort(Double::compareTo);
		return kDistances;
	}

	public static double findElbowWithKneedle(List<Double> sseValues) {
		int n = sseValues.size();

		double minVal = Collections.min(sseValues);
		double maxVal = Collections.max(sseValues);
		List<Double> normalizedSSE = sseValues.stream()
				.map(val -> (val - minVal) / (maxVal - minVal))
				.collect(Collectors.toList());

		double[] diffs = new double[n - 1];
		for (int i = 1; i < n; i++) {
			diffs[i - 1] = normalizedSSE.get(i) - normalizedSSE.get(i - 1);
		}

		int elbowIndex = 1;
		double maxDrop = 0;
		for (int i = 1; i < diffs.length; i++) {
			double drop = Math.abs(diffs[i] - diffs[i - 1]);
			if (drop > maxDrop) {
				maxDrop = drop;
				elbowIndex = i + 1;
			}
		}
		return sseValues.get(elbowIndex);
	}

	public static double findElbowWithDist(List<Double> sseValues) {
		int n = sseValues.size();
 
		int kMin = 1, kMax = n;
		double sseMin = sseValues.get(0), sseMax = sseValues.get(n - 1);
 
		double a = sseMax - sseMin;
		double b = kMin - kMax;
		double c = kMax * sseMin - kMin * sseMax;
		double normFactor = Math.sqrt(a * a + b * b);
 
		// Calc dist btwn each pts & regression line
		int elbowIndex = 1;
		double maxDistance = 0;
	
		for (int i = 1; i < n - 1; i++) {
			int k = i + 1;
			double sse = sseValues.get(i);
			double distance = Math.abs(a * k + b * sse + c) / normFactor;

			if (distance > maxDistance) {
				maxDistance = distance;
				elbowIndex = k;
			}
		}
		return sseValues.get(elbowIndex);
	}

		/**
	 * Calculates the triple difference for a cluster and
	 * saves the results to a file named "triplediff.csv"
	 *
	 * @param cluster   the cluster of points
	 * @param clusterId the ID of the cluster
	 * 
	 * @return the list of calculated triple-diff obj.
	 */
	private static List<Object[]> calcTripleDifferences(Cluster<Point> cluster, int clusterId) {
		List<Point> points = cluster.getPoints();
		List<Object[]> tripleDifferences = new ArrayList<>();

		for (int eid1 = 0; eid1 < points.size(); eid1++) {
			for (int eid2 = eid1 + 1; eid2 < points.size(); eid2++) {
				Point p1 = points.get(eid1);
				Point p2 = points.get(eid2);
				double dist = getDistance2D(p1.getLat(), p1.getLon(), p2.getLat(), p2.getLon());
				dist *= App.deg2km;
				double[][] lagTable1 = p1.getLagTable();
				double[][] lagTable2 = p2.getLagTable();

				for (double[] row1 : lagTable1) {
					for (double[] row2 : lagTable2) {
						if (row1[0] == row2[0] && row1[1] == row2[1]) {
							double diff = row2[2] - row1[2];
							Object[] result = new Object[] {
									eid1, eid2, (int) row1[0], (int) row1[1], diff, dist,
									clusterId
							};
							tripleDifferences.add(result);
						}
					}
				}
			}
		}
		tripleDifferences.sort((a, b) -> Double.compare((double) a[5], (double) b[5]));
		return tripleDifferences;
	}

	/**
	 * Saves the triple differences to a CSV file.
	 *
	 * @param tripleDifferences the list of triple differences
	 * @param clusterId         the ID of the cluster
	 */
	private static void saveTripleDifferences(List<Object[]> tripleDifferences, int clusterId) {
		try (PrintWriter writer = new PrintWriter(new FileWriter("triple_diff_" + clusterId + ".csv"))) {
			writer.println("eve0,eve1,stn0,stn1,tdTime,distKm,clusterId");
			for (Object[] td : tripleDifferences) {
				writer.printf("%d,%d,%d,%d,%.3f,%.3f,%d%n", td[0], td[1], td[2], td[3], td[4], td[5], td[6]);
			}
		} catch (IOException e) {
			System.err.println("> Error writing triple differences: " + e.getMessage());
		}
	}
}

class KDistanceGraph {
	private final List<Double> kDistances;
	// private final double elbowEps;

	public KDistanceGraph(List<Double> kDistances, double elbowEps) {
		this.kDistances = kDistances;
		// this.elbowEps = elbowEps;
	}

	public void saveKDistanceGraph(String filePath) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
			writer.println("Point,Distance");
			for (int i = 0; i < kDistances.size(); i++) {
				double value = kDistances.get(i);
				writer.printf("%d,%.3f%n", i + 1, value);
			}
			System.out.println("> k-Distance graph data saved to " + filePath);
		} catch (IOException e) {
			System.err.println("> Error writing k-Distance graph data: " + e.getMessage());
		}
	}

	private void kDistPlotWithGnuplot(List<Double> kDistances, double elbowEps) {
		try {
			String plotFile = "plot_kdist.plt";
			try (PrintWriter writer = new PrintWriter(new FileWriter(plotFile))) {
				writer.println("set terminal pdfcairo");
				writer.println("set datafile separator ','");
				writer.println("set output 'kdist_plot.pdf'");
				writer.println("set title 'k-Distance Graph'");
				writer.println("set xlabel 'Points'");
				writer.println("set ylabel 'Epsilon [km]'");
				writer.println("set grid");
				writer.println("set style line 1 lc rgb '#0060ad' lt 1 lw 2 pt 7 ps 0.5");
				writer.println("set style line 2 lc rgb '#dd181f' lt 1 lw 2");
				writer.println("plot 'kdist.csv' using 1:2 with points ls 1 title 'k-Distance', " + 
							  elbowEps + " with lines ls 2 title sprintf('Elbow = %.2f', " + elbowEps + ")");
			}

			// ProcessBuilder pb = new ProcessBuilder("gnuplot", plotFile);
			// Process p = pb.start();
			// int exitCode = p.waitFor();
			// if (exitCode == 0) {
			// 	System.out.println("> k-Distance plot script saved as " + plotFile);
			// 	System.out.println("> k-Distance plot saved as k_distance_plot.png");
			// } else {
			// 	System.err.println("> Error generating k-Distance plot");
			// }

		} catch (IOException e) {
			System.err.println("> Error executing gnuplot: " + e.getMessage());
		}
	}

	public static void displayKDistanceGraph(List<Double> kDistances, double eps) {
		KDistanceGraph graph = new KDistanceGraph(kDistances, eps);
		graph.saveKDistanceGraph("kdist.csv");
		graph.kDistPlotWithGnuplot(kDistances, eps);
	}
}
