package com.treloc.hypotd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/*
 * Configuration class
 * @author: K.M.
 * @date: 2025/02/14
 * @version: 0.1
 * @description: The class is used to read the configuration file.
 */

public final class ConfigLoader {
	private final JsonNode config;
	private double stnBottom = 0;
	private List<Station> stationList;
	private String[] codeStrings;
	private double[][] stationTable;

	public ConfigLoader(String jsonFile) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		this.config = mapper.readTree(new File(jsonFile));

		this.stationList = readStnTable(getStationFile());
		this.stationTable = new double[stationList.size()][5];
		this.codeStrings = new String[stationList.size()];
		for (int i = 0; i < stationList.size(); i++) {
			Station station = stationList.get(i);
			codeStrings[i] = station.getCode();
			stationTable[i][0] = station.getLat();
			stationTable[i][1] = station.getLon();
			stationTable[i][2] = station.getDep();
			if (station.getDep() > stnBottom) {
				stnBottom = station.getDep() + 0.01;
			}
			stationTable[i][3] = station.getPc();
			stationTable[i][4] = station.getSc();
		}
	}

	private static List<Path> seachFile(String pathPattern) {
		String filePattern = pathPattern
				.substring(pathPattern.lastIndexOf("/") + 1)
				.replace("*", ".*");

		Path parentPath = Paths.get(pathPattern.substring(0, pathPattern.lastIndexOf("/")));
		List<Path> datPathList = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentPath,
				path -> path.getFileName().toString().matches(filePattern))) {
			for (Path entry : stream) {
				datPathList.add(entry);
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
		} 
		return datPathList;
	}

	private static List<Station> readStnTable (String statinFile) {
		List<Station> stationList = new ArrayList<>();

		try {
			File file = new File(statinFile);
			Scanner scanner = new Scanner(file);

			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] parts = line.split("\\s+");

				String code = parts[0];
				double lat = Double.parseDouble(parts[1]);
				double lon = Double.parseDouble(parts[2]);
				double dep = Double.parseDouble(parts[3]);
				double pc = Double.parseDouble(parts[4]);
				double sc = Double.parseDouble(parts[5]);
				stationList.add(new Station(code, lat, lon, dep, pc, sc));
			}
			scanner.close();
		} catch(FileNotFoundException e) {
			System.err.println("Station file " + statinFile + " has not found" + e.getMessage());
		}
		return stationList;
	}

	public JsonNode getConfig () {
		return this.config;
	}

	/**
	 * Get the number of jobs to run.
	 *
	 * @return the number of jobs to run
	 */
	public int getNumJobs() {
		if (config.has("numJobs")) {
			return config.get("numJobs").asInt();
		} else {
			int defaultNumJobs = 1;
			System.err.println("Error: 'numJobs' parameter does not exist. Use default val. numJobs=" + defaultNumJobs);
			return defaultNumJobs;
		}
	}

	/**
	 * Get the number of grids to use.
	 *
	 * @return the number of grids to use
	 */
	public int getNumGrid() {
		if (config.has("numGrid")) {
			return config.get("numGrid").asInt();
		} else {
			int defaultNumGrid = 100;
			System.err.println("Error: 'numGrid' parameter does not exist. Use default val. numGrid=" + defaultNumGrid);
			return defaultNumGrid;
		}
	}

	/**
	 * Get the bottom depth of the hypocenter.
	 *
	 * @return the bottom depth of the hypocenter
	 */
	public double getHypBottom() {
		if (config.has("hypBottom")) {
			return config.get("hypBottom").asDouble();
		} else {
			double defaultHypBottom = 100.0;
			System.err.println(
					"Error: 'hypBottom' parameter does not exist. Use default val. hypBottom=" + defaultHypBottom);
			return defaultHypBottom;
		}
	}

	/**
	 * Get the threshold for the hypocenter.
	 *
	 * @return the threshold for the hypocenter
	 */
	public double getThreshold() {
		if (config.has("threshold")) {
			return config.get("threshold").asDouble();
		} else {
			double defaultThreshold = 0.0;
			System.err.println(
					"Error: 'threshold' parameter does not exist. Use default val. threshold=" + defaultThreshold);
			return defaultThreshold;
		}
	}

	/**
	 * Get the TauP file.
	 *
	 * @return the TauP file
	 */
	public String getTaupFile() {
		if (config.has("taupFile")) {
			return config.get("taupFile").asText();
		} else {
			String defaultTaupFile = "prem";
			System.err.println(
					"Error: 'taupFile' parameter does not exist. Use default val. taupFile=" + defaultTaupFile);
			return defaultTaupFile;
		}
	}

	/**
	 * Get the catalog file.
	 *
	 * @return the catalog file
	 */
	public String getCatalogFile() {
		if (config.has("catalogFile")) {
			return config.get("catalogFile").asText();
		} else {
			String defaultCatalogFile = "catalog.list";
			System.err.println("Error: 'catalogFile' parameter does not exist. Use default val. catalogFile="
					+ defaultCatalogFile);
			return defaultCatalogFile;
		}
	}

	/**
	 * Get the station file.
	 *
	 * @return the station file
	 */
	public String getStationFile() {
		if (config.has("stationFile")) {
			return config.get("stationFile").asText();
		} else {
			String defaultStationFile = "station.tbl";
			System.err.println("Error: 'stationFile' parameter does not exist. Use default val. stationFile="
					+ defaultStationFile);
			return defaultStationFile;
		}
	}

	/**
	 * Get the code strings.
	 *
	 * @return the code strings
	 */
	public String[] getCodeStrings () {
		return codeStrings;
	}

	/**
	 * Get the station table.
	 *
	 * @return the station table
	 */
	public double[][] getStationTable () {
		return stationTable;
	}

	/**
	 * Get the bottom depth of the station.
	 *
	 * @return the bottom depth of the station
	 */
	public double getStnBottom () {
		return stnBottom;
	}

	/**
	 * Get the data pattern.
	 *
	 * @return the data pattern
	 */
	public String getDatPattern() {
		if (config.has("datPattern")) {
			return config.get("datPattern").asText();
		} else {
			String defaultDatPattern = "./*";
			System.err.println(
					"Error: 'datPattern' parameter does not exist. Searching current directory...");
			return defaultDatPattern;
		}
	}

	/**
	 * Get the data paths.
	 *
	 * @return the data paths
	 */
	public Path[] getDatPaths () {
		return seachFile(getDatPattern()).toArray(new Path[0]);
	}

	/**
	 * Get the travel time distance in kilometers.
	 *
	 * @return the travel time distance in kilometers
	 */
	public int[] getTrpDistKm() {
		if (config.has("trpPrm") && config.get("trpPrm").has("distKm")) {
			JsonNode distKmNode = config.get("trpPrm").get("distKm");
			int[] distKmArray = new int[distKmNode.size()];
			for (int i = 0; i < distKmNode.size(); i++) {
				distKmArray[i] = distKmNode.get(i).asInt();
			}
			return distKmArray;
		} else {
			int[] trpDistKm = new int[] {40, 20, 10};
			System.err.println("Error: 'trpPrm.distKm' parameter does not exist. Use default vals. " + trpDistKm);
			return trpDistKm;
		}
	}

	/**
	 * Get the travel time damping factors.
	 *
	 * @return the travel time damping factors
	 */
	public int[] getTrpDampFact() {
		if (config.has("trpPrm") && config.get("trpPrm").has("dampFact")) {
			JsonNode dampFactNode = config.get("trpPrm").get("dampFact");
			int[] dampFactArray = new int[dampFactNode.size()];
			for (int i = 0; i < dampFactNode.size(); i++) {
				dampFactArray[i] = dampFactNode.get(i).asInt();
			}
			return dampFactArray;
		} else {
			int[] dampFacts = new int[] {10, 5, 1};
			System.err.println("Error: 'trpPrm.dampFact' parameter does not exist. Use default vals.=" + dampFacts);
			return dampFacts;
		}
	}

	/**
	 * Get the cluster epsilon.
	 *
	 * @return the cluster epsilon
	 */
	public double getClsEps() {
		if (config.has("clsEps")) {
			return config.get("clsEps").asDouble();
		} else {
			double defaultClsEps = 5;
			System.err.println("Error: 'clsEps' parameter does not exist. Use default val. clsEps=" + defaultClsEps);
			return defaultClsEps;
		}
	}

	/**
	 * Get the number of points in the cluster.
	 *
	 * @return the number of points in the cluster
	 */
	public int getClsPts() {
		if (config.has("clsPts")) {
			return config.get("clsPts").asInt();
		} else {
			int defaultClsPts = 10;
			System.err.println("Error: 'clsPts' parameter does not exist. Use default val. clsPts=" + defaultClsPts);
			return defaultClsPts;
		}
	}
}

