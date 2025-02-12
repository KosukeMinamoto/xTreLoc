package com.treloc.hypotd;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.List;

public class CatalogHander {
	private double threshold;
	private String[] codes;

	public CatalogHander (AppConfig appConfig) {
		this.threshold = appConfig.getThreshold();
		this.codes = appConfig.getCodes();
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
				PointsHandler pointsHandler = new PointsHandler();
				Point point = pointsHandler.getMainPoint();
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
					pointsHandler.readDatFile(parts[9], codes, threshold);
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
	 * @param catalogFile  the path to the catalog file
	 * @param withLagTable if true, lagTables are also read
	 * @param clusterId    cluster id of events to read in
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

				PointsHandler pointsHandler = new PointsHandler();
				Point point = pointsHandler.getMainPoint();
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
					pointsHandler.readDatFile(parts[9], codes, threshold);
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
	public static void writePointsToFile(List<Point> points, String outputFile) {
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
						point.getCid());
			}
			System.out.println("> Points written to " + outputFile);
		} catch (IOException e) {
			System.err.println("> Error writing points to file: " + e.getMessage());
		}
	}
}
