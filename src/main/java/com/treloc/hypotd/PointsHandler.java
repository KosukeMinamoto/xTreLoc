package com.treloc.hypotd;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.MatrixUtils;

/**
 * Handles reading and writing earthquake data from a .dat file.
 * Parses location, errors, and lag table data with a threshold filter.
 *
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 * 
 */
public class PointsHandler {
	private static final Logger logger = Logger.getLogger("com.treloc.hypotd");
	private Point mainPoint;

	public PointsHandler() {
		mainPoint = new Point();
	}

	/**
	 * Reads earthquake data from a .dat file.
	 *
	 * @param datFile Path to the data file.
	 * @param codes   Array of station codes.
	 * @param threshold Threshold for the lag table.
	 */
	public void readDatFile(String datFile, String[] codes, double threshold) {
		List<double[]> lagList = new ArrayList<>();

		int lineCount = 0;
		String line;
		try (BufferedReader reader = new BufferedReader(new FileReader(datFile))) {
			while ((line = reader.readLine()) != null) {
				lineCount++;
				String[] parts = line.trim().split("\\s+");

				if (lineCount == 1) {
					mainPoint.setLat(Double.parseDouble(parts[0]));
					mainPoint.setLon(Double.parseDouble(parts[1]));
					mainPoint.setDep(Double.parseDouble(parts[2]));
					mainPoint.setType(parts[3]);
				} else if (lineCount == 2) {
					mainPoint.setElat(Double.parseDouble(parts[0]));
					mainPoint.setElon(Double.parseDouble(parts[1]));
					mainPoint.setEdep(Double.parseDouble(parts[2]));
					mainPoint.setRes(Double.parseDouble(parts[3]));
				} else {
					if (Math.abs(Double.parseDouble(parts[3])) > threshold) {
						double[] data = new double[] {
								code2idx(parts[0], codes),
								code2idx(parts[1], codes),
								Double.parseDouble(parts[2]),
								Double.parseDouble(parts[3])
						};
						lagList.add(data);
					}
				}
			}

			double[][] lagTable = new double[lagList.size()][];
			Set<Integer> activeIdxs = new HashSet<>();
			for (int i = 0; i < lagList.size(); i++) {
				lagTable[i] = lagList.get(i);
				activeIdxs.add((int) lagList.get(i)[0]);
				activeIdxs.add((int) lagList.get(i)[1]);
			}
			mainPoint.setUsedIdxs(activeIdxs.stream().mapToInt(Integer::intValue).toArray());
			mainPoint.setLagTable(lagTable);
		} catch (IOException e) {
			logger.warning("Error: reading " + datFile + ": " + e.getMessage());
		}
	}

	/**
	 * Writes earthquake data to a file.
	 *
	 * @param filePath Output file path.
	 * @param codes    Array of station codes.
	 */
	public void writeDatFile(String filePath, String[] codes) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
			writer.printf("%.3f %.3f %.3f %s\n", 
				mainPoint.getLat(), mainPoint.getLon(), mainPoint.getDep(), mainPoint.getType()
				);
			writer.printf("%.3f %.3f %.3f %.3f\n", 
				mainPoint.getElat(), mainPoint.getElon(), mainPoint.getEdep(), mainPoint.getRes()
				);
			for (double[] row : mainPoint.getLagTable()) {
				writer.printf("%s %s %.3f %.3f\n", codes[(int) row[0]], codes[(int) row[1]], row[2], row[3]);
			}
		} catch (IOException e) {
			logger.warning("Error: writing " + filePath + ": " + e.getMessage());
		}
	}

	/**
	 * Converts station code to an index.
	 * 
	 * @param name  the station code
	 * @param codes the array of station codes
	 * @return the index of the station code
	 */
	private int code2idx(String name, String[] codes) {
		for (int i = 0; i < codes.length; i++) {
			if (name.equals(codes[i]))
				return i;
		}
		return -1;
	}

	public Point getMainPoint() {
		return mainPoint;
	}

	public void setMainPoint(Point mainPoint) {
		this.mainPoint = mainPoint;
	}
}

class Point implements Clusterable {
	private String time;
	private double lat, lon, dep;
	private double elat, elon, edep, res;
	private String filePath, type;
	private int cid;
	private double[][] lagTable;
	private int[] usedIdx;

	public Point() {}

	/**
	 * Constructs a Point object with the specified parameters.
	 *
	 * @param time      the time of the point
	 * @param lat       the latitude of the point
	 * @param lon       the longitude of the point
	 * @param dep       the depth of the point
	 * @param elat      the latitude error of the point
	 * @param elon      the longitude error of the point
	 * @param edep      the depth error of the point
	 * @param res       the residual of the point
	 * @param filePath  the file path of the point
	 * @param type      the type of the point
	 * @param cid       the cluster id of the point
	 */
	public Point(
			String time,
			double lat, double lon, double dep,
			double elat, double elon, double edep, double res,
			String filePath, String type, int cid) {
		this.time = time;
		this.lat = lat;
		this.lon = lon;
		this.dep = dep;
		this.elat = elat;
		this.elon = elon;
		this.edep = edep;
		this.res = res;
		this.filePath = filePath;
		this.type = type;
		this.cid = cid;
	}

	@Override
	public double[] getPoint() {
		return new double[] { lat, lon };
	}

	@Override
	public String toString() {
		return "Point{lat=" + lat + ", lon=" + lon + "}";
	}

	/**
	 * Returns the vector of the point.
	 * 
	 * @return the vector of the point
	 */
	public RealVector getVector() {
		return MatrixUtils.createRealVector(new double[] { lon, lat, dep });
	}

	public void setTime (String time) {
		this.time = time;
	}

	public String getTime() {
		return time;
	}

	public void setLat(double lat) {
		this.lat = lat;
	}

	public void setLon(double lon) {
		this.lon = lon;
	}

	public void setDep(double dep) {
		this.dep = dep;
	}

	public double getLat() {
		return lat;
	}

	public double getLon() {
		return lon;
	}

	public double getDep() {
		return dep;
	}

	public double getElat() {
		return elat;
	}

	public double getElon() {
		return elon;
	}

	public double getEdep() {
		return edep;
	}

	public void setElat(double elat) {
		this.elat = elat;
	}

	public void setElon(double elon) {
		this.elon = elon;
	}

	public void setEdep(double edep) {
		this.edep = edep;
	}

	public void setRes(double res) {
		this.res = res;
	}

	public double getRes() {
		return res;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getType() {
		return type;
	}

	public String getFilePath() {
		return filePath;
	}

	public String getFileName() {
		return filePath.substring(filePath.lastIndexOf("/") + 1);
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public void setCid(int cid) {
		this.cid = cid;
	}

	public int getCid() {
		return cid;
	}

	public void setLagTable(double[][] lagTable) {
		this.lagTable = lagTable;
	}

	public int[] getUsedIdx() {
		return usedIdx;
	}

	public void setUsedIdxs(int[] usedIdx) {
		this.usedIdx = usedIdx;
	}

	public double[][] getLagTable() {
		return lagTable;
	}
}
