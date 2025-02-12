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

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import org.jfree.ui.RectangleEdge;

import org.apache.commons.math3.ml.clustering.Cluster;
// import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.stat.descriptive.rank.Median;

public class SpatialClustering extends HypoUtils {
	private Point refPoint;
	private int minPts;
	private double eps;
	private String[] codes;
	private double threshold;

	public SpatialClustering (AppConfig appConfig) {
		super(appConfig);
		double[][] stnTable = appConfig.getStationTable();
		double refLat = new Median().evaluate(stnTable[0]);
		double refLon = new Median().evaluate(stnTable[1]);
		this.refPoint = new Point("", refLat, refLon, 0, 0, 0, 0, 0, "", "REF", -999);

		this.threshold = appConfig.getThreshold();
		this.codes = appConfig.getCodes();

		this.minPts = appConfig.getClsPts();
		this.eps = appConfig.getClsEps();

		runClustering(appConfig.getCatalogFile(), refPoint);
	}

	/**
	 * Runs the clustering process using DBSCAN with distances in kilometers.
	 *
	 * @param catalogFile the path to the catalog file
	 * @param minPts      the minimum number of points to form a cluster
	 * @param refPoints   the list of reference points
	 */
	public void runClustering(String catalogFile, Point refPoint) {
		List<Point> Points = loadPointsFromCatalog(catalogFile, false);
		boolean hasCid = Points.stream().anyMatch(p -> p.getCid() != -1);

		List<Cluster<Point>> clusters;
		if (hasCid) {
			System.out.println("> Using existing CID to form clusters.");
			clusters = Points.stream()
					.collect(Collectors.groupingBy(Point::getCid))
					.values().stream()
					.map(points -> {
						Cluster<Point> cluster = new Cluster<>();
						points.forEach(cluster::addPoint);
						return cluster;
					})
					.collect(Collectors.toList());
		} else {
			System.out.println("> CID not set for any point. Proceeding with clustering.");
			for (Point p : Points) {
				p.setKmLat((p.getLat() - refPoint.getLat()) * 111.32);
				p.setKmLon((p.getLon() - refPoint.getLon()) * 111.32 * Math.cos(Math.toRadians(p.getLat())));
			}

			if (eps <= 0) {
				System.out.println("> Negative 'clsEps' (=" + eps + ") is set. Estimating eps...");
				List<Double> kDistances = computeKDistance(Points, minPts);
				double estimatedEps = findElbowWithDist(kDistances);
				System.out.println("> Estimated epsilon: " + estimatedEps + " km & min samples: " + minPts);

				KDistanceGraph.displayKDistanceGraph(kDistances, estimatedEps);
				eps = estimatedEps;
			} else {
				System.out.println("> Given epsilon: " + eps + " km & min samples: " + minPts);
			}

			DBSCANClusterer<Point> clusterer = new DBSCANClusterer<>(eps, minPts, new EuclideanDistance());
			clusters = clusterer.cluster(Points);

			int clusterId = 0;
			for (Cluster<Point> cluster : clusters) {
				for (Point point : cluster.getPoints()) {
					point.setCid(clusterId);
				}
				clusterId++;
			}
		}
		System.out.println("> Number of clusters: " + clusters.size());

		String outputFile = catalogFile.replaceFirst("\\.[^.]+$", "_CLS$0");
		writePointsToFile(Points, outputFile);
	}

