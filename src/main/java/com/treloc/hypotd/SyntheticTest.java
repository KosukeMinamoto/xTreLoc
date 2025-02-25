package com.treloc.hypotd;

import java.io.FileReader;
import java.io.BufferedReader;
import java.util.logging.Logger;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.stream.IntStream;

import edu.sc.seis.TauP.TauModelException;

/**
 * SyntheticTest
 * This class is used to generate synthetic data for testing purposes.
 * It reads from a catalog file and generates synthetic data based on the provided parameters.
 * 
 * @version 0.1
 * @since 2025-02-17
 * @author K.M.
 */
public class SyntheticTest extends HypoUtils {
	private static final Logger logger = Logger.getLogger("com.treloc.hypotd");
	private final double[][] stationTable;
	private final String[] codeStrings;
	private String catalogFile;
	private final double phsErr = 0.1;
	private final double locErr = 0.03;
	private final double minSelectRate = 0.2;
	private final double maxSelectRate = 0.4;
	private final int randomSeed = 100;
	private Random rand = new Random(randomSeed);

	/**
	 * Constructs a SyntheticTest object with the specified configuration.
	 *
	 * @param appConfig the configuration loader containing necessary parameters
	 * @throws TauModelException if there is an error in the Tau model
	 */
	public SyntheticTest(ConfigLoader appConfig) throws TauModelException {
		super(appConfig);
		stationTable = appConfig.getStationTable();
		catalogFile = appConfig.getCatalogFile(appConfig.getMode());
		codeStrings = appConfig.getCodeStrings();
	}

	/**
	 * Generates synthetic data from the catalog file.
	 * It reads each line from the catalog and generates data based on the parsed information.
	 */
	public void generateDataFromCatalog() {
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
					data[8], // data file-path
					minSelectRate,
					maxSelectRate
					);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Generates synthetic data for a given set of parameters.
	 *
	 * @param lon           the longitude of the event
	 * @param lat           the latitude of the event
	 * @param dep           the depth of the event
	 * @param time          the time of the event
	 * @param dataFilePath  the file path to save the generated data
	 * @param minSelectRate the minimum selection rate for phase pairs
	 * @param maxSelectRate the maximum selection rate for phase pairs
	 * @throws TauModelException if there is an error in the Tau model
	 */
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
		pointsHandler.writeDatFile(dataFilePath, codeStrings);

		logger.info("Generated synthetic data: " + dataFilePath);
	}

	/**
	 * Generates random lag times for a given hypocenter and selection rates.
	 *
	 * @param hyp           the hypocenter coordinates
	 * @param minSelectRate the minimum selection rate for phase pairs
	 * @param maxSelectRate the maximum selection rate for phase pairs
	 * @return a 2D array of selected lag times
	 * @throws TauModelException if there is an error in the Tau model
	 */
	private double[][] randomLagTime(
		double[] hyp,
		double minSelectRate,
		double maxSelectRate) throws TauModelException {
		int[] codeIdx = IntStream.rangeClosed(0, stationTable.length-1).toArray();
		double[] sTravelTime = travelTime(stationTable, codeIdx, hyp);

		int numAllPairs = stationTable.length * (stationTable.length - 1) / 2;
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
