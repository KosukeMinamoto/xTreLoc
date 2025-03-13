package com.treloc.hypotd;

import java.io.IOException;
import java.util.logging.Logger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.NoSuchLayerException;
import edu.sc.seis.TauP.NoSuchMatPropException;
import edu.sc.seis.TauP.TauModel;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauModelLoader;
import edu.sc.seis.TauP.TauP_Time;
import edu.sc.seis.TauP.VelocityLayer;
import edu.sc.seis.TauP.VelocityModel;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

import org.apache.commons.math3.ml.clustering.Cluster;

/**
 * HypoUtils
 * This class provides utility methods for ray-tracing and travel time calculations
 * using the TauP toolkit.
 * 
 * @version 0.1
 * @since 2025-02-22
 * @author K.M.
 */
public class HypoUtils {
	private static final Logger logger = Logger.getLogger("com.treloc.hypotd");
	private final VelocityModel velMod;
	private TauModel tauMod;
	private String[] codeStrings;
	private double threshold;

	/**
	 * Constructs a HypoUtils object with the specified configuration.
	 * Loads the TauP model based on the provided configuration.
	 *
	 * @param config the configuration loader containing necessary parameters
	 */
	public HypoUtils(ConfigLoader config) {
		codeStrings = config.getCodeStrings();
		threshold = config.getThreshold();

		String taupFile = config.getTaupFile();
		String extension = getFileExtension(config.getTaupFile());
		switch (extension) {
			case "":
				try {
					tauMod = TauModelLoader.load(taupFile);
				} catch (TauModelException e) {
					logger.severe("Error loading TauP model: " + e.getMessage());
					System.exit(1);
				}
				break;
			case "taup":
				try {
					tauMod = TauModel.readModel(taupFile);
				} catch (Exception e) {
					logger.severe("Error: Loading TauP model: " + e.getMessage());
					System.exit(1);
				}
				break;
			default:
				logger.severe("Error: Unsupported file (only .taup file are supported): " + extension);
				System.exit(1);
		}
		velMod = tauMod.getVelocityModel();
		logger.info("Loaded velocity model:\n" + velMod);
	}

