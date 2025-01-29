package com.treloc.hypotd;

import java.io.FileReader;
import java.io.BufferedReader;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.stream.IntStream;

import edu.sc.seis.TauP.TauModelException;

/*
 * Synthetic data generator
 * @author: K.M.
 * @date: 2025/01/26
 * @version: 0.1
 * @description: The class is used to generate the synthetic data.
 * @usage: 
 */

public class SyntheticTest extends HypoUtils {
	private AppConfig config;
	private final double[][] stnTable;
	private final String[] allCodes;
	private Random rand;
	private String catalogFilePath;
	private final double phsErr, locErr;
	private final int randomSeed;

	public SyntheticTest(AppConfig config) throws Exception {
		super(config);
		stnTable = config.getStationTable();
		allCodes = config.getCodes();
		catalogFilePath = config.getCatalogFilePath();

		phsErr = 0.1; // Second
		locErr = 0.03; // Degree

		randomSeed = 100;
		rand = new Random(randomSeed);
	}

	public void generateDataFromCatalog() {
		try (BufferedReader reader = new BufferedReader(new FileReader(catalogFilePath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("[A-Z]")) {
					continue;
				} 

				String[] data = line.split(",");
				generateData(
					Double.parseDouble(data[2]), // lon
					Double.parseDouble(data[1]), // lat
					Double.parseDouble(data[3]), // dep
					LocalDateTime.parse(data[0].trim()), // time
					data[9] // data file-path
					);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void generateData(double lon, double lat, double dep, LocalDateTime time, String dataFilePath) throws TauModelException {
		double[] hyp = new double[] { lon, lat, dep };
		double[][] lagTable = randomLagTime(hyp);

		DataHandler handler = new DataHandler(config);
		handler.setLatitude(lat + rand.nextGaussian() * locErr);
		handler.setLongitude(lon + rand.nextGaussian() * locErr);
		handler.setDepth(dep + rand.nextGaussian() * locErr * App.deg2km);
		handler.setLatitudeError(locErr);
		handler.setLongitudeError(locErr);
		handler.setDepthError(locErr * App.deg2km);
		handler.setResidual(-999);
		handler.setMethod("SYN");
		handler.setLagTable(lagTable);
		handler.write(dataFilePath, allCodes);

		System.out.println("Generated data: " + dataFilePath);
	}

	private double[][] randomLagTime(double[] hyp) throws TauModelException {
		// Create synthetic data of all station-pair
		int[] codeIdx = IntStream.rangeClosed(0, stnTable.length-1).toArray();
		double[] sTravelTime = travelTime(stnTable, codeIdx, hyp);

		int numAllPairs = stnTable.length * (stnTable.length - 1) / 2;
		double[][] allData = new double[numAllPairs][4];

		int count = 0;
		for (int i = 0; i < sTravelTime.length - 1; i++) {
			for (int j = i + 1; j < sTravelTime.length; j++) {
				allData[count][0] = i;
				allData[count][1] = j;
				allData[count][2] = sTravelTime[j] - sTravelTime[i] + rand.nextGaussian() * phsErr;
				allData[count][3] = 1;
				count++;
			}
		}

		// Select phase randomly
		int minPairs = (int)(numAllPairs * 0.2);
		int maxPairs = (int)(numAllPairs * 0.4); 
		int numRandomPairs = minPairs + rand.nextInt(maxPairs - minPairs + 1);

		// Shuffle indices using Fisher-Yates algorithm
		int[] indices = IntStream.rangeClosed(0, numAllPairs-1).toArray();
		for (int i = numAllPairs - 1; i > 0; i--) {
			int j = rand.nextInt(i + 1);
			int temp = indices[i];
			indices[i] = indices[j];
			indices[j] = temp;
		}

		double[][] selectedData = new double[numRandomPairs][4];
		for (int i = 0; i < numRandomPairs; i++) {
			selectedData[i] = allData[indices[i]];
		}
		return selectedData;
	}
}
