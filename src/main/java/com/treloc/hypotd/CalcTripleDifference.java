package com.treloc.hypotd;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.ml.clustering.Cluster;

public class CalcTripleDifference extends HypoUtils {
	
	public CalcTripleDifference (AppConfig appConfig) {
		super(appConfig);
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
	private static List<TripleDifference> Calculate(Cluster<Point> cluster, int clusterId) {
		List<Point> points = cluster.getPoints();
		List<TripleDifference> tripleDifferences = new ArrayList<>();

		for (int i = 0; i < points.size(); i++) {
			for (int j = i + 1; j < points.size(); j++) {
				Point p1 = points.get(i);
				Point p2 = points.get(j);
				double dist = getDistance2D(p1.getLat(), p1.getLon(), p2.getLat(), p2.getLon());
				// TODO: Calculate triple difference logic here
				double diff = Math.random(); // Replace with actual calculation

				tripleDifferences.add(new TripleDifference(p1, p2, diff, dist, clusterId));
			}
		}
		return tripleDifferences;
	}

	/**
	 * Saves the triple differences to a CSV file.
	 *
	 * @param tripleDifferences the list of triple differences
	 * @param clusterId         the ID of the cluster
	 */
	private static void saveTripleDifferences(List<TripleDifference> tripleDifferences, int clusterId) {
		try (PrintWriter writer = new PrintWriter(new FileWriter("triple_diff_cluster_" + clusterId + ".csv"))) {
			writer.println("Point1,Point2,Diff,Distance");
			for (TripleDifference td : tripleDifferences) {
				writer.printf("%s,%s,%.3f,%.3f%n", td.getPoint1(), td.getPoint2(), td.getDiff(), td.getDistance());
			}
		} catch (IOException e) {
			System.err.println("> Error writing triple differences: " + e.getMessage());
		}
	}
}
