package com.treloc.hypotd;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import java.util.stream.Collectors;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
// import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

import edu.sc.seis.TauP.TauModelException;

/**
 * SpatialClustering
 * This class is used to perform spatial clustering on a set of points.
 * It uses the DBSCAN algorithm to cluster points based on geographical
 * distance.
 * 
 * @version 0.1
 * @since 2025-02-22
 * @author K.M.
 */
public class SpatialClustering extends HypoUtils {
	private static final Logger logger = Logger.getLogger("com.treloc.hypotd");
	private Point refPoint;
	private int minPts;
	private double eps;
	private String catalogFile;

	/**
	 * Constructs a SpatialClustering object with the specified configuration.
	 *
	 * @param appConfig the configuration loader containing necessary parameters
	 * @throws TauModelException if there is an error in the Tau model
	 */
	public SpatialClustering (ConfigLoader appConfig) throws TauModelException {
		super(appConfig);
		this.catalogFile = appConfig.getCatalogFile("CLS");
	
		double[][] stationTable = appConfig.getStationTable();
		double refLat = new Median().evaluate(stationTable[0]);
		double refLon = new Median().evaluate(stationTable[1]);
		this.refPoint = new Point("", refLat, refLon, 0, 0, 0, 0, 0, "", "REF", -999);

		this.minPts = appConfig.getClsPts();
		this.eps = appConfig.getClsEps();

		Cluster<Point> allCluster = loadPointsFromCatalog(catalogFile, false);
		List<Point> allPoints = allCluster.getPoints();
		boolean hasCid = allPoints.stream().anyMatch(p -> p.getCid() != -1);
		if (hasCid) {
			logger.info("Info: No clustering performed");
			logger.info("Info: The number of defined cls.: " + allPoints.stream().map(Point::getCid).distinct().count());
		} else {
			logger.info("Info: CID not set for any point. Proceeding with clustering...");
			List<Cluster<Point>> clusters = runClustering(allCluster, refPoint);

			Set<Point> clusteredPoints = new HashSet<>();
			for (Cluster<Point> cluster : clusters) {
				clusteredPoints.addAll(cluster.getPoints());
				for (Point point : cluster.getPoints()) {
					allPoints.remove(point); // allPoints -> Noise Points
				}
			}
			clusteredPoints.addAll(allPoints);
			writePointsToFile(new ArrayList<>(clusteredPoints), catalogFile);
		}

		int clusterId = 0;
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
	 * @param clsPts   the cluster of points to be clustered
	 * @param refPoint the reference point for clustering
	 * @return a list of clusters formed
	 */
	public List<Cluster<Point>> runClustering(Cluster<Point> clsPts, Point refPoint) {
		List<Point> points = clsPts.getPoints();

		if (eps <= 0) {
			List<Double> kDistances = computeKDistance(points, minPts);
			double estimatedEps = findElbowWithDist(kDistances);

			logger.info(
				"Negative 'clsEps' (=" + eps + ") is set" +
				"\nEstimated epsilon: " + estimatedEps + " km" +
				"\nMin samples: " + minPts
			);

			KDistancePlot.setKDistances(kDistances, estimatedEps);
			KDistancePlot.displayChart();
			eps = estimatedEps;
		} else {
			logger.info(
				"Given epsilon: " + eps + " km" +
				"\nMin samples: " + minPts
			);
		}

		DBSCANClusterer<Point> clusterer = new DBSCANClusterer<>(eps, minPts, new HaversineDistance());
		List<Cluster<Point>> clusters = clusterer.cluster(points);
		int clusterId = 0;
		for (Cluster<Point> cluster : clusters) {
			for (Point point : cluster.getPoints()) {
				point.setCid(clusterId);
			}
			clusterId++;
		}

		logger.info("There are " + clusters.size() + " clusters.");
		for (Cluster<Point> cluster : clusters) {
			logger.info("CID-" + cluster.getPoints().get(0).getCid() + " has " + cluster.getPoints().size() + " events.");
		}
		return clusters;
	}

	/**
	 * Computes the k-distance for each point in the list.
	 *
	 * @param points the list of points
	 * @param k      the number of nearest neighbors to consider
	 * @return a list of k-distances
	 */
	private static List<Double> computeKDistance(List<Point> points, int k) {
		// VincentyDistance distance = new VincentyDistance();
		HaversineDistance distance = new HaversineDistance();
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

	/**
	 * Finds the elbow point in the list of SSE values using the Kneedle algorithm.
	 *
	 * @param sseValues the list of SSE values
	 * @return the elbow point value
	 */
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

	/**
	 * Finds the elbow point in the list of SSE values using distance calculation.
	 *
	 * @param sseValues the list of SSE values
	 * @return the elbow point value
	 */
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
	 * Calculates the triple differences for a cluster and returns the results.
	 *
	 * @param cluster   the cluster of points
	 * @param clusterId the ID of the cluster
	 * @return the list of calculated triple-difference objects
	 */
	private static List<Object[]> calcTripleDifferences(Cluster<Point> cluster, int clusterId) {
		List<Point> points = cluster.getPoints();
		List<Object[]> tripleDifferences = new ArrayList<>();

		for (int eid1 = 0; eid1 < points.size(); eid1++) {
			for (int eid2 = eid1 + 1; eid2 < points.size(); eid2++) {
				Point p1 = points.get(eid1);
				Point p2 = points.get(eid2);

				if (p1.getType().equals("REF") && p2.getType().equals("REF")) {
					continue;
				} else if (p1.getType().equals("ERR") || p2.getType().equals("ERR")) {
					continue;
				}

				GeodesicData g = Geodesic.WGS84.Inverse(p1.getLat(), p1.getLon(), p2.getLat(), p2.getLon());
				double distKm = g.s12 / 1000.0; // Distance in km
				double[][] lagTable1 = p1.getLagTable();
				double[][] lagTable2 = p2.getLagTable();

				for (double[] row1 : lagTable1) {
					for (double[] row2 : lagTable2) {
						if (row1[0] == row2[0] && row1[1] == row2[1]) {
							double diff = row2[2] - row1[2];
							Object[] result = new Object[] {
									eid1, eid2, (int) row1[0], (int) row1[1], diff, distKm,
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
			logger.warning("Error: Writing triple differences: " + e.getMessage());
		}
	}
}

class HaversineDistance implements DistanceMeasure {
	private static final double EARTH_RADIUS_KM = 6371.0;

	/**
	 * Computes the Haversine distance between two points.
	 *
	 * @param a the first point
	 * @param b the second point
	 * @return the Haversine distance between the two points
	 */
	@Override
	public double compute(double[] a, double[] b) {
		double lat1 = Math.toRadians(a[0]);
		double lon1 = Math.toRadians(a[1]);
		double lat2 = Math.toRadians(b[0]);
		double lon2 = Math.toRadians(b[1]);

		double dlat = lat2 - lat1;
		double dlon = lon2 - lon1;

		double haversine = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
						   Math.cos(lat1) * Math.cos(lat2) *
						   Math.sin(dlon / 2) * Math.sin(dlon / 2);

		return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(haversine));
	}
}

class VincentyDistance implements DistanceMeasure {
	private static final double EARTH_RADIUS_KM = 6371.0;

	/**
	 * Computes the Vincenty distance between two points.
	 *
	 * @param a the first point
	 * @param b the second point
	 * @return the Vincenty distance between the two points
	 */
	public double compute(double[] a, double[] b) {
		double lat1 = Math.toRadians(a[0]);
		double lon1 = Math.toRadians(a[1]);
		double lat2 = Math.toRadians(b[0]);
		double lon2 = Math.toRadians(b[1]);

		double dlon = lon2 - lon1;

		double U1 = Math.atan((1 - 0.0818191908426) * Math.tan(lat1));
		double U2 = Math.atan((1 - 0.0818191908426) * Math.tan(lat2));
		double sinU1 = Math.sin(U1);
		double cosU1 = Math.cos(U1);
		double sinU2 = Math.sin(U2);
		double cosU2 = Math.cos(U2);

		double lambda = dlon;
		double lambdaP;
		double iterLimit = 100;
		double cosSqAlpha;
		double sinSigma;
		double cos2SigmaM;
		double cosSigma;
		double sigma;

		do {
			double sinLambda = Math.sin(lambda);
			double cosLambda = Math.cos(lambda);
			sinSigma = Math.sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) +
					(cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda));
			if (sinSigma == 0) {
				return 0; // co-incident points
			}
			cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
			sigma = Math.atan2(sinSigma, cosSigma);
			double sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
			cosSqAlpha = 1 - sinAlpha * sinAlpha;
			cos2SigmaM = cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha;
			double C = 0.00335281066474748 / 16 * cosSqAlpha * (4 + 0.00335281066474748 * (4 - 3 * cosSqAlpha));
			lambdaP = lambda;
			lambda = dlon + (1 - C) * 0.00335281066474748 * sinAlpha *
					(sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
		} while (Math.abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0);

		if (iterLimit == 0) {
			return Double.NaN; // formula failed to converge
		}

		double uSq = cosSqAlpha * (EARTH_RADIUS_KM * EARTH_RADIUS_KM - 6356.752314245 * 6356.752314245)
				/ (6356.752314245 * 6356.752314245);
		double A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
		double B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));
		double deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
				B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));

		return 6356.752314245 * A * (sigma - deltaSigma);
	}
}