class Station {
	private String code;
	private double lat;
	private double lon;
	private double dep;
	private double pc;
	private double sc;

	/**
	 * Constructor for the Station class.
	 *
	 * @param code the code of the station
	 * @param lat the latitude of the station
	 * @param lon the longitude of the station
	 * @param dep the depth of the station
	 * @param pc the phase velocity of the station
	 * @param sc the station correction of the station
	 */
	public Station(String code, double lat, double lon, double dep, double pc, double sc) {
		if (Math.abs(lat) > 90) {
			throw new IllegalArgumentException("Unreal Latitude:" + lat);
		} else if (Math.abs(lon) > 180) {
			throw new IllegalArgumentException("Unreal Longitude:" + lon);
		} else if ( Math.abs(dep) > 1000 ) {
			System.err.println("Warning: Depth units must be in 'km' (pos-down): " + dep);
		}
		this.code = code;
		this.lat = lat;
		this.lon = lon;
		this.dep = dep;
		this.pc = pc;
		this.sc = sc;
	}

	/**
	 * Get the code of the station.
	 *
	 * @return the code of the station
	 */
	public String getCode() {
		return code;
	}

	/**
	 * Get the latitude of the station.
	 *
	 * @return the latitude of the station
	 */
	public double getLat() {
		return lat;
	}

	/**
	 * Get the longitude of the station.
	 *
	 * @return the longitude of the station
	 */
	public double getLon() {
		return lon;
	}

	/**
	 * Get the depth of the station.
	 *
	 * @return the depth of the station
	 */
	public double getDep() {
		return dep;
	}

	/**
	 * Get the station correction of the station.
	 *
	 * @return the station correction of the station
	 */
	public double getPc() {
		return pc;
	}

	/**
	 * Get the station correction of the station.
	 *
	 * @return the station correction of the station
	 */
	public double getSc() {
		return sc;
	}

	@Override
	public String toString() {
		return "DataRecord{" +
				"code='" + code + '\'' +
				", lat=" + lat +
				", lon=" + lon +
				", dep=" + dep +
				", pc=" + pc +
				", sc=" + sc +
				'}';
	}
}
