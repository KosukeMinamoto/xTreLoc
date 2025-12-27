package com.treloc.xtreloc.solver;

import java.util.logging.Logger;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.FileUtils;

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
    private static final Logger logger = Logger.getLogger(HypoUtils.class.getName());
    /** Conversion factor from degrees to kilometers (approximately 111.32 km per degree) */
    private static final double DEG2KM = 111.32;
    
    private final VelocityModel velMod;
    private TauModel tauMod;
    private final double threshold;

    /**
     * Constructs a HypoUtils object with the specified configuration.
     * Loads the TauP model based on the provided configuration.
     *
     * @param config the application configuration
     */
    public HypoUtils(AppConfig config) throws TauModelException {
        this.threshold = config.threshold;

        String taupFile = config.taupFile;
        if (taupFile == null || taupFile.isEmpty()) {
            throw new IllegalArgumentException("TauP file path is not specified");
        }

        String[] defaultModels = {"prem", "iasp91", "ak135", "ak135f"};
        boolean isDefaultModel = false;
        for (String model : defaultModels) {
            if (taupFile.equalsIgnoreCase(model)) {
                isDefaultModel = true;
                break;
            }
        }
        
        if (isDefaultModel) {
            try {
                tauMod = TauModelLoader.load(taupFile);
            } catch (TauModelException e) {
                logger.severe("Error loading TauP default model: " + e.getMessage());
                throw e;
            }
        } else {
            String extension = FileUtils.getFileExtension(taupFile);
            switch (extension) {
                case "":
                case "nd":
                    try {
                        tauMod = TauModelLoader.load(taupFile);
                    } catch (TauModelException e) {
                        logger.severe("Error loading TauP model: " + e.getMessage());
                        throw e;
                    }
                    break;
                case "taup":
                    try {
                        tauMod = TauModel.readModel(taupFile);
                    } catch (Exception e) {
                        logger.severe("Error: Loading TauP model: " + e.getMessage());
                        throw new TauModelException("Failed to load TauP model", e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported file extension: " + extension);
            }
        }
        velMod = tauMod.getVelocityModel();
        logger.info("Loaded velocity model: " + taupFile);
    }

    /**
     * Calculates S-wave travel times from the hypocenter to specified stations.
     * Uses the TauP toolkit for ray-tracing calculations with depth corrections.
     *
     * @param stnTable the station table with columns [lat, lon, dep, pc, sc]
     *                  where dep is in kilometers (positive, depth below surface)
     * @param idxList  the index list of stations to use
     * @param point    the hypocenter coordinates
     * @return an array of travel times in seconds (Double.MAX_VALUE for invalid stations)
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

        double[] trvTime = new double[stnTable.length];
        for (int i : idxList) {
            double stnLat = stnTable[i][0];
            double stnLon = stnTable[i][1];
            double stnDep = stnTable[i][2];

            if (stnDep <= 0) {
                logger.warning(String.format("Invalid station depth for station %d: stnDep=%.6f (should be positive km)", 
                    i, stnDep));
                trvTime[i] = Double.MAX_VALUE;
                continue;
            }

            GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
            double dis = Math.toDegrees(g.s12 / 1000.0 / 6371.0);

            try {
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
            } catch (Exception e) {
                logger.warning(String.format("Depth correction failed for station %d: hypDep=%.3f, stnDep=%.3f, error=%s", 
                    i, hypDep, stnDep, e.getMessage()));
                trvTime[i] = Double.MAX_VALUE;
            }
        }
        return trvTime;
    }

    /**
     * Converts residuals to weights for a given array of residuals.
     * Uses the inverse of absolute residual values as weights.
     *
     * @param resDiffTime the residuals as an array
     * @return an array of weights (inverse of absolute residuals)
     */
    public static double[] residual2weight(double[] resDiffTime) {
        double[] weight = new double[resDiffTime.length];
        for (int i = 0; i < resDiffTime.length; i++) {
            double w = Math.abs(1.0 / (resDiffTime[i] + 1e-10));
            weight[i] = w;
        }
        return weight;
    }

    /**
     * Calculates the partial derivative matrix for travel time with respect to hypocenter coordinates.
     * Uses analytical calculation based on take-off angle and azimuth.
     * Units: dtdr[i][0] = dt/dlon [s/deg], dtdr[i][1] = dt/dlat [s/deg], dtdr[i][2] = dt/ddep [s/km]
     * 
     * @param stnTable the station table with columns [lat, lon, dep, pc, sc]
     * @param idxList  the index list of stations to use
     * @param point    the hypocenter coordinates
     * @return an Object array containing [dtdr (double[][]), trvTime (double[])]
     *         where dtdr[i][0] = dt/dlon [s/deg], dtdr[i][1] = dt/dlat [s/deg], dtdr[i][2] = dt/ddep [s/km]
     * @throws TauModelException if there is an error in the Tau model
     * @throws NoSuchLayerException if a specified layer does not exist
     * @throws NoSuchMatPropException if a specified material property does not exist
     */
    public Object[] partialDerivativeMatrix(double[][] stnTable, int[] idxList, Point point)
        throws TauModelException, NoSuchLayerException, NoSuchMatPropException {
        double hypLon = point.getLon();
        double hypLat = point.getLat();
        double hypDep = point.getDep();

        int layerNumber = velMod.layerNumberBelow(hypDep);
        VelocityLayer velocityLayer = velMod.getVelocityLayer(layerNumber);
        double sVel = velocityLayer.evaluateAt(hypDep, 's');

        double[][] dtdr = new double[stnTable.length][3];
        double[] trvTime = new double[stnTable.length];

        TauP_Time taup_time = new TauP_Time();
        taup_time.setTauModel(tauMod);
        taup_time.clearPhaseNames();
        taup_time.clearArrivals();
        taup_time.clearPhases();
        taup_time.parsePhaseList("tts");

        for (int i : idxList) {
            double stnLat = stnTable[i][0];
            double stnLon = stnTable[i][1];
            double stnDep = stnTable[i][2];

            GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
            double azm = Math.toRadians(g.azi1);
            double dis = Math.toDegrees(g.s12 / 1000.0 / 6371.0);

            taup_time.depthCorrect(hypDep, stnDep);
            taup_time.setSourceDepth(hypDep);
            taup_time.calculate(dis);

            Arrival fastestArr = taup_time.getArrival(0);
            for (Arrival arr : taup_time.getArrivals()) {
                if (fastestArr.getTime() > arr.getTime()) {
                    fastestArr = arr;
                }
            }

            double tak = fastestArr.getTakeoffAngle();
            if (!Double.isNaN(tak)) {
                if (tak < 0.0) {
                    tak += 180.0;
                }
                tak = Math.toRadians(tak);
            } else {
                double p = fastestArr.getRayParam();
                double dip = Math.asin(p * sVel);
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
                    tak = Math.PI - dip;
                } else {
                    tak = dip;
                }
            }

            dtdr[i][0] = -Math.sin(tak) * Math.sin(azm) / sVel * DEG2KM * Math.cos(Math.toRadians(hypLat));
            dtdr[i][1] = -Math.sin(tak) * Math.cos(azm) / sVel * DEG2KM;
            dtdr[i][2] = -Math.cos(tak) / sVel;

            trvTime[i] = fastestArr.getTime() + stnTable[i][4];
        }
        return new Object[]{dtdr, trvTime};
    }

    /**
     * Gets the TauModel instance.
     * Protected access for subclasses.
     *
     * @return the TauModel instance
     */
    protected TauModel getTauModel() {
        return tauMod;
    }

    /**
     * Gets the conversion factor from degrees to kilometers.
     *
     * @return the DEG2KM constant (approximately 111.32 km per degree)
     */
    public static double getDeg2Km() {
        return DEG2KM;
    }
}

