package com.treloc.xtreloc.solver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.ml.clustering.Cluster;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.TripleDifferenceIO;
import com.treloc.xtreloc.io.TripleDifferenceIO.TripleDifference;
import com.treloc.xtreloc.util.BatchExecutorFactory;
import com.treloc.xtreloc.util.SolverLogger;

import com.treloc.xtreloc.io.VelocityModelLoadException;

/**
 * Triple difference relocation for clustered seismic events.
 * 
 * <p>This class implements the triple difference relocation algorithm, which relocates
 * clustered events by minimizing the differences in travel time differences between
 * event pairs observed at station pairs. The algorithm uses an iterative least-squares
 * approach with multiple stages, each with different distance thresholds and damping factors.
 * 
 * <p>The relocation process involves:
 * <ul>
 *   <li>Loading clustered events from a catalog file</li>
 *   <li>Loading triple difference data from binary files</li>
 *   <li>Iterative relocation with multiple stages (different distance thresholds)</li>
 *   <li>Output of relocated events to a catalog file</li>
 * </ul>
 * 
 * <p>This solver requires that clustering (CLS mode) has been performed first to generate
 * the triple difference binary files.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class HypoTripleDiff extends SolverBase {
    private static final Logger logger = Logger.getLogger(HypoTripleDiff.class.getName());
    
    private String catalogFile;
    private double hypBottom;
    private SpatialClustering spatialCls;
    private boolean showLSQR;
    private Path outDir;
    private Path targetDir;
    private int[] iterNumArray;
    private int[] distKmArray;
    private int[] dampFactArray;
    private double lsqrAtol = 1e-6;
    private double lsqrBtol = 1e-6;
    private double lsqrConlim = 1e8;
    private int lsqrIterLim = 1000;
    private boolean calcVar = true;
    /** Maximum number of triple-diff data to use per cluster (smallest residual first). Null = no limit. */
    private final Integer maxTripleDiffCount;
    private java.util.function.Consumer<String> logConsumer;
    private ConvergenceCallback convergenceCallback;
    private int numJobs;
    /** Absolute path of the catalog CSV written by the last successful {@link #start}; used by the GUI to auto-load results. */
    private volatile String lastOutputCatalogAbsolutePath;

    /**
     * Sets the convergence callback for reporting convergence information.
     * 
     * @param callback the convergence callback
     */
    public void setConvergenceCallback(ConvergenceCallback callback) {
        this.convergenceCallback = callback;
    }

    /**
     * @return absolute path of the TRD output catalog from the last successful {@link #start}, or {@code null}
     */
    public String getLastOutputCatalogAbsolutePath() {
        return lastOutputCatalogAbsolutePath;
    }

    /**
     * Constructs a new HypoTripleDiff solver with the specified configuration.
     * 
     * <p>The configuration must include:
     * <ul>
     *   <li>TRD mode configuration with catalog file and datDirectory</li>
     *   <li>Station file and velocity model (.nd / .tvel for Raytrace1D)</li>
     *   <li>Optional solver parameters (iterNum, distKm, dampFact arrays)</li>
     * </ul>
     * 
     * @param appConfig the application configuration containing TRD mode settings
     * @throws VelocityModelLoadException if there is an error loading the velocity model
     * @throws IllegalArgumentException if TRD mode configuration is missing or invalid
     */
    public HypoTripleDiff(AppConfig appConfig) throws VelocityModelLoadException {
        super(appConfig);
        
        this.hypBottom = appConfig.hypBottom;
        this.numJobs = appConfig.numJobs > 0 ? appConfig.numJobs : 
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        Integer maxTripleDiffCount = null;
        if (appConfig.getModes() != null && appConfig.getModes().containsKey("TRD")) {
            var trdConfig = appConfig.getModes().get("TRD");
            this.catalogFile = trdConfig.catalogFile;
            if (trdConfig.maxTripleDiffCount != null && trdConfig.maxTripleDiffCount > 0) {
                maxTripleDiffCount = trdConfig.maxTripleDiffCount;
            }
            if (trdConfig.outDirectory != null) {
                this.outDir = trdConfig.outDirectory;
            } else {
                this.outDir = Paths.get(".");
            }
            if (trdConfig.datDirectory != null) {
                this.targetDir = trdConfig.datDirectory;
                if (!java.nio.file.Files.exists(this.targetDir)) {
                    throw new IllegalArgumentException(
                        String.format("Target directory does not exist: %s\n" +
                            "  Please check the datDirectory path in config.json for TRD mode",
                            this.targetDir));
                }
                if (!java.nio.file.Files.isDirectory(this.targetDir)) {
                    throw new IllegalArgumentException(
                        String.format("Target path is not a directory: %s\n" +
                            "  Please check the datDirectory path in config.json for TRD mode",
                            this.targetDir));
                }
                logger.info("Using target directory from config: " + this.targetDir);
                SolverLogger.info("TRD: Using target directory from config: " + this.targetDir);
            } else {
                throw new IllegalArgumentException(
                    "TRD mode requires datDirectory to be specified in config.json.\n" +
                    "  The datDirectory should point to the directory containing triple difference binary files (e.g., ./demo/dat-cls).\n" +
                    "  Please add \"datDirectory\" to the TRD mode configuration in config.json.");
            }
        } else {
            throw new IllegalArgumentException("TRD mode configuration not found");
        }

        this.maxTripleDiffCount = maxTripleDiffCount;

        if (appConfig.getParams() != null && appConfig.getParams().containsKey("TRD")) {
            var trdSolver = appConfig.getParams().get("TRD");
            this.iterNumArray = parseIntArray(trdSolver, "iterNum", new int[]{10, 10});
            this.distKmArray = parseIntArray(trdSolver, "distKm", new int[]{50, 20});
            this.dampFactArray = parseIntArray(trdSolver, "dampFact", new int[]{0, 1});
            
            if (trdSolver.has("lsqrAtol")) {
                this.lsqrAtol = trdSolver.get("lsqrAtol").asDouble();
            }
            if (trdSolver.has("lsqrBtol")) {
                this.lsqrBtol = trdSolver.get("lsqrBtol").asDouble();
            }
            if (trdSolver.has("lsqrConlim")) {
                this.lsqrConlim = trdSolver.get("lsqrConlim").asDouble();
            }
            if (trdSolver.has("lsqrIterLim")) {
                this.lsqrIterLim = trdSolver.get("lsqrIterLim").asInt();
            }
            if (trdSolver.has("lsqrCalcVar")) {
                this.calcVar = trdSolver.get("lsqrCalcVar").asBoolean();
            }
        } else {
            this.iterNumArray = new int[]{10, 10};
            this.distKmArray = new int[]{50, 20};
            this.dampFactArray = new int[]{0, 1};
        }
        
        if (iterNumArray.length != distKmArray.length || iterNumArray.length != dampFactArray.length) {
            throw new IllegalArgumentException("The length of iterNum, distKm, and dampFact must be the same.");
        }
        
        try {
            AppConfig clsConfig = new AppConfig();
            clsConfig.stationFile = appConfig.stationFile;
            clsConfig.taupFile = appConfig.taupFile;
            clsConfig.threshold = appConfig.threshold;
            clsConfig.hypBottom = appConfig.hypBottom;
            clsConfig.io = new java.util.HashMap<>();
            var ioCls = new AppConfig.ModeIOConfig();
            ioCls.catalogFile = this.catalogFile;
            clsConfig.io.put("CLS", ioCls);
            clsConfig.params = new java.util.HashMap<>();
            this.spatialCls = new SpatialClustering(clsConfig);
        } catch (Exception e) {
            logger.severe("Failed to initialize SpatialClustering: " + e.getMessage());
            SolverLogger.severe("TRD: Failed to initialize SpatialClustering: " + e.getMessage());
            throw new RuntimeException("Failed to initialize SpatialClustering", e);
        }
        
        if (appConfig.getParams() != null && appConfig.getParams().containsKey("TRD")) {
            var trdSolver = appConfig.getParams().get("TRD");
            if (trdSolver.has("lsqrShowLog")) {
                this.showLSQR = trdSolver.get("lsqrShowLog").asBoolean();
            } else {
                String logLevel = appConfig.logLevel != null ? appConfig.logLevel : "INFO";
                this.showLSQR = java.util.logging.Level.INFO.intValue() <= 
                               java.util.logging.Level.parse(logLevel.toUpperCase()).intValue();
            }
        } else {
            String logLevel = appConfig.logLevel != null ? appConfig.logLevel : "INFO";
            this.showLSQR = java.util.logging.Level.INFO.intValue() <= 
                           java.util.logging.Level.parse(logLevel.toUpperCase()).intValue();
        }
    }
    
    public void setLogConsumer(java.util.function.Consumer<String> logConsumer) {
        this.logConsumer = logConsumer;
    }
    
    private int[] parseIntArray(com.fasterxml.jackson.databind.JsonNode node, String key, int[] defaultValue) {
        if (!node.has(key) || node.get(key).isNull()) {
            return defaultValue;
        }
        com.fasterxml.jackson.databind.JsonNode v = node.get(key);
        if (v.isArray()) {
            List<Integer> list = new ArrayList<>();
            for (var element : v) {
                list.add(element.asInt());
            }
            return list.stream().mapToInt(i -> i).toArray();
        }
        if (v.isTextual()) {
            return parseCommaSeparatedInts(v.asText(), defaultValue);
        }
        if (v.isNumber()) {
            return new int[]{v.asInt()};
        }
        return defaultValue;
    }

    /**
     * Parses comma-separated integers (e.g. config or UI export {@code "10,10"}).
     */
    private static int[] parseCommaSeparatedInts(String text, int[] defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        String[] parts = text.trim().split("\\s*,\\s*");
        if (parts.length == 0) {
            return defaultValue;
        }
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i].trim());
            }
            return out;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Starts the triple difference relocation process.
     * 
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Loads clustered events from the catalog file</li>
     *   <li>Verifies that clustering has been performed and triple difference data exists</li>
     *   <li>For each cluster, performs iterative relocation with multiple stages</li>
     *   <li>Outputs relocated events to a catalog file</li>
     * </ol>
     * 
     * <p>Note: The datFile and outputFile parameters are not used in TRD mode.
     * The catalog file and output directory are taken from the configuration.
     * 
     * @param datFile the input .dat file path (not used in TRD mode, uses catalog from config)
     * @param outputFile the output .dat file path (not used in TRD mode, uses output directory from config)
     * @throws VelocityModelLoadException if there is an error in the velocity model
     * @throws IllegalArgumentException if clustering has not been performed or triple difference data is missing
     * @throws RuntimeException if relocation fails for any cluster
     */
    public void start(String datFile, String outputFile) throws VelocityModelLoadException {
        lastOutputCatalogAbsolutePath = null;
        String catalogPathToUse = null;
        try {
            if (this.catalogFile == null || this.catalogFile.isEmpty()) {
                StringBuilder errorMsg = new StringBuilder("Catalog file is required for TRD mode.\n");
                errorMsg.append("  Please specify catalogFile in the TRD mode configuration.\n");
                errorMsg.append("  Target directory: ").append(targetDir != null ? targetDir.toFile().getAbsolutePath() : "not set").append("\n");
                logger.severe(errorMsg.toString());
                SolverLogger.severe("TRD: " + errorMsg.toString());
                throw new IllegalArgumentException(errorMsg.toString());
            }
            
            File catalogFileObj = new File(this.catalogFile);
            if (!catalogFileObj.exists()) {
                if (targetDir != null) {
                    File targetDirFile = targetDir.toFile();
                    if (catalogFileObj.isAbsolute()) {
                        File catalogInTarget = new File(targetDirFile, catalogFileObj.getName());
                        if (catalogInTarget.exists()) {
                            catalogPathToUse = catalogInTarget.getAbsolutePath();
                            logger.info("Using catalog file from target directory: " + catalogPathToUse);
                            SolverLogger.info("TRD: Using catalog file from target directory: " + catalogPathToUse);
                        }
                    } else {
                        File catalogInTarget = new File(targetDirFile, this.catalogFile);
                        if (catalogInTarget.exists()) {
                            catalogPathToUse = catalogInTarget.getAbsolutePath();
                            logger.info("Using catalog file from target directory: " + catalogPathToUse);
                            SolverLogger.info("TRD: Using catalog file from target directory: " + catalogPathToUse);
                        }
                    }
                }
                
                if (catalogPathToUse == null) {
                    StringBuilder errorMsg = new StringBuilder("Catalog file not found.\n");
                    errorMsg.append("  Specified catalog file: ").append(this.catalogFile).append("\n");
                    errorMsg.append("  File exists: ").append(catalogFileObj.exists()).append("\n");
                    if (targetDir != null) {
                        errorMsg.append("  Target directory: ").append(targetDir.toFile().getAbsolutePath()).append("\n");
                        if (catalogFileObj.isAbsolute()) {
                            File catalogInTarget = new File(targetDir.toFile(), catalogFileObj.getName());
                            errorMsg.append("  Searched in target directory: ").append(catalogInTarget.getAbsolutePath()).append("\n");
                            errorMsg.append("  File exists: ").append(catalogInTarget.exists()).append("\n");
                        } else {
                            File catalogInTarget = new File(targetDir.toFile(), this.catalogFile);
                            errorMsg.append("  Searched in target directory: ").append(catalogInTarget.getAbsolutePath()).append("\n");
                            errorMsg.append("  File exists: ").append(catalogInTarget.exists()).append("\n");
                        }
                    }
                    errorMsg.append("\nPlease ensure that the specified catalog file exists.");
                    logger.severe(errorMsg.toString());
                SolverLogger.severe("TRD: " + errorMsg.toString());
                    throw new IllegalArgumentException(errorMsg.toString());
                }
            } else {
                catalogPathToUse = catalogFileObj.getAbsolutePath();
                logger.info("Using specified catalog file: " + catalogPathToUse);
                SolverLogger.info("TRD: Using specified catalog file: " + catalogPathToUse);
            }
            
            
            logger.info("Discovering clusters from catalog (no full load for memory): " + catalogPathToUse);
            SolverLogger.info("TRD: Discovering clusters from catalog.");
            List<Integer> clusterIds = new ArrayList<>();
            try {
                for (int cid = 1; ; cid++) {
                    List<Point> pts = spatialCls.loadPointsFromCatalogByCluster(catalogPathToUse, cid);
                    if (pts.isEmpty()) break;
                    clusterIds.add(cid);
                }
            } catch (Exception e) {
                String errorMsg = "Failed to read catalog: " + catalogPathToUse + "\n  Error: " + e.getMessage();
                logger.severe(errorMsg);
                SolverLogger.severe("TRD: " + errorMsg);
                if (e.getCause() != null) {
                    logger.severe("  Caused by: " + e.getCause().getMessage());
                    SolverLogger.severe("TRD: Caused by: " + e.getCause().getMessage());
                }
                throw new IllegalArgumentException(errorMsg, e);
            }
            if (clusterIds.isEmpty()) {
                String errorMsg = "No clusters found in catalog (cluster ID 1,2,...). Please run CLS mode first.";
                logger.severe(errorMsg);
                SolverLogger.severe("TRD: " + errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            logger.info("Clustering detected. Found " + clusterIds.size() + " cluster(s)");
            SolverLogger.info("TRD: Clustering detected. Found " + clusterIds.size() + " cluster(s)");

            List<Integer> missingBinClusters = new ArrayList<>();
            for (Integer cid : clusterIds) {
                List<TripleDifference> testLoad = loadTripleDiff(cid);
                if (testLoad.isEmpty()) {
                    missingBinClusters.add(cid);
                }
            }
            
            if (!missingBinClusters.isEmpty()) {
                String missingClusters = missingBinClusters.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
                StringBuilder errorMsg = new StringBuilder("Triple difference bin files are missing.\n");
                errorMsg.append("  Missing clusters: ").append(missingClusters).append("\n");
                errorMsg.append("  Target directory: ").append(targetDir != null ? targetDir.toFile().getAbsolutePath() : "not set").append("\n");
                errorMsg.append("  Output directory: ").append(outDir != null ? outDir.toFile().getAbsolutePath() : "not set").append("\n");
                errorMsg.append("  Please run CLS mode first to generate triple difference data for all clusters.");
                logger.severe(errorMsg.toString());
                SolverLogger.severe("TRD: " + errorMsg.toString());
                throw new IllegalArgumentException(errorMsg.toString());
            }
            
            logger.info("Clustering confirmed. Processing clusters: " +
                clusterIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")));
            SolverLogger.info("TRD: Clustering confirmed. Processing: " +
                clusterIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")));

            File outputCatalogFileObj = com.treloc.xtreloc.util.CatalogFileNameGenerator.generateCatalogFileName(
                catalogPathToUse, "TRD", outDir.toFile());
            String outputCatalogFile = outputCatalogFileObj.getName();
            String outputCatalogPath = outDir.resolve(outputCatalogFile).toString();

            try (java.io.PrintWriter catalogWriter = new java.io.PrintWriter(new java.io.FileWriter(outputCatalogPath))) {
                catalogWriter.println("time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid");

                List<Point> noisePoints = spatialCls.loadPointsFromCatalogByCluster(catalogPathToUse, 0);
                for (Point originalPoint : noisePoints) {
                    Point point = new Point(
                        originalPoint.getTime(),
                        originalPoint.getLat(),
                        originalPoint.getLon(),
                        originalPoint.getDep(),
                        originalPoint.getElat(),
                        originalPoint.getElon(),
                        originalPoint.getEdep(),
                        originalPoint.getRes(),
                        originalPoint.getFilePath(),
                        originalPoint.getType(),
                        originalPoint.getCid()
                    );
                    appendPointToCatalog(catalogWriter, point);
                    writePointToDatFile(point);
                }

                for (int clusterId : clusterIds) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Triple difference relocation interrupted by user");
                    SolverLogger.info("TRD: Interrupted by user");
                    throw new RuntimeException("Cancelled");
                }

                List<Point> originalClusterPoints = spatialCls.loadPointsFromCatalogByCluster(catalogPathToUse, clusterId);
                if (originalClusterPoints.isEmpty()) {
                    continue;
                }
                
                List<Point> clusterPoints = new ArrayList<>();
                for (Point originalPoint : originalClusterPoints) {
                        Point point = new Point(
                            originalPoint.getTime(),
                            originalPoint.getLat(),
                            originalPoint.getLon(),
                            originalPoint.getDep(),
                            originalPoint.getElat(),
                            originalPoint.getElon(),
                            originalPoint.getEdep(),
                            originalPoint.getRes(),
                            originalPoint.getFilePath(),
                            originalPoint.getType(),
                            originalPoint.getCid()
                        );
                        point.setLagTable(originalPoint.getLagTable());
                        point.setUsedIdx(originalPoint.getUsedIdx());
                    clusterPoints.add(point);
                }
                
                int[] targMap = new int[clusterPoints.size()];
                int numTarget = 0;
                int refCount = 0;
                int errCount = 0;
                for (int i = 0; i < clusterPoints.size(); i++) {
                    if (clusterPoints.get(i).getType().equals("ERR") || clusterPoints.get(i).getType().equals("REF")) {
                        targMap[i] = -1;
                        if (clusterPoints.get(i).getType().equals("REF")) {
                            refCount++;
                        } else {
                            errCount++;
                        }
                    } else {
                        targMap[i] = numTarget;
                        numTarget++;
                    }
                }
                if (refCount > 0) {
                    logger.info("Cluster " + clusterId + ": " + refCount + " REF events will not be updated (excluded from relocation)");
                    SolverLogger.info("TRD: Cluster " + clusterId + ": " + refCount + " REF events excluded from relocation");
                }
                if (errCount > 0) {
                    logger.info("Cluster " + clusterId + ": " + errCount + " ERR events excluded from relocation");
                    SolverLogger.info("TRD: Cluster " + clusterId + ": " + errCount + " ERR events excluded from relocation");
                }
                
                if (numTarget == 0) {
                    logger.warning("Cluster " + clusterId + " has no target events, skipping");
                    SolverLogger.warning("TRD: Cluster " + clusterId + " has no target events, skipping");
                    continue;
                }
                
                Cluster<Point> cluster = new Cluster<>();
                clusterPoints.forEach(cluster::addPoint);
                
                List<TripleDifference> tripDiff = loadTripleDiff(clusterId);
                if (tripDiff.isEmpty()) {
                    logger.warning("No triple difference data found for cluster " + clusterId + ", skipping");
                    SolverLogger.warning("TRD: No triple difference data for cluster " + clusterId + ", skipping");
                    continue;
                }
                tripDiff = selectTripleDiffByResidual(tripDiff);
                if (tripDiff.isEmpty()) {
                    logger.warning("No triple difference data left after selection for cluster " + clusterId + ", skipping");
                    SolverLogger.warning("TRD: No triple difference data after selection for cluster " + clusterId + ", skipping");
                    continue;
                }

                for (int i = 0; i < iterNumArray.length; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        logger.info("Triple difference relocation interrupted by user");
                        SolverLogger.info("TRD: Interrupted by user");
                        throw new RuntimeException("Cancelled");
                    }
                    
                    int distKm = distKmArray[i];
                    int iterNum = iterNumArray[i];
                    int dampFact = dampFactArray[i];
                    int globalIterOffset = 0;
                    for (int ii = 0; ii < i; ii++) {
                        globalIterOffset += iterNumArray[ii];
                    }
                    
                    logger.info("Cluster " + clusterId + ", iteration stage " + (i + 1) + "/" + iterNumArray.length + 
                               " (distKm=" + distKm + ", iterNum=" + iterNum + ", dampFact=" + dampFact + ")");
                    SolverLogger.info("TRD: Cluster " + clusterId + ", stage " + (i + 1) + "/" + iterNumArray.length + " (distKm=" + distKm + ", iterNum=" + iterNum + ", dampFact=" + dampFact + ")");
                    
                    List<TripleDifference> filteredTripDiff = filterTripDiffByDistance(tripDiff, distKm);
                    
                    if (filteredTripDiff.isEmpty()) {
                        logger.warning("No triple difference data within " + distKm + " km for cluster " + clusterId + 
                                     " (total triple differences: " + tripDiff.size() + ")");
                        SolverLogger.warning("TRD: No triple difference data within " + distKm + " km for cluster " + clusterId);
                        continue;
                    }
                    
                    logger.info("Cluster " + clusterId + ", stage " + (i + 1) + ": Using " + filteredTripDiff.size() + 
                               " triple differences (filtered from " + tripDiff.size() + " total)");
                    SolverLogger.info("TRD: Cluster " + clusterId + ", stage " + (i + 1) + ": using " + filteredTripDiff.size() + " triple differences");
                    
                    double[] dm = null;
                    
                    for (int j = 0; j < iterNum; j++) {
                        if (Thread.currentThread().isInterrupted()) {
                            logger.info("Triple difference relocation interrupted by user");
                            SolverLogger.info("TRD: Interrupted by user");
                            throw new RuntimeException("Cancelled");
                        }
                        try {
                            logger.info("Cluster " + clusterId + ", stage " + (i + 1) + ", iteration " + (j + 1) + "/" + iterNum);
                            SolverLogger.info("TRD: Cluster " + clusterId + ", stage " + (i + 1) + "/" + iterNumArray.length + ", iteration " + (j + 1) + "/" + iterNum);
                            
                            boolean isLastStage = (i == iterNumArray.length - 1);
                            boolean isLastIteration = (j == iterNum - 1);
                            boolean shouldCalcVar = calcVar && isLastStage && isLastIteration;
                            
                            // After ERR marking, targMap can have gaps (e.g. indices 0,2,3); matrix columns are 0..numTarget-1 only.
                            if (targMapHasGaps(targMap)) {
                                dm = null;
                            }
                            numTarget = compactTargMap(targMap);
                            
                            if (numTarget == 0) {
                                logger.warning("Cluster " + clusterId + " has no valid target events after filtering, skipping remaining iterations");
                                SolverLogger.warning("TRD: Cluster " + clusterId + " has no valid target events after filtering, skipping.");
                                break;
                            }
                            
                            double[][][] partialTbl = createPartialTblArray(cluster, targMap);
                            if (Thread.currentThread().isInterrupted()) {
                                logger.info("Triple difference relocation interrupted by user");
                                SolverLogger.info("TRD: Interrupted by user");
                                throw new RuntimeException("Cancelled");
                            }
                            if (targMapHasGaps(targMap)) {
                                dm = null;
                            }
                            numTarget = compactTargMap(targMap);
                            if (numTarget == 0) {
                                logger.warning("Cluster " + clusterId + " has no valid target events after partial table, skipping remaining iterations");
                                SolverLogger.warning("TRD: Cluster " + clusterId + " has no valid target events after partial table, skipping.");
                                break;
                            }
                            Object[] dG = matrixDG(filteredTripDiff, cluster, partialTbl, distKm, targMap, numTarget);
                            if (Thread.currentThread().isInterrupted()) {
                                logger.info("Triple difference relocation interrupted by user");
                                SolverLogger.info("TRD: Interrupted by user");
                                throw new RuntimeException("Cancelled");
                            }
                            double[] d = (double[]) dG[0];
                            Object GObj = dG[1];
                            
                            if (d.length == 0) {
                                throw new IllegalArgumentException("Empty residual vector d. This may indicate no valid triple differences.");
                            }
                            
                            int gRows, gCols;
                            if (GObj instanceof OpenMapRealMatrix) {
                                OpenMapRealMatrix G = (OpenMapRealMatrix) GObj;
                                gRows = G.getRowDimension();
                                gCols = G.getColumnDimension();
                            } else if (GObj instanceof COOSparseMatrix) {
                                COOSparseMatrix G = (COOSparseMatrix) GObj;
                                gRows = G.getRowDimension();
                                gCols = G.getColumnDimension();
                            } else {
                                throw new IllegalArgumentException("Unknown matrix type: " + GObj.getClass().getName());
                            }
                            
                            if (gRows == 0 || gCols == 0) {
                                throw new IllegalArgumentException("Empty design matrix G. This may indicate no valid triple differences.");
                            }
                            
                            int expectedDmLength = 3 * numTarget;
                            if (gCols != expectedDmLength) {
                                throw new IllegalArgumentException(
                                    "Design matrix G column dimension mismatch. " +
                                    "Expected: " + expectedDmLength + " (3 * numTarget), " +
                                    "Actual: " + gCols);
                            }
                            
                            // Previous iteration may have left dm with old length after numTarget decreased (ERR marking).
                            if (dm != null && dm.length != expectedDmLength) {
                                logger.warning(String.format(
                                    "Cluster %d, stage %d, iteration %d: dm length mismatch. " +
                                    "Expected: %d (3 * numTarget), Actual: %d. " +
                                    "Resetting dm to null (numTarget may have changed due to ERR marking).",
                                    clusterId, i + 1, j + 1, expectedDmLength, dm.length));
                                SolverLogger.warning("TRD: Cluster " + clusterId + " dm length mismatch, resetting.");
                                dm = null;
                            }
                            
                            double[] weights;
                            if (j == 0 || dm == null) {
                                weights = new double[d.length];
                                for (int k = 0; k < weights.length; k++) {
                                    weights[k] = 1.0;
                                }
                            } else {
                                double[] residuals = calculateResiduals(GObj, d, dm);
                                weights = turkeyBiweight(residuals, 4.685);
                            }
                            
                            logger.fine("TRD: applyWeights start (d.length=" + d.length + ")");
                            SolverLogger.fine("TRD: applyWeights start");
                            Object GWeighted = applyWeightsToMatrix(GObj, weights);
                            double[] dWeighted = applyWeightsToVector(d, weights);
                            logger.fine("TRD: applyWeights done");
                            SolverLogger.fine("TRD: applyWeights done");
                            
                            logger.fine("TRD LSQR: starting (rows=" + gRows + ", cols=" + gCols + ")");
                            SolverLogger.fine("TRD: LSQR start (rows=" + gRows + ", cols=" + gCols + ")");
                            ScipyLSQR.LSQRResult result;
                            if (GWeighted instanceof OpenMapRealMatrix) {
                                result = ScipyLSQR.lsqr(
                                    (OpenMapRealMatrix) GWeighted,
                                    dWeighted,
                                    dampFact,
                                    lsqrAtol,
                                    lsqrBtol,
                                    lsqrConlim,
                                    lsqrIterLim,
                                    showLSQR,
                                    shouldCalcVar,
                                    null,
                                    logConsumer);
                            } else {
                                result = ScipyLSQR.lsqr(
                                    (COOSparseMatrix) GWeighted,
                                    dWeighted,
                                    dampFact,
                                    lsqrAtol,
                                    lsqrBtol,
                                    lsqrConlim,
                                    lsqrIterLim,
                                    showLSQR,
                                    shouldCalcVar,
                                    null,
                                    logConsumer);
                            }
                            
                            logger.fine("TRD LSQR: done itn=" + (result != null ? result.itn : -1) + " r2norm=" + (result != null ? result.r2norm : Double.NaN));
                            SolverLogger.fine("TRD: LSQR done (itn=" + (result != null ? result.itn : -1) + ")");
                            if (Thread.currentThread().isInterrupted()) {
                                logger.info("Triple difference relocation interrupted by user");
                                SolverLogger.info("TRD: Interrupted by user");
                                throw new RuntimeException("Cancelled");
                            }
                            if (convergenceCallback != null && result != null) {
                                double residualRMS = result.r2norm;
                                // Use cumulative index across TRD stages so GUI plot/log advances past stage 1
                                // (per-stage j alone resets to 0 each stage and looks like "stuck" in step 1).
                                convergenceCallback.onClusterResidualUpdate(clusterId, globalIterOffset + j, residualRMS);
                            }
                            
                            if (result == null) {
                                throw new IllegalArgumentException("LSQR solver returned null result.");
                            }
                            
                            dm = result.x;
                            
                            if (dm == null || dm.length == 0) {
                                throw new IllegalArgumentException("LSQR solver returned empty solution vector.");
                            }
                            
                            // When numTarget decreased (e.g. ERR marking), dm may be longer than expected. Use only the
                            // prefix dm[0..expectedDmLength-1] to update the current numTarget points; skip only if dm is too short.
                            if (dm.length < expectedDmLength) {
                                logger.warning(String.format(
                                    "Cluster %d, stage %d, iteration %d: solution dm too short. " +
                                    "Expected: %d (3 * numTarget), Actual: %d. Skipping position update this iteration.",
                                    clusterId, i + 1, j + 1, expectedDmLength, dm.length));
                                SolverLogger.warning("TRD: Cluster " + clusterId + " dm too short, skipping position update.");
                                dm = null;
                            } else if (dm.length > expectedDmLength) {
                                logger.warning(String.format(
                                    "Cluster %d, stage %d, iteration %d: solution dm longer than expected (numTarget may have decreased). " +
                                    "Expected: %d, Actual: %d. Updating only the first %d components for valid targets.",
                                    clusterId, i + 1, j + 1, expectedDmLength, dm.length, expectedDmLength));
                                SolverLogger.warning("TRD: Cluster " + clusterId + " dm length mismatch, using prefix for valid targets only.");
                            }
                            
                            double[] var = null;
                            if (shouldCalcVar && result != null && result.var != null) {
                                var = result.var;
                            }
                            
                            if (dm != null) {
                            // Use only indices [0, numTarget-1] when dm is longer than expected (ERR reduced numTarget).
                            int effectiveDmLength = Math.min(dm.length, expectedDmLength);
                            for (int k = 0; k < clusterPoints.size(); k++) {
                                if (targMap[k] == -1) {
                                    continue;
                                }
                                Point point = clusterPoints.get(k);
                                int targIdx = targMap[k];
                                if (targIdx * 3 + 2 >= effectiveDmLength) {
                                    // This target index is beyond current numTarget (e.g. ERR reduced count); exclude from relocation.
                                    logger.warning("Cluster " + clusterId + ": excluding point " + k + " from relocation (targIdx=" + targIdx + " >= numTarget=" + numTarget + "). Target mapping was reduced by ERR marking.");
                                    SolverLogger.warning("TRD: Excluding point " + k + " from relocation (targIdx out of range after ERR marking).");
                                    targMap[k] = -1;
                                    continue;
                                }
                                // Always apply solution update (dm) to position; then mark ERR if depth out of range (excluded from next iterations).
                                double newLon = point.getLon() + dm[targIdx * 3];
                                double newLat = point.getLat() + dm[targIdx * 3 + 1];
                                double newDep = point.getDep() + dm[targIdx * 3 + 2];
                                point.setLon(newLon);
                                point.setLat(newLat);
                                point.setDep(newDep);

                                if (newDep < stnBottom || newDep > hypBottom) {
                                    logger.warning("Point " + point.getTime() + " in cluster " + clusterId +
                                                 " has depth " + newDep + " outside valid range [" + stnBottom + ", " + hypBottom + "]. Marking as ERR (position still updated).");
                                    SolverLogger.warning("TRD: Point " + point.getTime() + " in cluster " + clusterId + " depth outside range, marked ERR.");
                                    point.setType("ERR");
                                    targMap[k] = -1;
                                } else {
                                    if (shouldCalcVar && var != null && targIdx * 3 + 2 < var.length) {
                                        double lonVar = var[targIdx * 3];
                                        double latVar = var[targIdx * 3 + 1];
                                        double depVar = var[targIdx * 3 + 2];

                                        double lonStd = Math.sqrt(Math.max(0.0, lonVar));
                                        double latStd = Math.sqrt(Math.max(0.0, latVar));
                                        double depStd = Math.sqrt(Math.max(0.0, depVar));

                                        double newLatRad = Math.toRadians(newLat);
                                        double elon = lonStd * HypoUtils.getDeg2Km() * Math.cos(newLatRad);
                                        double elat = latStd * HypoUtils.getDeg2Km();
                                        double edep = depStd;

                                        point.setElon(elon);
                                        point.setElat(elat);
                                        point.setEdep(edep);
                                    }
                                }
                            }
                            }
                            
                            cluster = new Cluster<>();
                            clusterPoints.forEach(cluster::addPoint);
                        } catch (IndexOutOfBoundsException e) {
                            String errorMsg = String.format(
                                "Index out of bounds error in cluster %d, stage %d, iteration %d:\n" +
                                "  Error: %s\n" +
                                "  Number of target events: %d\n" +
                                "  Number of cluster points: %d\n" +
                                "  This may indicate a problem with the matrix dimensions or target mapping.",
                                clusterId, i + 1, j + 1, e.getMessage(), numTarget, clusterPoints.size());
                            logger.severe(errorMsg);
                            SolverLogger.severe("TRD: " + errorMsg);
                            throw new RuntimeException(errorMsg, e);
                        } catch (Exception e) {
                            String errorMsg = String.format(
                                "Error in cluster %d, stage %d, iteration %d:\n" +
                                "  Error: %s\n" +
                                "  Number of target events: %d\n" +
                                "  Number of cluster points: %d\n" +
                                "  Number of triple differences: %d",
                                clusterId, i + 1, j + 1, e.getMessage(), numTarget, clusterPoints.size(), filteredTripDiff.size());
                            logger.severe(errorMsg);
                            SolverLogger.severe("TRD: " + errorMsg);
                            if (e.getCause() != null) {
                                logger.severe("  Caused by: " + e.getCause().getMessage());
                                SolverLogger.severe("TRD: Caused by: " + e.getCause().getMessage());
                            }
                            throw new RuntimeException(errorMsg, e);
                        }
                    }
                }
                
                // Recompute final triple-difference residuals and set per-event RMS for relocated hypocenters
                try {
                    cluster = new Cluster<>();
                    clusterPoints.forEach(cluster::addPoint);
                    numTarget = compactTargMap(targMap);
                    double[] rmsPerEvent = computeFinalRmsPerEvent(cluster, tripDiff, targMap, numTarget);
                    for (int k = 0; k < clusterPoints.size(); k++) {
                        if (k < rmsPerEvent.length && !Double.isNaN(rmsPerEvent[k])) {
                            clusterPoints.get(k).setRes(rmsPerEvent[k]);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("TRD: Could not compute final RMS per event for cluster " + clusterId + ": " + e.getMessage());
                    SolverLogger.warning("TRD: Could not compute final RMS for cluster " + clusterId + ": " + e.getMessage());
                }
                
                for (Point point : clusterPoints) {
                    if (!point.getType().equals("ERR") && !point.getType().equals("REF")) {
                        point.setType("TRD");
                    }
                    appendPointToCatalog(catalogWriter, point);
                    writePointToDatFile(point);
                }
                }

                catalogWriter.flush();
            }
            lastOutputCatalogAbsolutePath = outputCatalogPath;
            logger.info("Triple difference relocation completed. Output catalog: " + outputCatalogFile);
            SolverLogger.info("TRD: Completed. Output catalog: " + outputCatalogFile);
            
        } catch (IllegalArgumentException e) {
            StringBuilder errorMsg = new StringBuilder("Triple difference relocation failed (IllegalArgumentException):\n");
            errorMsg.append("  ").append(e.getMessage()).append("\n");
            errorMsg.append("  Catalog file: ").append(catalogPathToUse != null ? catalogPathToUse : "not determined").append("\n");
            errorMsg.append("  Target directory: ").append(targetDir != null ? targetDir.toFile().getAbsolutePath() : "not set").append("\n");
            errorMsg.append("  Output directory: ").append(outDir != null ? outDir.toFile().getAbsolutePath() : "not set").append("\n");
            logger.severe(errorMsg.toString());
            SolverLogger.severe("TRD: " + errorMsg.toString());
            throw e;
        } catch (Exception e) {
            StringBuilder errorMsg = new StringBuilder("Triple difference relocation failed with unexpected error:\n");
            errorMsg.append("  Error type: ").append(e.getClass().getName()).append("\n");
            errorMsg.append("  Error message: ").append(e.getMessage()).append("\n");
            if (catalogPathToUse != null) {
                errorMsg.append("  Catalog file: ").append(catalogPathToUse).append("\n");
            }
            if (targetDir != null) {
                errorMsg.append("  Target directory: ").append(targetDir.toFile().getAbsolutePath()).append("\n");
            }
            if (outDir != null) {
                errorMsg.append("  Output directory: ").append(outDir.toFile().getAbsolutePath()).append("\n");
            }
            logger.severe(errorMsg.toString());
            SolverLogger.severe("TRD: " + errorMsg.toString());
            if (e.getCause() != null) {
                logger.severe("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
                SolverLogger.severe("TRD: Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
            }
            logger.log(Level.SEVERE, "TRD: unexpected error stack trace", e);
            throw new RuntimeException("Triple difference relocation failed", e);
        }
    }
    
    private List<TripleDifference> loadTripleDiff(int clusterId) {
        String fileName = "triple_diff_" + clusterId + ".bin";
        
        File targetDirFile = targetDir != null ? targetDir.toFile() : new File(".");
        File file = new File(targetDirFile, fileName);
        
        if (!file.exists()) {
            File outDirFile = outDir != null ? outDir.toFile() : new File(".");
            file = new File(outDirFile, fileName);
            
            if (!file.exists()) {
                logger.warning("Triple difference file not found: " + fileName + 
                             " (searched in target directory: " + targetDirFile.getAbsolutePath() + 
                             ", output directory: " + outDirFile.getAbsolutePath() + ")");
                SolverLogger.warning("TRD: Triple difference file not found: " + fileName);
                return new ArrayList<>();
            } else {
                logger.info("Using triple difference file from output directory: " + file.getAbsolutePath());
                SolverLogger.info("TRD: Using triple difference file from output directory: " + file.getAbsolutePath());
            }
        } else {
            logger.info("Using triple difference file from target directory: " + file.getAbsolutePath());
            SolverLogger.info("TRD: Using triple difference file from target directory: " + file.getAbsolutePath());
        }
        
        try {
            return TripleDifferenceIO.loadBinary(file);
        } catch (IOException e) {
            logger.severe("Error reading triple difference file: " + e.getMessage());
            SolverLogger.severe("TRD: Error reading triple difference file: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * @return an Object array containing [d (double[]), G (OpenMapRealMatrix or COOSparseMatrix)]
     * 
     * <p>This method automatically selects the appropriate sparse matrix implementation
     * based on matrix size. For large matrices (M * 3*N > 10^7), COOSparseMatrix is used;
     * otherwise, OpenMapRealMatrix is used.
     */
    private Object[] matrixDG(List<TripleDifference> trpDiff, Cluster<Point> cluster, 
                              double[][][] partialTbl, double distanceThreshold, int[] targMap, int numTarget) {
        int M = trpDiff.size();
        int N = numTarget;
        logger.fine("TRD matrixDG: start M=" + M + " N=" + N + " (3*N=" + (3 * N) + ")");
        SolverLogger.fine("TRD: matrixDG start (rows=" + M + ", cols=" + (3 * N) + ")");
        
        // Count valid rows (excluding REF-REF pairs and rows with targMap index >= N)
        int validRowCount = 0;
        for (int m = 0; m < M; m++) {
            TripleDifference td = trpDiff.get(m);
            int nCol0 = targMap[td.eve0];
            int nCol1 = targMap[td.eve1];
            if (nCol0 == -1 && nCol1 == -1) continue;
            if ((nCol0 >= 0 && nCol0 >= N) || (nCol1 >= 0 && nCol1 >= N)) continue;
            validRowCount++;
        }
        
        int M_constrained = validRowCount + 3;
        double[] d = new double[M_constrained];
        
        long matrixSize = (long) M_constrained * (long) (3 * N);
        boolean useCOO = matrixSize > 10_000_000L;
        
        if (useCOO) {
            logger.info(String.format(
                "Using COOSparseMatrix for large matrix (M=%d, N=%d, size=%d). " +
                "This avoids array size limitations.",
                M_constrained, N, matrixSize));
            SolverLogger.info("TRD: Using COOSparseMatrix for large matrix (M=" + M_constrained + ", N=" + N + ").");
            COOSparseMatrix G = new COOSparseMatrix(M_constrained, 3 * N);
            
            int rowIdx = 0;
            int skipCountOutOfRange = 0;
            for (int m = 0; m < M; m++) {
                TripleDifference td = trpDiff.get(m);
                int eve0 = td.eve0;
                int eve1 = td.eve1;
                int stnk = td.stn0;
                int stnl = td.stn1;
                
                int nCol0 = targMap[eve0];
                int nCol1 = targMap[eve1];
                
                // Skip REF-REF pairs (both are -1)
                if (nCol0 == -1 && nCol1 == -1) {
                    continue;
                }
                
                // Skip when targMap value >= numTarget (happens when targMap has gaps after ERR marking; design matrix has columns 0..numTarget-1 only).
                if ((nCol0 >= 0 && nCol0 >= N) || (nCol1 >= 0 && nCol1 >= N)) {
                    skipCountOutOfRange++;
                    if (skipCountOutOfRange <= 3) {
                        logger.warning(String.format(
                            "Skipping triple difference: targMap index out of range. eve0=%d, nCol0=%d, eve1=%d, nCol1=%d, numTarget=%d",
                            eve0, nCol0, eve1, nCol1, N));
                        SolverLogger.warning("TRD: Skipping triple difference (targMap index out of range).");
                    }
                    continue;
                }
                
                if (nCol1 >= 0) {
                    G.setEntry(rowIdx, 3 * nCol1, partialTbl[eve1][stnl][0] - partialTbl[eve1][stnk][0]);
                    G.setEntry(rowIdx, 3 * nCol1 + 1, partialTbl[eve1][stnl][1] - partialTbl[eve1][stnk][1]);
                    G.setEntry(rowIdx, 3 * nCol1 + 2, partialTbl[eve1][stnl][2] - partialTbl[eve1][stnk][2]);
                }
                
                if (nCol0 >= 0) {
                    G.setEntry(rowIdx, 3 * nCol0, -(partialTbl[eve0][stnl][0] - partialTbl[eve0][stnk][0]));
                    G.setEntry(rowIdx, 3 * nCol0 + 1, -(partialTbl[eve0][stnl][1] - partialTbl[eve0][stnk][1]));
                    G.setEntry(rowIdx, 3 * nCol0 + 2, -(partialTbl[eve0][stnl][2] - partialTbl[eve0][stnk][2]));
                }
                // Note: If nCol0 == -1 (REF event), no column entry is set (REF position is fixed, δx = 0)
                
                // Calculate residual (using partialTbl for both events, including REF)
                double cal0 = partialTbl[eve0][stnl][3] - partialTbl[eve0][stnk][3];
                double cal1 = partialTbl[eve1][stnl][3] - partialTbl[eve1][stnk][3];
                double lagCal = cal1 - cal0;
                double lagObs = td.tdTime;
                d[rowIdx] = lagObs - lagCal;
                
                rowIdx++;
                if (rowIdx % 100000 == 0 && rowIdx > 0) {
                    logger.fine("TRD matrixDG COO: row progress " + rowIdx + "/" + validRowCount);
                    SolverLogger.fine("TRD: matrixDG COO rows " + rowIdx);
                }
            }
            
            if (skipCountOutOfRange > 0) {
                logger.info("TRD: Skipped " + skipCountOutOfRange + " triple-diff row(s) in this matrix (targMap index >= numTarget=" + N + ").");
                SolverLogger.info("TRD: Skipped " + skipCountOutOfRange + " triple-diff(s) (targMap index out of range).");
            }
            
            logger.fine("TRD matrixDG COO: data rows done, adding constraint rows");
            SolverLogger.fine("TRD: matrixDG COO constraint rows");
            // Add constraint rows: sum{delta m} = 0 for each component (x, y, z)
            // Constraint row for x component: sum(delta_x_i) = 0
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount, 3 * k, 1.0);
            }
            d[validRowCount] = 0.0;
            
            // Constraint row for y component: sum(delta_y_i) = 0
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount + 1, 3 * k + 1, 1.0);
            }
            d[validRowCount + 1] = 0.0;
            
            // Constraint row for z component: sum(delta_z_i) = 0
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount + 2, 3 * k + 2, 1.0);
            }
            d[validRowCount + 2] = 0.0;
            
            logger.fine("TRD matrixDG: COO done (rows=" + M_constrained + ")");
            SolverLogger.fine("TRD: matrixDG COO done");
            return new Object[] { d, G };
        } else {
            OpenMapRealMatrix G = new OpenMapRealMatrix(M_constrained, 3 * N);
            
            int rowIdx = 0;
            int skipCountOutOfRange = 0;
            for (int m = 0; m < M; m++) {
                TripleDifference td = trpDiff.get(m);
                int eve0 = td.eve0;
                int eve1 = td.eve1;
                int stnk = td.stn0;
                int stnl = td.stn1;
                
                int nCol0 = targMap[eve0];
                int nCol1 = targMap[eve1];
                
                // Skip REF-REF pairs (both are -1)
                if (nCol0 == -1 && nCol1 == -1) {
                    continue;
                }
                
                // Skip when targMap value >= numTarget (happens when targMap has gaps after ERR marking).
                if ((nCol0 >= 0 && nCol0 >= N) || (nCol1 >= 0 && nCol1 >= N)) {
                    skipCountOutOfRange++;
                    if (skipCountOutOfRange <= 3) {
                        logger.warning(String.format(
                            "Skipping triple difference: targMap index out of range. eve0=%d, nCol0=%d, eve1=%d, nCol1=%d, numTarget=%d",
                            eve0, nCol0, eve1, nCol1, N));
                        SolverLogger.warning("TRD: Skipping triple difference (targMap index out of range).");
                    }
                    continue;
                }
                
                if (nCol1 >= 0) {
                    G.setEntry(rowIdx, 3 * nCol1, partialTbl[eve1][stnl][0] - partialTbl[eve1][stnk][0]);
                    G.setEntry(rowIdx, 3 * nCol1 + 1, partialTbl[eve1][stnl][1] - partialTbl[eve1][stnk][1]);
                    G.setEntry(rowIdx, 3 * nCol1 + 2, partialTbl[eve1][stnl][2] - partialTbl[eve1][stnk][2]);
                }
                
                if (nCol0 >= 0) {
                    G.setEntry(rowIdx, 3 * nCol0, -(partialTbl[eve0][stnl][0] - partialTbl[eve0][stnk][0]));
                    G.setEntry(rowIdx, 3 * nCol0 + 1, -(partialTbl[eve0][stnl][1] - partialTbl[eve0][stnk][1]));
                    G.setEntry(rowIdx, 3 * nCol0 + 2, -(partialTbl[eve0][stnl][2] - partialTbl[eve0][stnk][2]));
                }
                // Note: If nCol0 == -1 (REF event), no column entry is set (REF position is fixed, δx = 0)
                
                // Calculate residual (using partialTbl for both events, including REF)
                double cal0 = partialTbl[eve0][stnl][3] - partialTbl[eve0][stnk][3];
                double cal1 = partialTbl[eve1][stnl][3] - partialTbl[eve1][stnk][3];
                double lagCal = cal1 - cal0;
                double lagObs = td.tdTime;
                d[rowIdx] = lagObs - lagCal;
                
                rowIdx++;
                if (rowIdx % 100000 == 0 && rowIdx > 0) {
                    logger.fine("TRD matrixDG OpenMap: row progress " + rowIdx + "/" + validRowCount);
                }
            }
            
            if (skipCountOutOfRange > 0) {
                logger.info("TRD: Skipped " + skipCountOutOfRange + " triple-diff row(s) in this matrix (targMap index >= numTarget=" + N + ").");
                SolverLogger.info("TRD: Skipped " + skipCountOutOfRange + " triple-diff(s) (targMap index out of range).");
            }
            
            logger.fine("TRD matrixDG OpenMap: adding constraint rows");
            // Add constraint rows: sum{delta m} = 0 for each component (x, y, z)
            // Constraint row for x component: sum(delta_x_i) = 0
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount, 3 * k, 1.0);
            }
            d[validRowCount] = 0.0;
            
            // Constraint row for y component: sum(delta_y_i) = 0
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount + 1, 3 * k + 1, 1.0);
            }
            d[validRowCount + 1] = 0.0;
            
            // Constraint row for z component: sum(delta_z_i) = 0
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount + 2, 3 * k + 2, 1.0);
            }
            d[validRowCount + 2] = 0.0;
            
            logger.fine("TRD matrixDG: OpenMap done (rows=" + M_constrained + ")");
            SolverLogger.fine("TRD: matrixDG OpenMap done");
            return new Object[] { d, G };
        }
    }
    
    private double[][][] createPartialTblArray(Cluster<Point> cluster) {
        return createPartialTblArray(cluster, null);
    }
    
    /**
     * True if non-negative targMap entries are not exactly {@code 0 .. count-1}
     * (e.g. after marking ERR without renumbering remaining targets).
     */
    private static boolean targMapHasGaps(int[] targMap) {
        int maxT = -1;
        int cnt = 0;
        for (int v : targMap) {
            if (v >= 0) {
                cnt++;
                if (v > maxT) {
                    maxT = v;
                }
            }
        }
        return maxT >= 0 && maxT + 1 != cnt;
    }

    /**
     * Renumbers {@code targMap} so target events are contiguous {@code 0 .. n-1};
     * REF/ERR stay at {@code -1}. Returns the new target count {@code n}.
     */
    private static int compactTargMap(int[] targMap) {
        int next = 0;
        for (int i = 0; i < targMap.length; i++) {
            if (targMap[i] >= 0) {
                targMap[i] = next++;
            }
        }
        return next;
    }

    /** Returns true if the throwable indicates depth-out-of-range or no valid ray (1D velocity model limit); such points are marked ERR and excluded. */
    private static boolean isDepthOrArrivalFailure(Throwable t) {
        if (t == null) return false;
        if (t instanceof VelocityModelLoadException) return true;
        String msg = t.getMessage();
        if (msg != null && (msg.contains("No ray arrivals") || msg.contains("Depth may be outside model range") || msg.contains("depth") && msg.contains("outside"))) return true;
        return isDepthOrArrivalFailure(t.getCause());
    }
    
    /**
     * Creates a partial derivative table for a cluster of events.
     * When a point fails (e.g. no ray arrivals for depth), it is marked ERR and, if targMap is not null, targMap[i]=-1 so it is excluded from the design matrix.
     *
     * @param cluster the cluster of events
     * @param targMap optional; if non-null, set to -1 for any point that fails partial derivative calculation so caller can recompute numTarget
     * @return three-dimensional array of partial derivatives
     */
    private double[][][] createPartialTblArray(Cluster<Point> cluster, int[] targMap) {
        List<Point> points = cluster.getPoints();
        int numEvents = points.size();
        int numStations = stationTable.length;
        double[][][] partialTbl = new double[numEvents][numStations][4];
        logger.fine("TRD createPartialTblArray: start numEvents=" + numEvents + " numStations=" + numStations + " numJobs=" + numJobs);
        SolverLogger.fine("TRD: createPartialTblArray start (events=" + numEvents + ", stations=" + numStations + ")");
        
        if (numEvents <= 1 || numJobs <= 1) {
            int i = 0;
            for (Point point : points) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Cancelled");
                }
                if (i > 0 && i % 100 == 0) {
                    logger.finer("TRD createPartialTblArray: single-thread progress point " + i + "/" + numEvents);
                    SolverLogger.finest("TRD: partial progress " + i + "/" + numEvents);
                }
                int[] usedIdx = new int[numStations];
                for (int j = 0; j < numStations; j++) {
                    usedIdx[j] = j;
                }
                try {
                    Object[] tmp = partialDerivativeMatrix(stationTable, usedIdx, point);
                    double[][] dtdr = (double[][]) tmp[0];
                    double[] trvTime = (double[]) tmp[1];
                    for (int j = 0; j < numStations; j++) {
                        partialTbl[i][j][0] = dtdr[j][0];
                        partialTbl[i][j][1] = dtdr[j][1];
                        partialTbl[i][j][2] = dtdr[j][2];
                        partialTbl[i][j][3] = trvTime[j];
                    }
                } catch (Exception e) {
                    boolean depthOutOfRange = isDepthOrArrivalFailure(e);
                    String pointInfo = String.format("point %d (time: %s, lat=%.6f, lon=%.6f, dep=%.3f km)",
                        i, point.getTime(), point.getLat(), point.getLon(), point.getDep());
                    if (depthOutOfRange) {
                        logger.warning("TRD: " + pointInfo + " — depth outside velocity model range or no ray arrivals; marked ERR and excluded from relocation.");
                        SolverLogger.warning("TRD: " + pointInfo + " — depth outside model range or no arrivals; marked ERR, excluded.");
                    } else {
                        String errorMsg = String.format(
                            "Error creating partial derivative table for %s in cluster:\n  Error: %s\n  Number of stations: %d",
                            pointInfo, e.getMessage(), numStations);
                        logger.severe(errorMsg);
                        SolverLogger.severe("TRD: " + errorMsg);
                        if (e.getCause() != null) {
                            logger.severe("  Caused by: " + e.getCause().getMessage());
                            SolverLogger.severe("TRD: Caused by: " + e.getCause().getMessage());
                        }
                    }
                    points.get(i).setType("ERR");
                    if (targMap != null && i < targMap.length) {
                        targMap[i] = -1;
                    }
                } finally {
                    i++;
                }
            }
            logger.fine("TRD createPartialTblArray: single-thread done");
            SolverLogger.fine("TRD: createPartialTblArray done (single-thread)");
        } else {
            java.util.concurrent.ExecutorService executor =
                BatchExecutorFactory.newFixedThreadPoolBounded(
                        numJobs, BatchExecutorFactory.suggestedQueueCapacity(numEvents));
            java.util.List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
            
            try {
                for (int i = 0; i < numEvents; i++) {
                    final int eventIndex = i;
                    final Point point = points.get(i);
                    
                    java.util.concurrent.Future<Void> future = executor.submit(() -> {
                        int[] usedIdx = new int[numStations];
                        for (int j = 0; j < numStations; j++) {
                            usedIdx[j] = j;
                        }
                        try {
                            Object[] tmp = partialDerivativeMatrix(stationTable, usedIdx, point);
                            double[][] dtdr = (double[][]) tmp[0];
                            double[] trvTime = (double[]) tmp[1];
                            for (int j = 0; j < numStations; j++) {
                                partialTbl[eventIndex][j][0] = dtdr[j][0];
                                partialTbl[eventIndex][j][1] = dtdr[j][1];
                                partialTbl[eventIndex][j][2] = dtdr[j][2];
                                partialTbl[eventIndex][j][3] = trvTime[j];
                            }
                        } catch (Exception e) {
                            boolean depthOutOfRange = isDepthOrArrivalFailure(e);
                            String pointInfo = String.format("point %d (time: %s, lat=%.6f, lon=%.6f, dep=%.3f km)",
                                eventIndex, point.getTime(), point.getLat(), point.getLon(), point.getDep());
                            if (depthOutOfRange) {
                                logger.warning("TRD: " + pointInfo + " — depth outside velocity model range or no ray arrivals; marked ERR and excluded from relocation.");
                                SolverLogger.warning("TRD: " + pointInfo + " — depth outside model range or no arrivals; marked ERR, excluded.");
                            } else {
                                String errorMsg = String.format(
                                    "Error creating partial derivative table for %s in cluster:\n  Error: %s\n  Number of stations: %d",
                                    pointInfo, e.getMessage(), numStations);
                                logger.severe(errorMsg);
                                SolverLogger.severe("TRD: " + errorMsg);
                                if (e.getCause() != null) {
                                    logger.severe("  Caused by: " + e.getCause().getMessage());
                                    SolverLogger.severe("TRD: Caused by: " + e.getCause().getMessage());
                                }
                            }
                            points.get(eventIndex).setType("ERR");
                            if (targMap != null && eventIndex < targMap.length) {
                                targMap[eventIndex] = -1;
                            }
                        }
                        return null;
                    });
                    futures.add(future);
                }
                
                for (java.util.concurrent.Future<Void> future : futures) {
                    if (Thread.currentThread().isInterrupted()) {
                        executor.shutdownNow();
                        throw new RuntimeException("Cancelled");
                    }
                    try {
                        future.get();
                    } catch (java.util.concurrent.ExecutionException e) {
                        Throwable cause = e.getCause();
                        if (cause != null && "Cancelled".equals(cause.getMessage())) {
                            executor.shutdownNow();
                            throw new RuntimeException("Cancelled", cause);
                        }
                        logger.warning("Error in parallel partial derivative calculation: " + e.getMessage());
                        SolverLogger.warning("TRD: Error in parallel partial derivative calculation.");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        executor.shutdownNow();
                        logger.warning("Interrupted while waiting for parallel partial derivative calculation");
                        SolverLogger.warning("TRD: Interrupted while waiting for parallel partial derivative calculation.");
                        throw new RuntimeException("Cancelled", e);
                    }
                }
            logger.fine("TRD createPartialTblArray: parallel done (all futures completed)");
            SolverLogger.fine("TRD: createPartialTblArray done (parallel)");
            } finally {
                boolean interrupted = Thread.currentThread().isInterrupted();
                if (interrupted) {
                    executor.shutdownNow();
                } else {
                    executor.shutdown();
                }
                try {
                    long awaitSec = interrupted ? 3L : 60L;
                    if (!executor.awaitTermination(awaitSec, java.util.concurrent.TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        return partialTbl;
    }
    
    /**
     * Calculates the median of a list of values.
     * 
     * @param values the list of values (will be sorted in-place)
     * @return the median value, or 0.0 if the list is empty
     */
    private double calculateMedian(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        Collections.sort(values);
        int size = values.size();
        if (size % 2 == 0) {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
        } else {
            return values.get(size / 2);
        }
    }
    
    /**
     * Calculates Turkey biweight weights for robust estimation.
     * This function downweights outliers based on their residuals.
     * 
     * @param residuals array of residuals
     * @param c tuning constant (default: 4.685)
     * @return array of weights (0 to 1)
     */
    private double[] turkeyBiweight(double[] residuals, double c) {
        if (residuals.length == 0) {
            return new double[0];
        }
        
        List<Double> residualList = new ArrayList<>();
        for (double r : residuals) {
            residualList.add(r);
        }
        double median = calculateMedian(residualList);
        
        List<Double> absDeviations = new ArrayList<>();
        for (double r : residuals) {
            absDeviations.add(Math.abs(r - median));
        }
        double mad = calculateMedian(absDeviations);
        
        double scale = c * mad + Double.MIN_VALUE;
        
        double[] weights = new double[residuals.length];
        for (int i = 0; i < residuals.length; i++) {
            double u = residuals[i] / scale;
            if (Math.abs(u) > 1.0) {
                weights[i] = 0.0;
            } else {
                weights[i] = Math.pow(1.0 - u * u, 2);
            }
        }
        
        return weights;
    }
    
    /**
     * Applies weights to a sparse matrix by multiplying each row by its weight.
     * 
     * @param G the matrix to weight
     * @param weights array of weights (one per row)
     * @return weighted matrix (same type as input)
     */
    private Object applyWeightsToMatrix(Object G, double[] weights) {
        if (G instanceof OpenMapRealMatrix) {
            OpenMapRealMatrix GMat = (OpenMapRealMatrix) G;
            OpenMapRealMatrix GWeighted = new OpenMapRealMatrix(GMat.getRowDimension(), GMat.getColumnDimension());
            for (int i = 0; i < GMat.getRowDimension(); i++) {
                for (int j = 0; j < GMat.getColumnDimension(); j++) {
                    double value = GMat.getEntry(i, j);
                    if (value != 0.0) {
                        GWeighted.setEntry(i, j, value * weights[i]);
                    }
                }
            }
            return GWeighted;
        } else if (G instanceof COOSparseMatrix) {
            COOSparseMatrix GMat = (COOSparseMatrix) G;
            COOSparseMatrix GWeighted = new COOSparseMatrix(GMat.getRowDimension(), GMat.getColumnDimension());
            for (int i = 0; i < GMat.getRowDimension(); i++) {
                for (int j = 0; j < GMat.getColumnDimension(); j++) {
                    double value = GMat.getEntry(i, j);
                    if (value != 0.0) {
                        GWeighted.setEntry(i, j, value * weights[i]);
                    }
                }
            }
            return GWeighted;
        } else {
            throw new IllegalArgumentException("Unknown matrix type: " + G.getClass().getName());
        }
    }
    
    /**
     * Applies weights to a vector by multiplying each element by its weight.
     * 
     * @param d the vector to weight
     * @param weights array of weights (one per element)
     * @return weighted vector
     */
    private double[] applyWeightsToVector(double[] d, double[] weights) {
        if (d.length != weights.length) {
            throw new IllegalArgumentException("Vector and weights must have the same length");
        }
        double[] dWeighted = new double[d.length];
        for (int i = 0; i < d.length; i++) {
            dWeighted[i] = d[i] * weights[i];
        }
        return dWeighted;
    }
    
    /**
     * Calculates residuals: residuals = d - G * dm
     * 
     * @param G the design matrix
     * @param d the residual vector
     * @param dm the solution vector
     * @return array of residuals
     */
    private double[] calculateResiduals(Object G, double[] d, double[] dm) {
        if (dm == null) {
            throw new IllegalArgumentException("Solution vector dm cannot be null");
        }
        
        int gCols;
        if (G instanceof OpenMapRealMatrix) {
            OpenMapRealMatrix GMat = (OpenMapRealMatrix) G;
            gCols = GMat.getColumnDimension();
        } else if (G instanceof COOSparseMatrix) {
            COOSparseMatrix GMat = (COOSparseMatrix) G;
            gCols = GMat.getColumnDimension();
        } else {
            throw new IllegalArgumentException("Unknown matrix type: " + G.getClass().getName());
        }
        
        if (dm.length != gCols) {
            throw new IllegalArgumentException(String.format(
                "Dimension mismatch in calculateResiduals: G column dimension (%d) != dm length (%d). " +
                "This may occur if numTarget changed between iterations due to ERR marking.",
                gCols, dm.length));
        }
        
        double[] residuals = new double[d.length];
        
        if (G instanceof OpenMapRealMatrix) {
            OpenMapRealMatrix GMat = (OpenMapRealMatrix) G;
            double[] Gdm = GMat.operate(dm);
            if (Gdm.length != d.length) {
                throw new IllegalArgumentException(String.format(
                    "Dimension mismatch: G * dm length (%d) != d length (%d)",
                    Gdm.length, d.length));
            }
            for (int i = 0; i < d.length; i++) {
                residuals[i] = d[i] - Gdm[i];
            }
        } else if (G instanceof COOSparseMatrix) {
            COOSparseMatrix GMat = (COOSparseMatrix) G;
            double[] Gdm = GMat.operate(dm);
            if (Gdm.length != d.length) {
                throw new IllegalArgumentException(String.format(
                    "Dimension mismatch: G * dm length (%d) != d length (%d)",
                    Gdm.length, d.length));
            }
            for (int i = 0; i < d.length; i++) {
                residuals[i] = d[i] - Gdm[i];
            }
        } else {
            throw new IllegalArgumentException("Unknown matrix type: " + G.getClass().getName());
        }
        
        return residuals;
    }
    
    /**
     * @return the filtered triple difference
     */
    private List<TripleDifference> filterTripDiffByDistance(List<TripleDifference> tripDiff, int distKm) {
        List<TripleDifference> filtered = new ArrayList<>();
        for (TripleDifference td : tripDiff) {
            if (td.distKm < distKm) {
                filtered.add(td);
            }
        }
        return filtered;
    }

    /**
     * Selects triple-difference data by residual: sort by residual (smallest first), NaN last,
     * take 1.5 * maxTripleDiffCount as candidate pool, then randomly select maxTripleDiffCount from it.
     * Then ensures every event is covered by at least one selected triple-diff (adds from rest if needed).
     *
     * @param tripDiff full list of triple differences (e.g. from loadTripleDiff)
     * @return list after sorting, random selection, and coverage guarantee
     */
    private List<TripleDifference> selectTripleDiffByResidual(List<TripleDifference> tripDiff) {
        if (maxTripleDiffCount == null || maxTripleDiffCount <= 0) {
            return tripDiff;
        }
        List<TripleDifference> sorted = new ArrayList<>(tripDiff);
        sorted.sort((a, b) -> {
            boolean aNaN = Double.isNaN(a.residual);
            boolean bNaN = Double.isNaN(b.residual);
            if (aNaN && bNaN) return 0;
            if (aNaN) return 1;
            if (bNaN) return -1;
            return Double.compare(a.residual, b.residual);
        });
        int poolSize = Math.min(sorted.size(), (int) Math.ceil(maxTripleDiffCount * 1.5));
        if (poolSize <= maxTripleDiffCount) {
            return sorted;
        }
        List<TripleDifference> pool = new ArrayList<>(sorted.subList(0, poolSize));
        Random rnd = new Random();
        Collections.shuffle(pool, rnd);
        List<TripleDifference> selected = new ArrayList<>(pool.subList(0, maxTripleDiffCount));
        java.util.Set<TripleDifference> selectedSet = new java.util.HashSet<>(selected);
        java.util.Set<Integer> covered = new java.util.HashSet<>();
        for (TripleDifference td : selected) {
            covered.add(td.eve0);
            covered.add(td.eve1);
        }
        java.util.Set<Integer> allEvents = new java.util.HashSet<>();
        for (TripleDifference td : sorted) {
            allEvents.add(td.eve0);
            allEvents.add(td.eve1);
        }
        java.util.Set<Integer> uncovered = new java.util.HashSet<>(allEvents);
        uncovered.removeAll(covered);
        int startForCoverage = poolSize;
        for (int r = startForCoverage; r < sorted.size() && !uncovered.isEmpty(); r++) {
            TripleDifference td = sorted.get(r);
            if (selectedSet.contains(td)) continue;
            boolean coversAny = false;
            if (uncovered.contains(td.eve0)) { uncovered.remove(td.eve0); coversAny = true; }
            if (uncovered.contains(td.eve1)) { uncovered.remove(td.eve1); coversAny = true; }
            if (coversAny) {
                selected.add(td);
                selectedSet.add(td);
            }
        }
        if (!uncovered.isEmpty()) {
            for (int r = 0; r < poolSize && !uncovered.isEmpty(); r++) {
                TripleDifference td = sorted.get(r);
                if (selectedSet.contains(td)) continue;
                boolean coversAny = false;
                if (uncovered.contains(td.eve0)) { uncovered.remove(td.eve0); coversAny = true; }
                if (uncovered.contains(td.eve1)) { uncovered.remove(td.eve1); coversAny = true; }
                if (coversAny) {
                    selected.add(td);
                    selectedSet.add(td);
                }
            }
        }
        int added = selected.size() - maxTripleDiffCount;
        if (added > 0) {
            logger.info("TRD: Pool " + poolSize + " (1.5×limit), random select " + maxTripleDiffCount + ", added " + added + " for coverage (total " + selected.size() + "); had " + tripDiff.size());
            SolverLogger.info("TRD: Pool 1.5×limit, random select " + maxTripleDiffCount + ", +" + added + " for coverage (total " + selected.size() + ").");
        } else {
            logger.info("TRD: Pool " + poolSize + " (1.5×limit), random select " + maxTripleDiffCount + "; had " + tripDiff.size());
            SolverLogger.info("TRD: Using " + maxTripleDiffCount + " triple differences (from 1.5× pool, random), was " + tripDiff.size());
        }
        return selected;
    }

    /**
     * Computes the final triple-difference residuals (observed - predicted at current positions)
     * and returns per-event RMS in seconds. Used after relocation to set each event's RMS in the output catalog.
     *
     * @param cluster cluster with relocated points (final positions)
     * @param tripDiff full list of triple differences for this cluster
     * @param targMap mapping from event index to target index (-1 for REF)
     * @param numTarget number of target events
     * @return array of RMS per event (same order as cluster.getPoints()); NaN if no residuals for that event
     */
    private double[] computeFinalRmsPerEvent(Cluster<Point> cluster, List<TripleDifference> tripDiff,
                                             int[] targMap, int numTarget) {
        List<Point> points = cluster.getPoints();
        int numEvents = points.size();
        double[] rmsPerEvent = new double[numEvents];
        java.util.Arrays.fill(rmsPerEvent, Double.NaN);
        
        double[][][] partialTbl = createPartialTblArray(cluster);
        Object[] dG = matrixDG(tripDiff, cluster, partialTbl, 0, targMap, numTarget);
        double[] d = (double[]) dG[0];
        int validRowCount = d.length - 3;
        if (validRowCount <= 0) {
            return rmsPerEvent;
        }
        int M = tripDiff.size();
        int N = numTarget;
        List<int[]> rowToEve = new ArrayList<>();
        for (int m = 0; m < M; m++) {
            TripleDifference td = tripDiff.get(m);
            int nCol0 = targMap[td.eve0];
            int nCol1 = targMap[td.eve1];
            if (nCol0 == -1 && nCol1 == -1) {
                continue;
            }
            if ((nCol0 >= 0 && nCol0 >= N) || (nCol1 >= 0 && nCol1 >= N)) {
                continue;
            }
            rowToEve.add(new int[]{td.eve0, td.eve1});
        }
        double[] sumSq = new double[numEvents];
        int[] count = new int[numEvents];
        for (int i = 0; i < validRowCount && i < rowToEve.size(); i++) {
            double r = d[i];
            int eve0 = rowToEve.get(i)[0];
            int eve1 = rowToEve.get(i)[1];
            sumSq[eve0] += r * r;
            count[eve0]++;
            sumSq[eve1] += r * r;
            count[eve1]++;
        }
        for (int k = 0; k < numEvents; k++) {
            if (count[k] > 0) {
                rmsPerEvent[k] = Math.sqrt(sumSq[k] / count[k]);
            }
        }
        return rmsPerEvent;
    }

    private static void appendPointToCatalog(java.io.PrintWriter writer, Point p) {
        String filePath = p.getFilePath() != null ? p.getFilePath() : "";
        String type = p.getType() != null ? p.getType() : "";
        int cid = p.getCid();
        writer.printf("%s,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%s,%d%n",
            p.getTime(), p.getLat(), p.getLon(), p.getDep(),
            p.getElat(), p.getElon(), p.getEdep(), p.getRes(),
            filePath, type, cid);
    }

    private void writePointToDatFile(Point point) throws IOException {
        String outFilePath = outDir.resolve(point.getFilePath()).toString();
        File outFile = new File(outFilePath);
        outFile.getParentFile().mkdirs();
        PointsHandler pointsHandler = new PointsHandler();
        pointsHandler.setMainPoint(point);
        pointsHandler.writeDatFile(outFilePath, codeStrings);
    }
}

