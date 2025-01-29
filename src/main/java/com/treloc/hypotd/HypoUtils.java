package com.treloc.hypotd;

// import java.util.Arrays;

import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.NoSuchLayerException;
import edu.sc.seis.TauP.NoSuchMatPropException;
import edu.sc.seis.TauP.TauModel;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import edu.sc.seis.TauP.VelocityLayer;
import edu.sc.seis.TauP.VelocityModel;

// import net.sf.geographiclib.Geodesic;
// import net.sf.geographiclib.GeodesicData;

/*
 * Ray-tracing using TauP toolkit
 * @author: K.M.
 * @date: 2025/01/26
 * @version: 0.1
 * @description: The class is used to calculate the travel time 
 * and partial derivative matrix using TauP toolkit.
 * @usage: 
 */

 public class HypoUtils {
	private final String modPath;
	private VelocityModel velMod;
	private TauModel tauMod;

	public HypoUtils (AppConfig config) throws Exception {
		modPath = config.getTaumodPath();
		String extension = getFileExtension(modPath);
		switch (extension) {
			case "":
				/*
				 * 1066a, see [Gilbert & Dziewonski, 1975]
				 * 1066b, see [Gilbert & Dziewonski, 1975]
				 * ak135, see [Kennet, Engdahl, & Buland, 1995]
				 * herrin, see [Herrin, 1968]
				 * iasp91, see [Kennet & Engdah, 1991]
				 * jb, see [Jeffreys & Bullen, 1940]
				 * prem, see [Dziewonski, 1981]
				 * pwdk, see [Weber & Davis, 1990]
				 * sp6, see [Morelli & Dziewonski, 1993]
				 */ 
				TauP_Time taup_time = new TauP_Time(modPath);
				tauMod = taup_time.getTauModel();
				// tauMod.wfriteModel(modPath + extension);
				break;
			case "taup":
				tauMod = TauModel.readModel(modPath);
				break;
			default:
				System.out.println("Unsupported file (only .tvel file are supported): " + extension);
		}
		velMod = tauMod.getVelocityModel();
		velMod.printGMT(modPath + ".gmt");
		System.out.println("Velocity model:\n" + velMod);
	}

	private String getFileExtension(String fileName) {
		if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0) {
			return fileName.substring(fileName.lastIndexOf(".") + 1);
		} else {
			return "";
		}
	}

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
						// TauP applies "fake neg velocity so angle is neg in case of upgoing"
						tak += 180.0;
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

				dtdr[i][0] = -Math.sin(tak) * Math.sin(azm) / sVel * App.deg2km; // dt/dx (lon)
				dtdr[i][1] = -Math.sin(tak) * Math.cos(azm) / sVel * App.deg2km * Math.cos(Math.toRadians(hypLat)); // dt/dy (lat)
				dtdr[i][2] = -Math.cos(tak) / sVel; // dt/dz (dep)

				// Theoritical travel time
				trvTime[i] = fastestArr.getTime() + stnTable[i][4];
			} catch (IndexOutOfBoundsException e) {
				e.printStackTrace();
			}
		}
		// System.out.println(Arrays.deepToString(dtdr));
		// System.out.println(Arrays.toString(tts));
		return new Object[]{dtdr, trvTime};
	}

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

	private double getAzimuth(double lat1, double lon1, double lat2, double lon2) {
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

	private double getDistance2D(double lat1, double lon1, double lat2, double lon2) {
		/*
		 * Calculate 2D distance between two points
		 * Return: Distance [deg]
		 */
		lon1 = Math.toRadians(lon1);
		lat1 = Math.toRadians(lat1);
		lon2 = Math.toRadians(lon2);
		lat2 = Math.toRadians(lat2);
		double theta = lon2 - lon1;
		double dist = Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1) * Math.cos(lat2) * Math.cos(theta);
		return Math.toDegrees(Math.acos(dist));
	}

	public static double[] residual2weight(RealVector resDiffTime) {
		/*
		 * Convert residuals to weights
		 * @param resDiffTime: Residuals [s]
		 * @return weights: Weights
		 */
		double[] weight = new double[resDiffTime.getDimension()];
		for (int i = 0; i < resDiffTime.getDimension(); i++) {
			double w = Math.abs(1 / resDiffTime.getEntry(i));
			// double w = Math.exp(-Math.pow(resDiffTime.getEntry(i), 2) / variance / variance);
			// double w = 1/Math.pow(resDiffTime.getEntry(i),2);
			weight[i] = w;
		}
		return weight;
	}

	public static double[] residual2weight(double[] resDiffTime) {
		/*
		 * Convert residuals to weights
		 * @param resDiffTime: Residuals [s]
		 * @return weights: Weights
		 */
		double[] weight = new double[resDiffTime.length];
		for (int i = 0; i < resDiffTime.length; i++) {
			double w = Math.abs(1 / resDiffTime[i]);
			// double w = Math.exp(-Math.pow(resDiffTime[i], 2) / variance / variance);
			weight[i] = w;
		}
		return weight;
	}

	public static double standardDeviation(double[] data) {
		double sum = 0.0, standardDeviation = 0.0;
		int length = data.length;

		for (double num : data) {
			sum += num;
		}
		double mean = sum / length;

		for (double num : data) {
			standardDeviation += Math.pow(num - mean, 2);
		}
		return Math.sqrt(standardDeviation / length);
	}

	public static double standardDeviation(RealVector data) {
		double sum = 0.0, standardDeviation = 0.0;
		int length = data.getDimension();

		for (int i = 0; i < length; i++) {
			sum += data.getEntry(i);
		}
		double mean = sum / length;

		for (int i = 0; i < length; i++) {
			standardDeviation += Math.pow(data.getEntry(i) - mean, 2);
		}
		return Math.sqrt(standardDeviation / length);
	}

	public static boolean containsNaN(double[] array) {
		for (double value : array) {
			if (Double.isNaN(value)) {
				return true;
			}
		}
		return false;
	}
}
