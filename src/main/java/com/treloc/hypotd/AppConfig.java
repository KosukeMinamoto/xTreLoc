package com.treloc.hypotd;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/*
 * Configuration class
 * @author: K.M.
 * @date: 2025/01/26
 * @version: 0.1
 * @description: The class is used to read the configuration file.
 * @usage: 
 */

public final class AppConfig {
	private int numJobs;
	private int numGrid;
	private double hypBottom;
	private double threshold;
	private String taumodFile;
	private String catalogFilePath;
	private AppConfig config;
	private double[][] stnTable;
	private Path[] datPaths;
	private Path parentPath;
	private String[] codes;
	private double stnBottom = 0;

	public AppConfig readConfig(String configFilePath) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(new File(configFilePath));

		this.numJobs = root.get("numJobs").asInt();
		this.numGrid = root.get("numGrid").asInt();
		this.hypBottom = root.get("hypBottom").asDouble();
		this.taumodFile = root.get("taumodFile").asText();
		this.threshold = root.get("threshold").asDouble();
		this.catalogFilePath = root.get("catalogFilePath").asText();

		String stnFile = root.get("stnFile").asText();
		readStnTable(stnFile);

		String datPattern = root.get("datPattern").asText();
		seachFile(datPattern);
		return this;
	}

	private void seachFile(String pathPattern) throws IOException {
		String filePattern = pathPattern
				.substring(pathPattern.lastIndexOf("/") + 1)
				.replace("*", ".*");

		this.parentPath = Paths.get(pathPattern.substring(0, pathPattern.lastIndexOf("/")));
		List<Path> datPathList = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentPath,
				path -> path.getFileName().toString().matches(filePattern))) {
			for (Path entry : stream) {
				datPathList.add(entry);
			}
		}
		this.datPaths = datPathList.toArray(new Path[0]);
	}

	private void readStnTable (String stnFile) {
		List<double[]> stnList = new ArrayList<>();
		List<String> codeList = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(stnFile))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.trim().split("\\s+");

				// Station code
				codeList.add(parts[0]);

				// Lat, Lon, Dep, P-corr, S-corr
				double[] observation = new double[5];
				for (int i = 0; i < 5; i++) {
					observation[i] = Double.parseDouble(parts[i + 1]);
				}

				double latitude = observation[0];
				if (Math.abs(latitude) > 90) {
					throw new IllegalArgumentException("Unreal Latitude:" + line);
				}

				double longitude = observation[1];
				if (Math.abs(longitude) > 180) {
					throw new IllegalArgumentException("Unreal Longitude:" + line);
				}

				double depth = observation[2];
				if ( Math.abs(depth) > 1000 ) {
					System.err.println("Warning: Depth units must be in 'km' (pos-down): " + line);
				} else if ( depth > this.stnBottom ) {
					this.stnBottom = depth + 0.001;
				}
				stnList.add(observation);
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		this.stnTable = stnList.toArray(new double[stnList.size()][]);
		this.codes = codeList.toArray(new String[codeList.size()]);
	}

	public int getNumJobs() {
		return this.numJobs;
	}

	public int getNumGrid() {
		return this.numGrid;
	}

	public double getHypBottom() {
		return this.hypBottom;
	}

	public double getThreshold() {
		return this.threshold;
	}

	public String getTaumodPath() {
		return this.taumodFile;
	}

	public String getCatalogFilePath() {
		return this.catalogFilePath;
	}

	public double[][] getStationTable (){
		return this.stnTable;
	}

	public double getStnBottom() {
		return this.stnBottom;
	}

	public String[] getCodes () {
		return this.codes;
	}

	public Path[] getDatPath () {
		return this.datPaths;
	}

	public AppConfig getConfig() {
		return this.config;
	}
}
