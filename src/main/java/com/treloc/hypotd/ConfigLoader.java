package com.treloc.hypotd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuration class
 * @author K.M.
 * @since 2025-02-22
 * @version 0.1
 */

public final class ConfigLoader {
	private static final Logger logger = Logger.getLogger(ConfigLoader.class.getName());
	private final JsonNode config;
	private double stnBottom = 0;
	private List<Station> stationList;
	private String[] codeStrings;
	private double[][] stationTable;
	private Path outDir;
	private String mode;

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

	private static List<Path> searchFile(String dirString) {
		Path parentPath = Paths.get(dirString);
		List<Path> datPathList = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentPath)) {
			for (Path entry : stream) {
				datPathList.add(entry);
			}
		} catch (IOException e) {
			logger.warning("Warning: " + e.getMessage());
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
			logger.warning("Warning: Station file " + statinFile + " has not found" + e.getMessage());
		}
		return stationList;
	}

	public JsonNode getConfig () {
		return this.config;
	}

	public String getMode() {
		return this.mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	/**
	 * Get the log level.
	 *
	 * @return the log level. Choices are INFO, WARNING, or SEVERE.
	 */
	public String getLogLevel() {
		String[] logLevels = {"ALL", "FINER", "FINE", "CONFIG", "INFO", "WARNING", "SEVERE", "OFF"};
		if (config.has("logLevel")) {
			String logLevel = config.get("logLevel").asText();
			if (Arrays.asList(logLevels).contains(logLevel)) {
				return logLevel;
			} else {
				logger.warning("Warning: Log level must be one of the following: " + Arrays.toString(logLevels) + ". Use default val. logLevel=INFO");
				return "INFO";
			}
		} else {
			logger.warning("Warning: 'logLevel' parameter does not exist. Use default val. logLevel=INFO");
			return "INFO";
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
			throw new IllegalArgumentException("Warning: 'taupFile' parameter does not exist.");
		}
	}

	/**
	 * Get the catalog file.
	 *
	 * @param mode the mode to retrieve parameters for ("SYN", "CLS", "TRD", "MAP")
	 * @return the catalog file
	 */
	public String getCatalogFile(String mode) {
		if (config.has(mode) && config.get(mode).has("catalogFile")) {
			return config.get(mode).get("catalogFile").asText();
		} else {
			throw new IllegalArgumentException("Warning: '" + mode + ".catalogFile' parameter does not exist.");
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
			throw new IllegalArgumentException("Warning: 'stationFile' parameter does not exist.");
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
	 * Get the number of jobs to run.
	 *
	 * @return the number of jobs to run
	 */
	public int getNumJobs() {
		if (config.has("numJobs")) {
			return config.get("numJobs").asInt();
		} else {
			int defaultNumJobs = 1;
			logger.warning("Warning: 'numJobs' parameter does not exist. Running with single job.");
			return defaultNumJobs;
		}
	}

	/**
	 * Get the data pattern.
	 *
	 * @param mode the mode to retrieve parameters for ("GRD" or "STD")
	 * @return the data pattern
	 */
	// public String getDatPattern(String mode) {
	// 	if (config.has(mode) && config.get(mode).has("datPattern")) {
	// 		return config.get(mode).get("datPattern").asText();
	// 	} else {
	// 		throw new IllegalArgumentException("Warning: '" + mode + ".datPattern' parameter does not exist.");
	// 	}
	// }

	/**
	 * Get the data directory.
	 *
	 * @param mode the mode to retrieve parameters for ("GRD" or "STD")
	 * @return the data directory
	 */
	public String getDatDirectory(String mode) {
		if (config.has(mode) && config.get(mode).has("datDirectory")) {
			return config.get(mode).get("datDirectory").asText();
		} else {
			throw new IllegalArgumentException("Warning: '" + mode + ".datDirectory' parameter does not exist.");
		}
	}

	/**
	 * Get the data paths.
	 *
	 * @param mode the mode to retrieve parameters for ("GRD" or "STD")
	 * @return the data paths
	 */
	public Path[] getDatPaths (String mode) {
		return searchFile(getDatDirectory(mode)).toArray(new Path[0]);
	}

	/**
	 * Get the number of grids to use on the grid search.
	 *
	 * @return the number of grids to use on the grid search
	 */
	public int getNumGrid() {
		if (config.has("GRD") && config.get("GRD").has("numGrid")) {
			return config.get("GRD").get("numGrid").asInt();
		} else {
			int defaultNumGrid = 100;
			logger.warning("Warning: 'GRD.numGrid' parameter does not exist. Use default val. numGrid=" + defaultNumGrid);
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
			logger.warning("Warning: 'hypBottom' parameter does not exist. Use default val. hypBottom="
					+ defaultHypBottom);
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
			logger.warning(
					"Warning: 'threshold' parameter does not exist. Use default val. threshold=" + defaultThreshold);
			return defaultThreshold;
		}
	}

	/**
	 * Get the iteration number for the each step on the triple difference.
	 *
	 * @return the iteration number for the each step on the triple difference
	 */
	public int[] getTrpIterNum() {
		if (config.has("TRD") && config.get("TRD").has("iterNum")) {
			JsonNode iterNumNode = config.get("TRD").get("iterNum");
			int[] iterNumArray = new int[iterNumNode.size()];
			for (int i = 0; i < iterNumNode.size(); i++) {
				iterNumArray[i] = iterNumNode.get(i).asInt();
			}
			return iterNumArray;
		} else {
			throw new IllegalArgumentException("Warning: 'TRD.iterNum' parameter does not exist.");
		}
	}

	/**
	 * Get the distance btwn connected events in kilometers.
	 *
	 * @return the distance btwn connected events in kilometers
	 */
	public int[] getTrpDistKm() {
		if (config.has("TRD") && config.get("TRD").has("distKm")) {
			JsonNode distKmNode = config.get("TRD").get("distKm");
			int[] distKmArray = new int[distKmNode.size()];
			for (int i = 0; i < distKmNode.size(); i++) {
				distKmArray[i] = distKmNode.get(i).asInt();
			}
			return distKmArray;
		} else {
			throw new IllegalArgumentException("Warning: 'TRD.distKm' parameter does not exist.");
		}
	}

	/**
	 * Get the damping factors for the each step on the triple difference.
	 *
	 * @return the damping factors for the each step on the triple difference
	 */
	public int[] getTrpDampFact() {
		if (config.has("TRD") && config.get("TRD").has("dampFact")) {
			JsonNode dampFactNode = config.get("TRD").get("dampFact");
			int[] dampFactArray = new int[dampFactNode.size()];
			for (int i = 0; i < dampFactNode.size(); i++) {
				dampFactArray[i] = dampFactNode.get(i).asInt();
			}
			return dampFactArray;
		} else {
			throw new IllegalArgumentException("Warning: 'TRD.dampFact' parameter does not exist.");
		}
	}

	/**
	 * Get the cluster epsilon on the DBSCAN.
	 *
	 * @return the cluster epsilon on the DBSCAN
	 */
	public double getClsEps() {
		if (config.has("CLS") && config.get("CLS").has("eps")) {
			return config.get("CLS").get("eps").asDouble();
		} else {
			throw new IllegalArgumentException("Warning: 'CLS.eps' parameter does not exist.");
		}
	}

	/**
	 * Get the number of points in the cluster on the DBSCAN.
	 *
	 * @return the number of points in the cluster
	 */
	public int getClsPts() {
		if (config.has("CLS") && config.get("CLS").has("minPts")) {
			return config.get("CLS").get("minPts").asInt();
		} else {
			throw new IllegalArgumentException("Warning: 'CLS.minPts' parameter does not exist.");
		}
	}

	/**
	 * Get the shapefile for the map.
	 *
	 * @return the shapefile for the map
	 */
	// public String getShpFile() {
	// 	if (config.has("MAP") && config.get("MAP").has("shpFile")) {
	// 		return config.get("MAP").get("shpFile").asText();
	// 	} else {
	// 		throw new IllegalArgumentException("Warning: 'MAP.shpFile' parameter does not exist.");
	// 	}
	// }

	public Path getOutDir() {
		return outDir;
	}

	public void setOutDir(Path outDir) {
		this.outDir = outDir;
	}

	public String getShapefilePath() {
		if (config.get("MAP").has("shpFile")) {
			return config.get("MAP").get("shpFile").asText();
		} else {
			throw new IllegalArgumentException("Warning: 'MAP.shpFile' parameter does not exist.");
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
