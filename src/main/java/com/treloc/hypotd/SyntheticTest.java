package com.treloc.hypotd;

import java.util.logging.Logger;
import java.util.Random;
import java.util.stream.IntStream;
import edu.sc.seis.TauP.TauModelException;

import org.apache.commons.math3.ml.clustering.Cluster;

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
	public void generateDataFromCatalog() throws TauModelException {
		Cluster<Point> points = loadPointsFromCatalog(catalogFile, false);
		for (Point point : points.getPoints()) {
			generateData(point, minSelectRate, maxSelectRate);
		}
	}

	/**
	 * Generates synthetic data for a given set of parameters.
	 *
	 * @param point         the point of the event
	 * @param minSelectRate the minimum selection rate for phase pairs
	 * @param maxSelectRate the maximum selection rate for phase pairs
	 * @throws TauModelException if there is an error in the Tau model
	 */
	public void generateData(Point pointTrue, double minSelectRate, double maxSelectRate) throws TauModelException {
		PointsHandler pointsHandler = new PointsHandler();
		if (pointTrue.getType().equals("REF")) {
			double[][] lagTable = randomLagTime(pointTrue, minSelectRate, maxSelectRate, false);
			pointTrue.setLagTable(lagTable);
			pointsHandler.setMainPoint(pointTrue);
			pointsHandler.writeDatFile(pointTrue.getFilePath(), codeStrings);
		} else {
			double[][] lagTable = randomLagTime(pointTrue, minSelectRate, maxSelectRate, true);

			Point point_perturbed = new Point();
			point_perturbed.setLat(pointTrue.getLat() + rand.nextGaussian() * locErr);
			point_perturbed.setLon(pointTrue.getLon() + rand.nextGaussian() * locErr);
			point_perturbed.setDep(pointTrue.getDep() + rand.nextGaussian() * locErr * App.deg2km);
			point_perturbed.setElat(locErr);
			point_perturbed.setElon(locErr);
			point_perturbed.setEdep(locErr * App.deg2km);
			point_perturbed.setRes(-999);
			point_perturbed.setType("SYN");
			point_perturbed.setLagTable(lagTable);
			pointsHandler.setMainPoint(point_perturbed);
			pointsHandler.writeDatFile(pointTrue.getFilePath(), codeStrings);
		} 
		logger.info("Generated synthetic data: " + pointTrue.getFilePath());
	}

	/**
	 * Generates random lag times for a given hypocenter and selection rates.
	 *
	 * @param point_true    the true hypocenter coordinates
	 * @param minSelectRate the minimum selection rate for phase pairs
	 * @param maxSelectRate the maximum selection rate for phase pairs
	 * @return a 2D array of selected lag times
	 */
	private double[][] randomLagTime (Point point, double minSelectRate, double maxSelectRate, boolean addPurturb) throws TauModelException {
		int[] codeIdx = IntStream.rangeClosed(0, stationTable.length-1).toArray();
		double[] sTravelTime = travelTime(stationTable, codeIdx, point);

		int numAllPairs = stationTable.length * (stationTable.length - 1) / 2;
		double[][] allData = new double[numAllPairs][4];

		int count = 0;
		for (int i = 0; i < sTravelTime.length - 1; i++) {
			for (int j = i + 1; j < sTravelTime.length; j++) {
				allData[count][0] = i;
				allData[count][1] = j;
				double lagErr = addPurturb ? rand.nextGaussian() * phsErr : 0;
				allData[count][2] = sTravelTime[j] - sTravelTime[i] + lagErr;
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