	/**
	 * Retrieves the file extension from the given file name.
	 *
	 * @param fileName the name of the file
	 * @return the file extension
	 */
	private static String getFileExtension(String fileName) {
		if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0) {
			return fileName.substring(fileName.lastIndexOf(".") + 1);
		} else {
			return "";
		}
	}

	/**
	 * Sets up the output directory based on the application configuration.
	 * Creates the directory if it does not exist.
	 *
	 * @param appConfig the application configuration loader
	 * @throws IOException if an I/O error occurs
	 */
	public static void setUpOutputDirectory(ConfigLoader appConfig) throws IOException {
		Path parentDir = Paths.get(appConfig.getDatDirectory(appConfig.getMode()));
		String parentDirName = parentDir.getFileName().toString();
		Path outDir = parentDir.resolveSibling(parentDirName + "_" + appConfig.getMode());
		if (!Files.exists(outDir)) {
			Files.createDirectories(outDir);
			logger.info("Created output directory: " + outDir);
		}
		appConfig.setOutDir(outDir);
	}

	/**
	 * Calculates the partial derivative matrix and travel time for the given parameters.
	 *
	 * @param stnTable   the station table
	 * @param idxList    the index list
	 * @param hypoVector the hypo vector
	 * @return an array containing the partial derivative matrix and travel time
	 * @throws TauModelException if there is an error in the Tau model
	 * @throws NoSuchLayerException if a specified layer does not exist
	 * @throws NoSuchMatPropException if a specified material property does not exist
	 */
	public Object[] partialDerivativeMatrix(double[][] stnTable, int[] idxList, RealVector hypoVector)
			throws TauModelException, NoSuchLayerException, NoSuchMatPropException {
		double hypLon = hypoVector.getEntry(0); // lon
		double hypLat = hypoVector.getEntry(1); // lat
		double hypDep = hypoVector.getEntry(2); // dep

		int layerNumber = velMod.layerNumberBelow(hypDep);
		VelocityLayer velocityLayer = velMod.getVelocityLayer(layerNumber);
		double sVel = velocityLayer.evaluateAt(hypDep, 's');

		double[][] dtdr = new double[stnTable.length][3];
		double[] trvTime = new double[stnTable.length]; // S-wave travel time

		TauP_Time taup_time = new TauP_Time();
		taup_time.setTauModel(tauMod);
		taup_time.clearPhaseNames();
		taup_time.clearArrivals();
		taup_time.clearPhases();
		taup_time.parsePhaseList("tts");

		for (int i : idxList) {
		// for (int i=0; i<stnTable.length; i++) {
			double stnLat = stnTable[i][0];
			double stnLon = stnTable[i][1];
			double stnDep = stnTable[i][2];

			// double azm = getAzimuth(hypLat, hypLon, stnLat, stnLon);
			// double dis = getDistance2D(hypLat, hypLon, stnLat, stnLon);

			GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
			double azm = Math.toRadians(g.azi1);
			double dis = Math.toDegrees(g.s12 / 1000 / 6371); // Distance in degree

			// Raytracing
			taup_time.depthCorrect(hypDep, stnDep);
			taup_time.setSourceDepth(hypDep);
			taup_time.calculate(dis);

			Arrival fastestArr = taup_time.getArrival(0);
			for (Arrival arr : taup_time.getArrivals()) {
				if (fastestArr.getTime() > arr.getTime()) {
					fastestArr = arr;
				}
			}

			// Calc take-off angle
			double tak = fastestArr.getTakeoffAngle();
			if (!Double.isNaN(tak)) {
				if (tak < 0.0) {
					tak += 180.0; // TauP applies "fake neg velocity so angle is neg in case of upgoing"
				}
				tak = Math.toRadians(tak);
			}
			else {
				// double p = fastestArr.getRayParamDeg();
				double p = fastestArr.getRayParam();
				double dip = Math.asin(p * sVel); // Radian
				boolean downgoing = true;
				int branch = fastestArr.getPhase().getTauModel().getSourceBranch();
				while (true) {
					try {
						downgoing = ((Boolean) (fastestArr.getPhase().getDownGoing()[branch])).booleanValue();
						break;
					} catch (Exception e) {
						if (branch > 0) {
							branch--;
							continue;
						}
						logger.warning("Warning: downgoing error: " + e);
						break;
					}
				}
				if (!downgoing) {
					tak = Math.PI - dip; // Radian
				}
			}

			dtdr[i][0] = -Math.sin(tak) * Math.sin(azm) / sVel * App.deg2km * Math.cos(Math.toRadians(hypLat)); // dt/dx (lon)
			dtdr[i][1] = -Math.sin(tak) * Math.cos(azm) / sVel * App.deg2km; // dt/dy (lat)
			dtdr[i][2] = -Math.cos(tak) / sVel; // dt/dz (dep)

			// Theoritical travel time
			trvTime[i] = fastestArr.getTime() + stnTable[i][4];
		}
		return new Object[]{dtdr, trvTime};
	}

	/**
	 * Calculates the travel time for the given parameters.
	 *
	 * @param stnTable the station table
	 * @param idxList  the index list
	 * @param point    the hypocenter coordinates
	 * @return an array of travel times
	 * @throws TauModelException if there is an error in the Tau model
	 */
	public double[] travelTime(double[][] stnTable, int[] idxList, Point point)
		throws TauModelException {
		double hypLon = point.getLon();
		double hypLat = point.getLat();
		double hypDep = point.getDep();

		TauP_Time taup_time = new TauP_Time();
		taup_time.setTauModel(tauMod);
		taup_time.clearPhaseNames();
		taup_time.clearArrivals();
		taup_time.clearPhases();
		taup_time.parsePhaseList("tts");

		double[] trvTime = new double[stnTable.length]; // S-wave travel time
		for (int i : idxList) {
			double stnLat = stnTable[i][0];
			double stnLon = stnTable[i][1];
			double stnDep = stnTable[i][2];

			// double dis = getDistance2D(hypLat, hypLon, stnLat, stnLon);
			GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
			double dis = Math.toDegrees(g.s12 / 1000 / 6371); // Distance in degree

			taup_time.depthCorrect(hypDep, stnDep);
			taup_time.setSourceDepth(hypDep);
			taup_time.calculate(dis);

			Arrival fastestArr = taup_time.getArrival(0);
			for (Arrival arr : taup_time.getArrivals()) {
				if (fastestArr.getTime() > arr.getTime()) {
					fastestArr = arr;
				}
			}
			trvTime[i] = fastestArr.getTime() + stnTable[i][4];
		}
		return trvTime;
	}

	/**
	 * Returns the azimuth angle between two points.
	 * @param lat1 the latitude of the first point
	 * @param lon1 the longitude of the first point
	 * @param lat2 the latitude of the second point
	 * @param lon2 the longitude of the second point
	 * @return the azimuth angle
	 * @deprecated Use Geodesic.WGS84.Inverse() instead
	 */
	public static double getAzimuth(double lat1, double lon1, double lat2, double lon2) {
		/*
		 * Calculate azimuth angle between two points
		 * Return: Azimuth angle [rad]
		 */
		lon1 = Math.toRadians(lon1);
		lat1 = Math.toRadians(lat1);
		lon2 = Math.toRadians(lon2);
		lat2 = Math.toRadians(lat2);

		double dLon = lon2 - lon1;
		double y = Math.sin(dLon) * Math.cos(lat2);
		double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
		double azimuth = Math.atan2(y, x);
		return azimuth;
	}

	/*
	 * Calculate 2D distance between two points
	 * @param lat1 the latitude of the first point
	 * @param lon1 the longitude of the first point
	 * @param lat2 the latitude of the second point
	 * @param lon2 the longitude of the second point
	 * @return the distance in degree
	 * @deprecated Use Geodesic.WGS84.Inverse() instead
	 */
	public static double getDistance2D(double lat1, double lon1, double lat2, double lon2) {
		lon1 = Math.toRadians(lon1);
		lat1 = Math.toRadians(lat1);
		lon2 = Math.toRadians(lon2);
		lat2 = Math.toRadians(lat2);
		double theta = lon2 - lon1;
		double dist = Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1) * Math.cos(lat2) * Math.cos(theta);
		return Math.toDegrees(Math.acos(dist));
	}

	/**
	 * Converts residuals to weights for a given residual vector.
	 *
	 * @param resDiffTime the residuals as a RealVector
	 * @return an array of weights
	 */
	public static double[] residual2weight(RealVector resDiffTime) {
		double[] weight = new double[resDiffTime.getDimension()];
		for (int i = 0; i < resDiffTime.getDimension(); i++) {
			double w = Math.abs(1 / resDiffTime.getEntry(i));
			// double w = Math.exp(-Math.pow(resDiffTime.getEntry(i), 2) / variance / variance);
			// double w = 1/Math.pow(resDiffTime.getEntry(i),2);
			weight[i] = w;
		}
		return weight;
	}

	/**
	 * Converts residuals to weights for a given array of residuals.
	 *
	 * @param resDiffTime the residuals as an array
	 * @return an array of weights
	 */
	public static double[] residual2weight(double[] resDiffTime) {
		double[] weight = new double[resDiffTime.length];
		for (int i = 0; i < resDiffTime.length; i++) {
			double w = Math.abs(1 / resDiffTime[i]);
			// double w = Math.exp(-Math.pow(resDiffTime[i], 2) / variance / variance);
			weight[i] = w;
		}
		return weight;
	}


	/**
	 * Loads the points from the specified catalog file.
	 *
	 * @param catalogFile  the path to the catalog file
	 * @param withLagTable if true, lagTables are also read
	 * @return the cluster of loaded points
	 */
	public Cluster<Point> loadPointsFromCatalog(String catalogFile, boolean withLagTable) {
		Cluster<Point> cluster = new Cluster<>();
		try (BufferedReader br = new BufferedReader(new FileReader(catalogFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\\s+");
				PointsHandler pointsHandler = new PointsHandler();

				if (withLagTable) {
					pointsHandler.readDatFile(parts[8], codeStrings, threshold);
					if (pointsHandler.getMainPoint().getLagTable().length < 4) {
						logger.warning("Error: Not enough data (< 4 pks.) to read in: " + parts[8]);
						continue;
					}
				}

				Point point = pointsHandler.getMainPoint();
				point.setTime(parts[0]);
				point.setLat(Double.parseDouble(parts[1]));
				point.setLon(Double.parseDouble(parts[2]));
				point.setDep(Double.parseDouble(parts[3]));
				point.setElat(Double.parseDouble(parts[4]));
				point.setElon(Double.parseDouble(parts[5]));
				point.setEdep(Double.parseDouble(parts[6]));
				point.setRes(Double.parseDouble(parts[7]));
				point.setFilePath(parts[8]);
				point.setType(parts[9]);

				int cid = -1;
				if (parts.length > 10) {
					cid = Integer.parseInt(parts[10]);
				}
				point.setCid(cid);
				cluster.addPoint(point);
			}
		} catch (IOException e) {
			logger.warning("Error: reading " + catalogFile + ": " + e.getMessage());
		}
		return cluster;
	}

	/**
	 * Loads the points from the specified catalog file for a specific cluster ID.
	 *
	 * @param catalogFile  the path to the catalog file
	 * @param withLagTable if true, lagTables are also read
	 * @param clusterId    the cluster ID of events to read in
	 * @return the cluster of loaded points
	 */
	public Cluster<Point> loadPointsFromCatalog(String catalogFile, boolean withLagTable, int clusterId) {
		Cluster<Point> cluster = new Cluster<>();
		try (BufferedReader br = new BufferedReader(new FileReader(catalogFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\\s+");

				int cid = Integer.parseInt(parts[10]);
				if (cid != clusterId) {
					continue;
				}

				PointsHandler pointsHandler = new PointsHandler();

				if (withLagTable) {
					pointsHandler.readDatFile(parts[8], codeStrings, threshold);
					if (pointsHandler.getMainPoint().getLagTable().length < 4) {
						logger.warning("Error: Not enough data (< 4 pks.) to read in: " + parts[8]);
						continue;
					}
				}

				Point point = pointsHandler.getMainPoint();
				point.setTime(parts[0]);
				point.setLat(Double.parseDouble(parts[1]));
				point.setLon(Double.parseDouble(parts[2]));
				point.setDep(Double.parseDouble(parts[3]));
				point.setElat(Double.parseDouble(parts[4]));
				point.setElon(Double.parseDouble(parts[5]));
				point.setEdep(Double.parseDouble(parts[6]));
				point.setRes(Double.parseDouble(parts[7]));
				point.setFilePath(parts[8]);
				point.setType(parts[9]);
				point.setCid(cid);

				cluster.addPoint(point);
			}
		} catch (IOException e) {
			logger.warning("Error: reading " + catalogFile + ": " + e.getMessage());
		}
		return cluster;
	}

	/**
	 * Writes the list of points to the specified output file.
	 *
	 * @param points     the list of points to write
	 * @param outputFile the path of the output file
	 */
	public void writePointsToFile(List<Point> points, String outputFile) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
			for (Point point : points) {
				writer.printf("%s %f %f %f %f %f %f %f %s %s %d%n",
						point.getTime(),
						point.getLat(),
						point.getLon(),
						point.getDep(),
						point.getElat(),
						point.getElon(),
						point.getEdep(),
						point.getRes(),
						point.getFilePath(),
						point.getType(),
						point.getCid());
			}
		} catch (IOException e) {
			logger.warning("Error: Writing points to " + outputFile + ": " + e.getMessage());
		}
	}
}
