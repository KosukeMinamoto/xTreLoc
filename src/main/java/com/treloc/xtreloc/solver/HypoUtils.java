package com.treloc.xtreloc.solver;

import java.io.File;
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
    
    /** Distance threshold for cache reuse (0.01 degrees ≈ 1.1 km) */
    private static final double CACHE_DISTANCE_THRESHOLD = 0.01;
    
    private final VelocityModel velMod;
    private TauModel tauMod;
    private final double threshold;
    
    // Cache for TauModel loading to avoid serialization issues
    private static java.util.Map<String, TauModel> modelCache = new java.util.HashMap<>();

    
    private static class TravelTimeCacheEntry {
        double lon;
        double lat;
        double dep;
        int[] usedIdx;
        double[] trvTime;
        
        boolean matches(double lon, double lat, double dep, int[] usedIdx) {
            if (this.trvTime == null || this.usedIdx == null) {
                return false;
            }
            if (this.usedIdx.length != usedIdx.length) {
                return false;
            }
            GeodesicData g = Geodesic.WGS84.Inverse(this.lat, this.lon, lat, lon);
            double distanceDeg = Math.toDegrees(g.s12 / 1000.0 / 6371.0);
            double depthDiff = Math.abs(this.dep - dep);
            if (distanceDeg > CACHE_DISTANCE_THRESHOLD || depthDiff > 0.1) {
                return false;
            }
            for (int i = 0; i < usedIdx.length; i++) {
                if (this.usedIdx[i] != usedIdx[i]) {
                    return false;
                }
            }
            return true;
        }
    }
    
    private static class PartialDerivativeCacheEntry {
        double lon;
        double lat;
        double dep;
        int[] usedIdx;
        double[][] dtdr;
        double[] trvTime;
        
        boolean matches(double lon, double lat, double dep, int[] usedIdx) {
            if (this.dtdr == null || this.trvTime == null || this.usedIdx == null) {
                return false;
            }
            if (this.usedIdx.length != usedIdx.length) {
                return false;
            }
            GeodesicData g = Geodesic.WGS84.Inverse(this.lat, this.lon, lat, lon);
            double distanceDeg = Math.toDegrees(g.s12 / 1000.0 / 6371.0);
            double depthDiff = Math.abs(this.dep - dep);
            if (distanceDeg > CACHE_DISTANCE_THRESHOLD || depthDiff > 0.1) {
                return false;
            }
            for (int i = 0; i < usedIdx.length; i++) {
                if (this.usedIdx[i] != usedIdx[i]) {
                    return false;
                }
            }
            return true;
        }
    }
    
    private TravelTimeCacheEntry travelTimeCache = null;
    private PartialDerivativeCacheEntry partialDerivativeCache = null;

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
            } catch (Exception e) {
                // Try clearing cache and retry for serialization issues
                if (e.getMessage() != null && e.getMessage().contains("serialVersionUID")) {
                    logger.info("TauModel serialization issue detected. Trying alternative loading method...");
                    try {
                        tauMod = loadTauModelWithFallback(taupFile);
                    } catch (Exception retryError) {
                        logger.severe("Error loading TauP default model after retry: " + retryError.getMessage());
                        throw new TauModelException("Failed to load TauP model", retryError);
                    }
                } else {
                    logger.severe("Error loading TauP default model: " + e.getMessage());
                    throw new TauModelException("Failed to load TauP model", e);
                }
            }
        } else {
            String extension = FileUtils.getFileExtension(taupFile);
            switch (extension) {
                case "":
                case "nd":
                    try {
                        tauMod = TauModelLoader.load(taupFile);
                    } catch (Exception e) {
                        // Try alternative loading on serialization error
                        if (e.getMessage() != null && e.getMessage().contains("serialVersionUID")) {
                            logger.info("TauModel serialization issue detected. Trying alternative loading method...");
                            try {
                                tauMod = loadTauModelWithFallback(taupFile);
                            } catch (Exception retryError) {
                                logger.severe("Error loading TauP model after retry: " + retryError.getMessage());
                                throw new TauModelException("Failed to load TauP model", retryError);
                            }
                        } else {
                            logger.severe("Error loading TauP model: " + e.getMessage());
                            throw new TauModelException("Failed to load TauP model", e);
                        }
                    }
                    break;
                case "taup":
                    try {
                        tauMod = TauModel.readModel(taupFile);
                    } catch (Exception e) {
                        // Check for version mismatch issues
                        if (e.getMessage() != null && e.getMessage().contains("serialVersionUID")) {
                            logger.info("TauModel serialVersionUID mismatch detected. Attempting version-tolerant loading...");
                            
                            try {
                                // Try loading with custom ObjectInputStream that tolerates version mismatch
                                tauMod = loadTaupFileWithVersionTolerance(taupFile);
                                logger.info("Successfully loaded .taup file with version tolerance");
                            } catch (Exception retryError) {
                                // If all strategies fail, provide detailed guidance to user
                                String errorMsg = "TauModel.taup file version mismatch.\n" +
                                    "The .taup file was generated with a different TauP library version.\n" +
                                    "Solutions:\n" +
                                    "1. Use .nd format instead (recommended): Convert your .taup model to .nd format\n" +
                                    "2. Regenerate .taup file: Run TauP_Time tool in your environment to regenerate the .taup file\n" +
                                    "   Command: java -cp $TAUP_JAR edu.sc.seis.TauP.TauP_Time -version\n" +
                                    "   Then use taup_create or TauP_Create to generate a new .taup file\n" +
                                    "3. Update your TauP library to match the version used to create the .taup file\n\n" +
                                    "File: " + taupFile;
                                
                                logger.severe(errorMsg);
                                throw new TauModelException(errorMsg, retryError);
                            }
                        } else {
                            logger.severe("Error: Loading TauP model: " + e.getMessage());
                            throw new TauModelException("Failed to load TauP model", e);
                        }
                    }
                    break;
                case "tvel":
                    // Support for .tvel velocity format (try as TauModelLoader first)
                    try {
                        tauMod = TauModelLoader.load(taupFile);
                    } catch (TauModelException e) {
                        logger.severe("Error: .tvel file format not directly supported. Please convert to .nd format.");
                        throw new TauModelException("Failed to load .tvel file. Please use .nd or .taup format instead.", e);
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
     * Loads a .taup file with tolerance for serialVersionUID mismatches.
     * Uses a custom ObjectInputStream that ignores version conflicts.
     */
    private static TauModel loadTaupFileWithVersionTolerance(String taupFile) throws Exception {
        java.io.ObjectInputStream ois = null;
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(taupFile);
            ois = new java.io.ObjectInputStream(fis) {
                @Override
                protected java.io.ObjectStreamClass readClassDescriptor() throws java.io.IOException, ClassNotFoundException {
                    java.io.ObjectStreamClass classDesc = super.readClassDescriptor();
                    // Check if this is a TauModel class
                    if (classDesc.getName().contains("TauModel")) {
                        logger.fine("Reading TauModel with version tolerance...");
                        // Get the local class descriptor
                        try {
                            java.io.ObjectStreamClass localClassDesc = 
                                java.io.ObjectStreamClass.lookup(Class.forName(classDesc.getName()));
                            if (localClassDesc != null) {
                                logger.fine("Using local TauModel class descriptor instead of serialized one");
                                return localClassDesc;
                            }
                        } catch (ClassNotFoundException e) {
                            logger.fine("Could not find local class: " + classDesc.getName());
                        }
                    }
                    return classDesc;
                }
            };
            
            Object obj = ois.readObject();
            if (obj instanceof TauModel) {
                return (TauModel) obj;
            } else {
                throw new TauModelException("File does not contain a TauModel object");
            }
        } catch (java.io.InvalidClassException e) {
            // This can still happen if the class structure has changed significantly
            logger.severe("Class structure mismatch: " + e.getMessage());
            throw new TauModelException("TauModel class structure mismatch", e);
        } finally {
            if (ois != null) {
                ois.close();
            }
        }
    }
    
    /**
     * Loads TauModel with fallback strategy to handle serialization issues.
     * This method uses multiple strategies to avoid deserializing corrupted cache.
     * For .taup files with version mismatch, this attempts to extract and use the model data.
     */
    private static TauModel loadTauModelWithFallback(String taupFile) throws Exception {
        String extension = FileUtils.getFileExtension(taupFile);
        
        // For .taup files, try reading as text/nd format first
        if ("taup".equalsIgnoreCase(extension)) {
            logger.info("For .taup files with serialization issues, trying to read model information...");
            // Since the .taup file has version mismatch, we can't deserialize it
            // Best strategy is to ask user to convert to .nd format
            throw new TauModelException(
                "Cannot load .taup file with version mismatch. Please convert to .nd format using TauP tools.");
        }
        
        // Strategy 1: Try loading as nd/default format
        try {
            logger.info("Trying TauModelLoader.load() for fallback...");
            return TauModelLoader.load(taupFile);
        } catch (Exception e1) {
            logger.info("TauModelLoader.load() failed: " + e1.getMessage());
        }
        
        // Strategy 2: Try reading as taup model
        try {
            logger.info("Trying TauModel.readModel() for fallback...");
            return TauModel.readModel(taupFile);
        } catch (Exception e2) {
            logger.info("TauModel.readModel() failed, trying cache cleanup...");
        }
        
        // Strategy 3: Try reloading with deleted cache hint
        try {
            // Delete TauP cache directory if it exists
            String homeDir = System.getProperty("user.home");
            File taupCacheDir = new File(homeDir, ".taup");
            if (taupCacheDir.exists() && taupCacheDir.isDirectory()) {
                logger.info("Found TauP cache directory at: " + taupCacheDir.getAbsolutePath());
                logger.info("Deleting cache files to resolve serialization issues...");
                deleteCacheFiles(taupCacheDir);
            }
            
            // Try loading again after cache deletion
            logger.info("Retrying load after cache cleanup...");
            return TauModelLoader.load(taupFile);
        } catch (Exception e3) {
            logger.warning("Failed to load even after cache cleanup: " + e3.getMessage());
            throw e3;
        }
    }
    
    /**
     * Recursively deletes cache files in a directory.
     */
    private static void deleteCacheFiles(File dir) {
        if (!dir.exists()) return;
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteCacheFiles(file);
                } else {
                    // Only delete .taup and .ser files
                    if (file.getName().endsWith(".taup") || file.getName().endsWith(".ser")) {
                        if (file.delete()) {
                            logger.fine("Deleted cache file: " + file.getName());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Clears the TauP internal model cache to resolve serialization issues.
     * This uses reflection to access and clear the static cache in TauModelLoader.
     */
    private static void clearTauPCache() {
        try {
            // Try to clear TauModelLoader's internal cache via reflection
            java.lang.reflect.Field cacheField = null;
            try {
                cacheField = TauModelLoader.class.getDeclaredField("modelCache");
            } catch (NoSuchFieldException e1) {
                // Try alternative cache field names
                try {
                    cacheField = TauModelLoader.class.getDeclaredField("cache");
                } catch (NoSuchFieldException e2) {
                    // Try another common name
                    try {
                        cacheField = TauModelLoader.class.getDeclaredField("models");
                    } catch (NoSuchFieldException e3) {
                        // Field not found, continue without clearing
                        logger.warning("Could not find TauModelLoader cache field");
                        return;
                    }
                }
            }
            
            if (cacheField != null) {
                cacheField.setAccessible(true);
                Object cache = cacheField.get(null);
                if (cache instanceof java.util.Map) {
                    ((java.util.Map<?, ?>) cache).clear();
                    logger.info("Cleared TauModelLoader internal cache");
                }
            }
        } catch (Exception e) {
            logger.warning("Could not clear TauModelLoader cache: " + e.getMessage());
        }
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
        
        if (travelTimeCache != null && travelTimeCache.matches(hypLon, hypLat, hypDep, idxList)) {
            return travelTimeCache.trvTime.clone();
        }

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
        
        travelTimeCache = new TravelTimeCacheEntry();
        travelTimeCache.lon = hypLon;
        travelTimeCache.lat = hypLat;
        travelTimeCache.dep = hypDep;
        travelTimeCache.usedIdx = idxList.clone();
        travelTimeCache.trvTime = trvTime.clone();
        
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
        
        if (partialDerivativeCache != null && partialDerivativeCache.matches(hypLon, hypLat, hypDep, idxList)) {
            double[][] dtdrCopy = new double[partialDerivativeCache.dtdr.length][];
            for (int i = 0; i < partialDerivativeCache.dtdr.length; i++) {
                dtdrCopy[i] = partialDerivativeCache.dtdr[i].clone();
            }
            return new Object[]{dtdrCopy, partialDerivativeCache.trvTime.clone()};
        }

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
        
        partialDerivativeCache = new PartialDerivativeCacheEntry();
        partialDerivativeCache.lon = hypLon;
        partialDerivativeCache.lat = hypLat;
        partialDerivativeCache.dep = hypDep;
        partialDerivativeCache.usedIdx = idxList.clone();
        partialDerivativeCache.dtdr = dtdr;
        partialDerivativeCache.trvTime = trvTime;
        
        double[][] dtdrCopy = new double[dtdr.length][];
        for (int i = 0; i < dtdr.length; i++) {
            dtdrCopy[i] = dtdr[i].clone();
        }
        return new Object[]{dtdrCopy, trvTime.clone()};
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