	/**
	 * Loads the points from the specified catalog file.
	 *
	 * @param catalogFile  the path to the catalog file
	 * @param withLagTable if true, lagTables are also read
	 * @return the list of loaded points
	 */
	 public List<Point> loadPointsFromCatalog(String catalogFile, boolean withLagTable) {
		List<Point> points = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(catalogFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\\s+");
				PointsHandler dataHandler = new PointsHandler();

				Point point = dataHandler.getMainPoint();
				point.setTime(parts[0]);
				point.setLat(Double.parseDouble(parts[1]));
				point.setLon(Double.parseDouble(parts[2]));
				point.setDep(Double.parseDouble(parts[3]));
				point.setElat(Double.parseDouble(parts[4]));
				point.setElon(Double.parseDouble(parts[5]));
				point.setEdep(Double.parseDouble(parts[6]));
				point.setRes(Double.parseDouble(parts[7]));
				point.setType(parts[8]);
				point.setFilePath(parts[9]);

				if (withLagTable) {
					dataHandler.readDatFile(parts[9], codes, threshold);
				}

				int cid = -1;
				if (parts.length > 10) {
					cid = Integer.parseInt(parts[10]);
				}
				point.setCid(cid);
				points.add(point);
			}
		} catch (IOException e) {
			System.err.println("> Error reading file: " + e.getMessage());
		}
		return points;
	}

	/**
	 * Loads the points from the specified catalog file.
	 *
	 * @param catalogFile the path to the catalog file
	 * @param withLagTable if true, lagTables are also read
	 * @param clusterId cluster id of events to read in
	 * @return the list of loaded points
	 */
	public List<Point> loadPointsFromCatalog(String catalogFile, boolean withLagTable, int clusterId) {
		List<Point> points = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(catalogFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\\s+");

				int cid = Integer.parseInt(parts[10]);
				if (cid != clusterId) {
					continue;
				}

				PointsHandler dataHandler = new PointsHandler();
				Point point = dataHandler.getMainPoint();
				point.setTime(parts[0]);
				point.setLat(Double.parseDouble(parts[1]));
				point.setLon(Double.parseDouble(parts[2]));
				point.setDep(Double.parseDouble(parts[3]));
				point.setElat(Double.parseDouble(parts[4]));
				point.setElon(Double.parseDouble(parts[5]));
				point.setEdep(Double.parseDouble(parts[6]));
				point.setRes(Double.parseDouble(parts[7]));
				point.setType(parts[8]);
				point.setFilePath(parts[9]);
				point.setCid(cid);

				if (withLagTable) {
					dataHandler.readDatFile(parts[9], codes, threshold);
				}

				points.add(point);
			}
		} catch (IOException e) {
			System.err.println("> Error reading file: " + e.getMessage());
		}
		return points;
	}

	/**
	 * Output points obj. to the given outputFile
	 *
	 * @param points     points list of to out
	 * @param outputFile the path of the output file
	 */
	private static void writePointsToFile(List<Point> points, String outputFile) {
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
}

class KDistanceGraph extends JPanel {
	private final List<Double> kDistances;
	private final double elbowEps;
	private final JFreeChart chart;

	public KDistanceGraph(List<Double> kDistances, double elbowEps) {
		this.kDistances = kDistances;
		this.elbowEps = elbowEps;
		this.chart = createChart();

		setLayout(new BorderLayout());
		ChartPanel chartPanel = new ChartPanel(chart);
		add(chartPanel, BorderLayout.CENTER);
	}

	private JFreeChart createChart() {
		XYSeries series = new XYSeries("k-Distance");
		XYSeries elbowSeries = new XYSeries("Elbow Point");

		int elbowIndex = -1;
		for (int i = 0; i < kDistances.size(); i++) {
			double value = kDistances.get(i);
			series.add(i + 1, value);
			if (Math.abs(value - elbowEps) < 1e-6) { // Find closest point
				elbowIndex = i + 1;
				elbowSeries.add(elbowIndex, value);
			}
		}

		XYSeriesCollection dataset = new XYSeriesCollection();
		dataset.addSeries(series);
		if (elbowIndex != -1) {
			dataset.addSeries(elbowSeries); // Add an elbow pt
		}

		JFreeChart chart = ChartFactory.createXYLineChart(
				"k-Distance Graph",
				"Points",
				"Distance",
				dataset,
				PlotOrientation.VERTICAL,
				true,
				true,
				false);

		XYPlot plot = (XYPlot) chart.getPlot();
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();

		// k-Distance
		renderer.setSeriesLinesVisible(0, true);
		renderer.setSeriesShapesVisible(0, false);

		// Elbow pt
		if (elbowIndex != -1) {
			renderer.setSeriesLinesVisible(1, false);
			renderer.setSeriesShapesVisible(1, true);
			renderer.setSeriesPaint(1, Color.RED);
		}

		plot.setRenderer(renderer);

		TextTitle textTitle = new TextTitle("Estimated epsilon: " + elbowEps);
		textTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
		textTitle.setPosition(RectangleEdge.TOP);
		textTitle.setHorizontalAlignment(HorizontalAlignment.LEFT);
		chart.addSubtitle(textTitle);

		return chart;
	}

	public static void displayKDistanceGraph(List<Double> kDistances, double eps) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("k-Distance Graph");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setSize(600, 400);

			KDistanceGraph graphPanel = new KDistanceGraph(kDistances, eps);
			frame.add(graphPanel, BorderLayout.CENTER);

			frame.setVisible(true);
		});
	}
}

class TripleDifference {
	private final Point point1;
	private final Point point2;
	private final double diff;
	private final double distance;
	private final int cid;

	public TripleDifference(Point point1, Point point2, double diff, double distance, int clusterId) {
		this.point1 = point1;
		this.point2 = point2;
		this.diff = diff;
		this.distance = distance;
		this.cid = clusterId;
	}

	public Point getPoint1() {
		return point1;
	}

	public Point getPoint2() {
		return point2;
	}

	public double getDiff() {
		return diff;
	}

	public double getDistance() {
		return distance;
	}

	public int getCid() {
		return cid;
	}
}