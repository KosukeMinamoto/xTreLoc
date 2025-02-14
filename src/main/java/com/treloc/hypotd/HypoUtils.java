package com.treloc.hypotd;

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

// import net.sf.geographiclib.Geodesic;
// import net.sf.geographiclib.GeodesicData;

/*
 * Ray-tracing using TauP toolkit
 * @author: K.M.
 * @date: 2025/02/14
 * @version: 0.1
 * @description: The class is used to calculate the travel time 
 * and partial derivative matrix using TauP toolkit.
 * @usage: 
 */

 public class HypoUtils {
	private final VelocityModel velMod;
	private TauModel tauMod;

	public HypoUtils (ConfigLoader config) {
		String taupFile = config.getTaupFile();
		String extension = getFileExtension(config.getTaupFile());
		switch (extension) {
			case "":
				try {
					tauMod = TauModelLoader.load(taupFile);
				} catch (TauModelException e) {
					System.out.println("> You can use the following TauP models:");
					System.out.println("  - 1066a, see [Gilbert & Dziewonski, 1975]");
					System.out.println("  - 1066b, see [Gilbert & Dziewonski, 1975]");
					System.out.println("  - ak135, see [Kennet, Engdahl, & Buland, 1995]");
					System.out.println("  - herrin, see [Herrin, 1968]");
					System.out.println("  - iasp91, see [Kennet & Engdah, 1991]");
					System.out.println("  - jb, see [Jeffreys & Bullen, 1940]");
					System.out.println("  - prem, see [Dziewonski, 1981]");
					System.out.println("  - pwdk, see [Weber & Davis, 1990]");
					System.out.println("  - sp6, see [Morelli & Dziewonski, 1993]");
					System.exit(1);
				}
				// TauP_Time taup_time = new TauP_Time(modPath);
				// tauMod = taup_time.getTauModel();
				// tauMod.wfriteModel(modPath + extension);
				break;
			case "taup":
				try {
					tauMod = TauModel.readModel(taupFile);
				} catch (Exception e) {
					System.err.println("> Error in loading TauP model: " + e.getMessage());
				}
				break;
			default:
				System.out.println("> Unsupported file (only .tvel file are supported): " + extension);
		}
		velMod = tauMod.getVelocityModel();
		System.out.println("> Velocity model:\n" + velMod);
		// velMod.printGMT(modPath + ".gmt");
	}

	private static String getFileExtension(String fileName) {
		if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0) {
			return fileName.substring(fileName.lastIndexOf(".") + 1);
		} else {
			return "";
		}
	}

	/**
	 * Returns the partial derivative matrix and travel time.
	 * @param stnTable the station table
	 * @param idxList the index list
	 * @param hypoVector the hypo vector
	 * @return the partial derivative matrix and travel time
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
			try {
				double stnLat = stnTable[i][0];
				double stnLon = stnTable[i][1];
				double stnDep = stnTable[i][2];

				double azm = getAzimuth(hypLat, hypLon, stnLat, stnLon);
				double dis = getDistance2D(hypLat, hypLon, stnLat, stnLon);

				// GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
				// double azm = Math.toRadians(g.azi1);
				// double dis = Math.toDegrees(g.s12 / 1000 / 6371); // Distance in degree

				// Raytracing
				taup_time.depthCorrect(hypDep, stnDep);
				taup_time.setSourceDepth(hypDep);
				taup_time.calculate(dis);

				int fastIdx = 0;
				Arrival fastestArr = taup_time.getArrival(fastIdx);
				for (Arrival arr : taup_time.getArrivals()) {
					if (fastestArr.getTime() > arr.getTime()) {
						fastestArr = arr;
						fastIdx++;
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
							System.out.println("downgoing error: " + e);
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
			} catch (IndexOutOfBoundsException e) {
				System.out.println("Index out of bounds: " + e.getMessage());
			}
		}
		// System.out.println(Arrays.deepToString(dtdr));
		// System.out.println(Arrays.toString(tts));
		return new Object[]{dtdr, trvTime};
	}

	/**
	 * Returns the travel time.
	 * @param stnTable the station table
	 * @param idxList the index list
	 * @param hyp the hypo vector
	 * @return the travel time
	 */
	public double[] travelTime(double[][] stnTable, int[] idxList, double[] hyp)
		throws TauModelException {
		double hypLon = hyp[0];
		double hypLat = hyp[1];
		double hypDep = hyp[2];

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

			double dis = getDistance2D(hypLat, hypLon, stnLat, stnLon);
			// GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
			// double dis = Math.toDegrees(g.s12 / 1000 / 6371); // Distance in degree

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

	/*
	 * Convert residuals to weights
	 * @param resDiffTime the residuals
	 * @return the weights
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

	/*
	 * Convert residuals to weights
	 * @param resDiffTime the residuals
	 * @return the weights
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
}
