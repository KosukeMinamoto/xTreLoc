package com.treloc.hypotd;

/*
 * DataHandler.java
 * 
 * This class handles the data read from the dat file.
 * 
 * @author: K.M.
 * @date: 2025/01/26
 * @version: 0.1
 * @description: The class is used to read the data from the dat file.
 * @usage: 
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataHandler {
	private double latitude;
	private double longitude;
	private double depth;
	private String method;
	private double latitudeError;
	private double longitudeError;
	private double depthError;
	private double residual;
	private double[][] lagTable;

	private final AppConfig appConfig;

	public DataHandler(AppConfig config) {
		appConfig = config;
	}

	public void read(String datFile, String[] codes) {
		try (BufferedReader reader = new BufferedReader(new FileReader(datFile))) {
			double threshold = appConfig.getCorrVal();
			List<double[]> lagList = new ArrayList<>();
			int lineCount = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				lineCount++;
				String[] parts = line.trim().split("\\s+");

				switch (lineCount) {
					case 1:
						this.latitude = Double.parseDouble(parts[0]);
						this.longitude = Double.parseDouble(parts[1]);
						this.depth = Double.parseDouble(parts[2]);
						this.method = parts[3];
						break;
					case 2:
						this.latitudeError = Double.parseDouble(parts[0]);
						this.longitudeError = Double.parseDouble(parts[1]);
						this.depthError = Double.parseDouble(parts[2]);
						this.residual = Double.parseDouble(parts[3]);
						break;
					default:
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
			int len = lagList.size();
			// if (len < 4) {
				// throw new RuntimeException("Not enough data to read in (< 4 picks).");
			// } else {
			this.lagTable = new double[len][];
			for (int i = 0; i < len; i++) {
				this.lagTable[i] = lagList.get(i);
				// }
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void write(String filePath, String[] codes) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
			writer.printf("%.3f %.3f %.3f %s\n", latitude, longitude, depth, method);
			writer.printf("%.3f %.3f %.3f %.3f\n", latitudeError, longitudeError, depthError, residual);
			for (double[] row : this.lagTable) {
				writer.printf("%s %s %.3f %.3f\n", codes[(int)row[0]], codes[(int)row[1]], row[2], row[3]);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private double code2idx(String name, String[] codes) {
		for (int i = 0; i < codes.length; i++) {
			if (name.equals(codes[i])) {
				return i;
			}
		}
		return -1;
	}

	private int[] usedIdxs(double[][] lagtTbl) {
		Set<Integer> activeIdxs = new HashSet<>();
		for (double[] row : lagtTbl) {
			activeIdxs.add((int) row[0]);
			activeIdxs.add((int) row[1]);
		}
		int[] result = new int[activeIdxs.size()];
		int i = 0;
		for (Integer idx : activeIdxs) {
			result[i++] = idx;
		}
		return result;
	}

	// Getter
	public double[][] getLagTable() {
		return this.lagTable;
	}

	public int[] getUsedIdx() {
		return usedIdxs(this.lagTable);
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public double getDepth() {
		return depth;
	}

	public String getMethod() {
		return method;
	}

	public double getLatitudeError() {
		return latitudeError;
	}

	public double getLongitudeError() {
		return longitudeError;
	}

	public double getDepthError() {
		return depthError;
	}

	public double getResidual() {
		return residual;
	}

	// Setter
	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public void setDepth(double depth) {
		this.depth = depth;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public void setLatitudeError(double latitudeError) {
		this.latitudeError = latitudeError;
	}

	public void setLongitudeError(double longitudeError) {
		this.longitudeError = longitudeError;
	}

	public void setDepthError(double depthError) {
		this.depthError = depthError;
	}

	public void setResidual(double residual) {
		this.residual = residual;
	}
	
	public void setLagTable(double[][] lagTable) {
		this.lagTable = lagTable;
	}
}
