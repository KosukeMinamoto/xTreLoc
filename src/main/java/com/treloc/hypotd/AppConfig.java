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
import java.io.PrintWriter;
import java.io.FileNotFoundException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/*
 * Configuration class
 * @author: K.M.
 * @date: 2025/02/11
 * @version: 0.1
 * @description: The class is used to read the configuration file.
 * @usage: 
 */

public final class AppConfig {
	private int numJobs, numGrid;
	private double hypBottom, stnBottom, threshold;
	private String taumodFile, catalogFile, stnFile, datPattern;
	private double[][] stnTable;
	private String[] codes;
	private int clsPts=10;
	private double clsEps;

	public AppConfig readConfig(String configFilePath)  {
		ObjectMapper mapper = new ObjectMapper();
		try {
			JsonNode root = mapper.readTree(new File(configFilePath));
			this.numJobs = root.get("numJobs").asInt();
			this.numGrid = root.get("numGrid").asInt();
			this.hypBottom = root.get("hypBottom").asDouble();
			this.taumodFile = root.get("taumodFile").asText();
			this.catalogFile = root.get("catalogFile").asText();
			this.threshold = root.get("threshold").asDouble();
			this.datPattern = root.get("datPattern").asText();
			this.stnFile = root.get("stnFile").asText();
			
			this.clsEps = root.get("clsEps").asDouble();
			this.clsPts = root.get("clsPts").asInt();

			readStnTable(stnFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return this;
	}

	public void writeConfig(String configFile) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

		objectMapper.writeValue(new File(configFile), this);
	}

	

	private List<Path> seachFile(String pathPattern) {
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
			System.err.println(e);
		} 
		return datPathList; //.toArray(new Path[0]);
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

	public void setNumJobs( int numJobs ) {
		this.numJobs = numJobs;
	}

	public int getNumJobs() {
		return this.numJobs;
	}

	public void setNumGrid( int numGrid ) {
		this.numGrid = numGrid;
	}

	public int getNumGrid() {
		return this.numGrid;
	}

	public void setHypBottom( int hypBottom ) {
		this.hypBottom = hypBottom;
	}

	public double getHypBottom() {
		return this.hypBottom;
	}

	public void setThreshold( double threshold ) {
		this.threshold = threshold;
	}
	
	public double getThreshold() {
		return this.threshold;
	}

	public void setTaumodFile( String taumodFile ) {
		this.taumodFile = taumodFile;
	}

	public String getTaumodFile() {
		return this.taumodFile;
	}

	public void setCatalogFile( String catalogFile ) {
		this.catalogFile = catalogFile;
	}

	public String getCatalogFile() {
		return this.catalogFile;
	}

	public void setStnFile(String stnFile) {
		this.stnFile = stnFile;
	}

	public String getStnFile() {
		return this.stnFile;
	}

	public void setStationTable(double[][] stnTable) {
		this.stnTable = stnTable;
	}

	public void setCodes(String[] codes) {
		this.codes = codes;
	}

	public double[][] getStationTable (){
		return this.stnTable;
	}

	public void setStnBottom( double stnBottom ) {
		this.stnBottom = stnBottom;
	}

	public double getStnBottom() {
		return this.stnBottom;
	}

	public String[] getCodes () {
		return this.codes;
	}

	public void setDatPattern(String datPattern) {
		this.datPattern = datPattern;
	}

	public String getDatPattern() {
		return this.datPattern;
	}

	public Path[] getDatPaths () {
		String datPattern = getDatPattern();
		List<Path> pathList = seachFile(datPattern);
		return pathList.toArray(new Path[0]);
	}

	public int getClsPts () {
		return clsPts;
	}

	public double getClsEps() {
		return clsEps;
	}

	/**
	 * Write station table to a file.
	 * @param fileName The name of the file to write to
	 */
	public void writeStnTable( String fileName ) {
		try (PrintWriter writer = new PrintWriter(fileName)) {
			for (int i=0; i<stnTable.length; i++) {
				writer.print(codes[i] + " ");
				for (double value : stnTable[i]) {
					writer.print(value + " ");
				}
				writer.println();
			}
		} catch (FileNotFoundException e) {
			System.err.println("Error writing station table to file: " + e.getMessage());
		}
	}
}
