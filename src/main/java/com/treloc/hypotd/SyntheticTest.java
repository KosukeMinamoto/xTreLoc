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
	private final double[][] stnTable;
	private final String[] allCodes;
	private Random rand;
	private String catalogFile;
	private final double phsErr, locErr;
	private final int randomSeed = 100;

	public SyntheticTest(AppConfig config) {
		super(config);
		stnTable = config.getStationTable();
		allCodes = config.getCodes();
		catalogFile = config.getCatalogFile();

		phsErr = 0.1; // Second
		locErr = 0.03; // Degree
		rand = new Random(randomSeed);
	}

	public void generateDataFromCatalog(double minSelectRate, double maxSelectRate) {
		try (BufferedReader reader = new BufferedReader(new FileReader(catalogFile))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("[A-Z]")) {
					continue;
				} 

				String[] data = line.split(" ");
				generateData(
					Double.parseDouble(data[2]), // lon
					Double.parseDouble(data[1]), // lat
					Double.parseDouble(data[3]), // dep
					LocalDateTime.parse(data[0].trim()), // time
					data[9], // data file-path
					minSelectRate,
					maxSelectRate
					);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void generateData(
		double lon, double lat, double dep, LocalDateTime time, String dataFilePath,
		double minSelectRate, double maxSelectRate) throws TauModelException {
		double[] hyp = new double[] { lon, lat, dep };
		double[][] lagTable = randomLagTime(hyp, minSelectRate, maxSelectRate);

		PointsHandler pointsHandler = new PointsHandler();
		Point point = pointsHandler.getMainPoint();
		point.setLat(lat + rand.nextGaussian() * locErr);
		point.setLon(lon + rand.nextGaussian() * locErr);
		point.setDep(dep + rand.nextGaussian() * locErr * App.deg2km);
		point.setElat(locErr);
		point.setElon(locErr);
		point.setEdep(locErr * App.deg2km);
		point.setRes(-999);
		point.setType("SYN");
		point.setLagTable(lagTable);
		pointsHandler.writeDatFile(dataFilePath, allCodes);

		System.out.println("> Generated data: " + dataFilePath);
	}

	private double[][] randomLagTime(
		double[] hyp,
		double minSelectRate,
		double maxSelectRate) throws TauModelException {
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
		int minPairs = (int)(numAllPairs * minSelectRate);
		int maxPairs = (int)(numAllPairs * maxSelectRate); 
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
