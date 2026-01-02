package com.treloc.xtreloc.solver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.ml.clustering.Cluster;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.TripleDifferenceIO;
import com.treloc.xtreloc.io.TripleDifferenceIO.TripleDifference;

import edu.sc.seis.TauP.TauModelException;

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
    private java.util.function.Consumer<String> logConsumer;
    private ConvergenceCallback convergenceCallback;
    private int numJobs;

    /**
     * Sets the convergence callback for reporting convergence information.
     * 
     * @param callback the convergence callback
     */
    public void setConvergenceCallback(ConvergenceCallback callback) {
        this.convergenceCallback = callback;
    }

    /**
     * Constructs a new HypoTripleDiff solver with the specified configuration.
     * 
     * <p>The configuration must include:
     * <ul>
     *   <li>TRD mode configuration with catalog file and datDirectory</li>
     *   <li>Station file and TauP velocity model</li>
     *   <li>Optional solver parameters (iterNum, distKm, dampFact arrays)</li>
     * </ul>
     * 
     * @param appConfig the application configuration containing TRD mode settings
     * @throws TauModelException if there is an error loading the TauP velocity model
     * @throws IllegalArgumentException if TRD mode configuration is missing or invalid
     */
    public HypoTripleDiff(AppConfig appConfig) throws TauModelException {
        super(appConfig);
        
        this.hypBottom = appConfig.hypBottom;
        this.numJobs = appConfig.numJobs > 0 ? appConfig.numJobs : 
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        
        if (appConfig.modes != null && appConfig.modes.containsKey("TRD")) {
            var trdConfig = appConfig.modes.get("TRD");
            this.catalogFile = trdConfig.catalogFile;
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
            } else {
                throw new IllegalArgumentException(
                    "TRD mode requires datDirectory to be specified in config.json.\n" +
                    "  The datDirectory should point to the directory containing triple difference binary files (e.g., ./demo/dat-cls).\n" +
                    "  Please add \"datDirectory\" to the TRD mode configuration in config.json.");
            }
        } else {
            throw new IllegalArgumentException("TRD mode configuration not found");
        }
        
        if (appConfig.solver != null && appConfig.solver.containsKey("TRD")) {
            var trdSolver = appConfig.solver.get("TRD");
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
            clsConfig.modes = new java.util.HashMap<>();
            var modeConfig = new AppConfig.ModeConfig();
            modeConfig.catalogFile = this.catalogFile;
            clsConfig.modes.put("CLS", modeConfig);
            
            this.spatialCls = new SpatialClustering(clsConfig);
        } catch (Exception e) {
            logger.severe("Failed to initialize SpatialClustering: " + e.getMessage());
            throw new RuntimeException("Failed to initialize SpatialClustering", e);
        }
        
        if (appConfig.solver != null && appConfig.solver.containsKey("TRD")) {
            var trdSolver = appConfig.solver.get("TRD");
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
        if (node.has(key) && node.get(key).isArray()) {
            List<Integer> list = new ArrayList<>();
            for (var element : node.get(key)) {
                list.add(element.asInt());
            }
            return list.stream().mapToInt(i -> i).toArray();
        }
        return defaultValue;
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
     * @throws TauModelException if there is an error in the TauP velocity model
     * @throws IllegalArgumentException if clustering has not been performed or triple difference data is missing
     * @throws RuntimeException if relocation fails for any cluster
     */
    public void start(String datFile, String outputFile) throws TauModelException {
        String catalogPathToUse = null;
        try {
            if (this.catalogFile == null || this.catalogFile.isEmpty()) {
                StringBuilder errorMsg = new StringBuilder("Catalog file is required for TRD mode.\n");
                errorMsg.append("  Please specify catalogFile in the TRD mode configuration.\n");
                errorMsg.append("  Target directory: ").append(targetDir != null ? targetDir.toFile().getAbsolutePath() : "not set").append("\n");
                logger.severe(errorMsg.toString());
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
                        }
                    } else {
                        File catalogInTarget = new File(targetDirFile, this.catalogFile);
                        if (catalogInTarget.exists()) {
                            catalogPathToUse = catalogInTarget.getAbsolutePath();
                            logger.info("Using catalog file from target directory: " + catalogPathToUse);
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
                    throw new IllegalArgumentException(errorMsg.toString());
                }
            } else {
                catalogPathToUse = catalogFileObj.getAbsolutePath();
                logger.info("Using specified catalog file: " + catalogPathToUse);
            }
            
            
            logger.info("Loading points from catalog: " + catalogPathToUse);
            List<Point> allPoints;
            try {
                allPoints = spatialCls.loadPointsFromCatalogByCluster(catalogPathToUse, -1);
            } catch (Exception e) {
                String errorMsg = "Failed to load points from catalog file: " + catalogPathToUse + "\n" +
                    "  Error: " + e.getMessage() + "\n" +
                    "  Please check that the catalog file is valid and accessible.";
                logger.severe(errorMsg);
                if (e.getCause() != null) {
                    logger.severe("  Caused by: " + e.getCause().getMessage());
                }
                throw new IllegalArgumentException(errorMsg, e);
            }
            
            if (allPoints.isEmpty()) {
                String errorMsg = "No points found in catalog file: " + catalogPathToUse + "\n" +
                    "  Please ensure the catalog file contains valid event data.";
                logger.severe(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            
            logger.info("Loaded " + allPoints.size() + " points from catalog");
            
            boolean hasClustering = false;
            Set<Integer> clusterIds = new HashSet<>();
            for (Point p : allPoints) {
                int cid = p.getCid();
                if (cid >= 0) {
                    hasClustering = true;
                    clusterIds.add(cid);
                }
            }
            
            if (!hasClustering) {
                String errorMsg = "Clustering has not been done.\n" +
                    "  Catalog file: " + catalogPathToUse + "\n" +
                    "  Total points loaded: " + allPoints.size() + "\n" +
                    "  Points with cluster ID >= 0: 0\n" +
                    "  Please run CLS mode first to cluster the events before running TRD mode.";
                logger.severe(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            
            logger.info("Clustering detected. Found " + clusterIds.size() + " unique cluster ID(s)");
            
            List<Integer> missingBinClusters = new ArrayList<>();
            for (Integer cid : clusterIds) {
                if (cid > 0) {
                    List<TripleDifference> testLoad = loadTripleDiff(cid);
                    if (testLoad.isEmpty()) {
                        missingBinClusters.add(cid);
                    }
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
                throw new IllegalArgumentException(errorMsg.toString());
            }
            
            logger.info("Clustering confirmed. Found " + clusterIds.size() + " cluster(s) (including noise). " +
                       "Processing clusters: " + clusterIds.stream()
                           .filter(cid -> cid > 0)
                           .map(String::valueOf)
                           .collect(java.util.stream.Collectors.joining(", ")));
            
            File outputCatalogFileObj = com.treloc.xtreloc.util.CatalogFileNameGenerator.generateCatalogFileName(
                catalogPathToUse, "TRD", outDir.toFile());
            String outputCatalogFile = outputCatalogFileObj.getName();
            
            List<Point> relocatedPoints = new ArrayList<>();
            
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
                relocatedPoints.add(point);
            }
            
            int clusterId = 1;
            while (true) {
                // Check for interruption
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Triple difference relocation interrupted by user");
                    throw new RuntimeException("Triple difference relocation was interrupted");
                }
                
                List<Point> originalClusterPoints = spatialCls.loadPointsFromCatalogByCluster(catalogPathToUse, clusterId);
                    if (originalClusterPoints.isEmpty()) {
                    break;
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
                }
                if (errCount > 0) {
                    logger.info("Cluster " + clusterId + ": " + errCount + " ERR events excluded from relocation");
                }
                
                if (numTarget == 0) {
                    logger.warning("Cluster " + clusterId + " has no target events, skipping");
                    clusterId++;
                    continue;
                }
                
                Cluster<Point> cluster = new Cluster<>();
                clusterPoints.forEach(cluster::addPoint);
                
                List<TripleDifference> tripDiff = loadTripleDiff(clusterId);
                if (tripDiff.isEmpty()) {
                    logger.warning("No triple difference data found for cluster " + clusterId + ", skipping");
                    clusterId++;
                    continue;
                }
                
                for (int i = 0; i < iterNumArray.length; i++) {
                    // Check for interruption
                    if (Thread.currentThread().isInterrupted()) {
                        logger.info("Triple difference relocation interrupted by user");
                        throw new RuntimeException("Triple difference relocation was interrupted");
                    }
                    
                    int distKm = distKmArray[i];
                    int iterNum = iterNumArray[i];
                    int dampFact = dampFactArray[i];
                    
                    logger.info("Cluster " + clusterId + ", iteration stage " + (i + 1) + "/" + iterNumArray.length + 
                               " (distKm=" + distKm + ", iterNum=" + iterNum + ", dampFact=" + dampFact + ")");
                    
                    List<TripleDifference> filteredTripDiff = filterTripDiffByDistance(tripDiff, distKm);
                    
                    if (filteredTripDiff.isEmpty()) {
                        logger.warning("No triple difference data within " + distKm + " km for cluster " + clusterId + 
                                     " (total triple differences: " + tripDiff.size() + ")");
                        continue;
                    }
                    
                    logger.info("Cluster " + clusterId + ", stage " + (i + 1) + ": Using " + filteredTripDiff.size() + 
                               " triple differences (filtered from " + tripDiff.size() + " total)");
                    
                    double[] dm = null;
                    
                    for (int j = 0; j < iterNum; j++) {
                        // Check for interruption
                        if (Thread.currentThread().isInterrupted()) {
                            logger.info("Triple difference relocation interrupted by user");
                            throw new RuntimeException("Triple difference relocation was interrupted");
                        }
                        try {
                            logger.info("Cluster " + clusterId + ", stage " + (i + 1) + ", iteration " + (j + 1) + "/" + iterNum);
                            
                            boolean isLastStage = (i == iterNumArray.length - 1);
                            boolean isLastIteration = (j == iterNum - 1);
                            boolean shouldCalcVar = calcVar && isLastStage && isLastIteration;
                            
                            int currentNumTarget = 0;
                            int maxTargIdx = -1;
                            for (int idx : targMap) {
                                if (idx >= 0) {
                                    currentNumTarget++;
                                    if (idx > maxTargIdx) {
                                        maxTargIdx = idx;
                                    }
                                }
                            }
                            numTarget = currentNumTarget;
                            
                            if (maxTargIdx >= 0 && maxTargIdx + 1 != currentNumTarget) {
                                logger.warning("Cluster " + clusterId + ": targMap has gaps. maxTargIdx=" + maxTargIdx + 
                                             ", currentNumTarget=" + currentNumTarget + ". Using currentNumTarget.");
                            }
                            
                            if (numTarget == 0) {
                                logger.warning("Cluster " + clusterId + " has no valid target events after filtering, skipping remaining iterations");
                                break;
                            }
                            
                            double[][][] partialTbl = createPartialTblArray(cluster);
                            Object[] dG = matrixDG(filteredTripDiff, cluster, partialTbl, distKm, targMap, numTarget);
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
                            
                            // Check if dm length matches current expectedDmLength
                            // This can happen if numTarget changed between iterations (e.g., due to ERR marking)
                            if (dm != null && dm.length != expectedDmLength) {
                                logger.warning(String.format(
                                    "Cluster %d, stage %d, iteration %d: dm length mismatch. " +
                                    "Expected: %d (3 * numTarget), Actual: %d. " +
                                    "Resetting dm to null (numTarget may have changed due to ERR marking).",
                                    clusterId, i + 1, j + 1, expectedDmLength, dm.length));
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
                            
                            Object GWeighted = applyWeightsToMatrix(GObj, weights);
                            double[] dWeighted = applyWeightsToVector(d, weights);
                            
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
                            
                            if (convergenceCallback != null && result != null) {
                                double residualRMS = result.r2norm;
                                convergenceCallback.onClusterResidualUpdate(clusterId, j, residualRMS);
                            }
                            
                            if (result == null) {
                                throw new IllegalArgumentException("LSQR solver returned null result.");
                            }
                            
                            dm = result.x;
                            
                            if (dm == null || dm.length == 0) {
                                throw new IllegalArgumentException("LSQR solver returned empty solution vector.");
                            }
                            
                            if (dm.length != expectedDmLength) {
                                throw new IndexOutOfBoundsException(
                                    "Solution vector dm length mismatch. " +
                                    "Expected: " + expectedDmLength + " (3 * numTarget), " +
                                    "Actual: " + dm.length + ". " +
                                    "numTarget=" + numTarget);
                            }
                        
                            double[] var = null;
                            if (shouldCalcVar && result != null && result.var != null) {
                                var = result.var;
                            }
                            
                            for (int k = 0; k < clusterPoints.size(); k++) {
                                if (targMap[k] == -1) {
                                    continue;
                                }
                                Point point = clusterPoints.get(k);
                                int targIdx = targMap[k];
                                if (targIdx * 3 + 2 >= dm.length) {
                                    throw new IndexOutOfBoundsException(
                                        "Index out of bounds when accessing dm array for point " + k + ". " +
                                        "targIdx=" + targIdx + ", dm.length=" + dm.length +
                                        ". This may indicate a mismatch in the target mapping.");
                                }
                                double newLon = point.getLon() + dm[targIdx * 3];
                                double newLat = point.getLat() + dm[targIdx * 3 + 1];
                                double newDep = point.getDep() + dm[targIdx * 3 + 2];
 
                                if (newDep < stnBottom || newDep > hypBottom) {
                                    logger.warning("Point " + point.getTime() + " in cluster " + clusterId + 
                                                 " has depth " + newDep + " outside valid range [" + stnBottom + ", " + hypBottom + "]. Marking as ERR.");
                                    point.setType("ERR");
                                    targMap[k] = -1;
                                } else {
                                    point.setLon(newLon);
                                    point.setLat(newLat);
                                    point.setDep(newDep);
                                    
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
                            if (e.getCause() != null) {
                                logger.severe("  Caused by: " + e.getCause().getMessage());
                            }
                            throw new RuntimeException(errorMsg, e);
                        }
                    }
                }
                
                for (Point point : clusterPoints) {
                    PointsHandler pointsHandler = new PointsHandler();
                    pointsHandler.setMainPoint(point);
                    if (!point.getType().equals("ERR") && !point.getType().equals("REF")) {
                        point.setType("TRD");
                    }
                    String outFilePath = outDir.resolve(point.getFilePath()).toString();
                    File outFile = new File(outFilePath);
                    outFile.getParentFile().mkdirs();
                    pointsHandler.writeDatFile(outFilePath, codeStrings);
                    relocatedPoints.add(point);
                }
                
                clusterId++;
            }
            
            writePointsToCatalog(relocatedPoints, outDir.resolve(outputCatalogFile).toString());
            logger.info("Triple difference relocation completed. Output catalog: " + outputCatalogFile);
            
        } catch (IllegalArgumentException e) {
            StringBuilder errorMsg = new StringBuilder("Triple difference relocation failed (IllegalArgumentException):\n");
            errorMsg.append("  ").append(e.getMessage()).append("\n");
            errorMsg.append("  Catalog file: ").append(catalogPathToUse != null ? catalogPathToUse : "not determined").append("\n");
            errorMsg.append("  Target directory: ").append(targetDir != null ? targetDir.toFile().getAbsolutePath() : "not set").append("\n");
            errorMsg.append("  Output directory: ").append(outDir != null ? outDir.toFile().getAbsolutePath() : "not set").append("\n");
            logger.severe(errorMsg.toString());
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
            if (e.getCause() != null) {
                logger.severe("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            logger.severe("Stack trace:");
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            logger.severe(sw.toString());
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
                return new ArrayList<>();
            } else {
                logger.info("Using triple difference file from output directory: " + file.getAbsolutePath());
            }
        } else {
            logger.info("Using triple difference file from target directory: " + file.getAbsolutePath());
        }
        
        try {
            return TripleDifferenceIO.loadBinary(file);
        } catch (IOException e) {
            logger.severe("Error reading triple difference file: " + e.getMessage());
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
        
        // Count valid rows (excluding REF-REF pairs)
        int validRowCount = 0;
        for (int m = 0; m < M; m++) {
            TripleDifference td = trpDiff.get(m);
            int nCol0 = targMap[td.eve0];
            int nCol1 = targMap[td.eve1];
            // Skip REF-REF pairs (both are -1)
            if (nCol0 == -1 && nCol1 == -1) {
                continue;
            }
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
            COOSparseMatrix G = new COOSparseMatrix(M_constrained, 3 * N);
            
            int rowIdx = 0;
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
                
                // Skip if both are out of range
                if ((nCol0 >= 0 && nCol0 >= N) || (nCol1 >= 0 && nCol1 >= N)) {
                    logger.warning(String.format(
                        "Skipping triple difference: targMap index out of range. eve0=%d, nCol0=%d, eve1=%d, nCol1=%d, numTarget=%d",
                        eve0, nCol0, eve1, nCol1, N));
                    continue;
                }
                
                // Set entries for event1 (if it's a target event)
                if (nCol1 >= 0) {
                    G.setEntry(rowIdx, 3 * nCol1, partialTbl[eve1][stnl][0] - partialTbl[eve1][stnk][0]);
                    G.setEntry(rowIdx, 3 * nCol1 + 1, partialTbl[eve1][stnl][1] - partialTbl[eve1][stnk][1]);
                    G.setEntry(rowIdx, 3 * nCol1 + 2, partialTbl[eve1][stnl][2] - partialTbl[eve1][stnk][2]);
                }
                // Note: If nCol1 == -1 (REF event), no column entry is set (REF position is fixed, δx = 0)
                
                // Set entries for event0 (if it's a target event)
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
            }
            
            double constraintWeight = 0;
            
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount, 3 * k, constraintWeight);
            }
            d[validRowCount] = 0.0;
            
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount + 1, 3 * k + 1, constraintWeight);
            }
            d[validRowCount + 1] = 0.0;
            
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount + 2, 3 * k + 2, constraintWeight);
            }
            d[validRowCount + 2] = 0.0;
            
            return new Object[] { d, G };
        } else {
            OpenMapRealMatrix G = new OpenMapRealMatrix(M_constrained, 3 * N);
            
            int rowIdx = 0;
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
                
                // Skip if both are out of range
                if ((nCol0 >= 0 && nCol0 >= N) || (nCol1 >= 0 && nCol1 >= N)) {
                    logger.warning(String.format(
                        "Skipping triple difference: targMap index out of range. eve0=%d, nCol0=%d, eve1=%d, nCol1=%d, numTarget=%d",
                        eve0, nCol0, eve1, nCol1, N));
                    continue;
                }
                
                // Set entries for event1 (if it's a target event)
                if (nCol1 >= 0) {
                    G.setEntry(rowIdx, 3 * nCol1, partialTbl[eve1][stnl][0] - partialTbl[eve1][stnk][0]);
                    G.setEntry(rowIdx, 3 * nCol1 + 1, partialTbl[eve1][stnl][1] - partialTbl[eve1][stnk][1]);
                    G.setEntry(rowIdx, 3 * nCol1 + 2, partialTbl[eve1][stnl][2] - partialTbl[eve1][stnk][2]);
                }
                // Note: If nCol1 == -1 (REF event), no column entry is set (REF position is fixed, δx = 0)
                
                // Set entries for event0 (if it's a target event)
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
            }
            
            double constraintWeight = 0;
            
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount, 3 * k, constraintWeight);
            }
            d[validRowCount] = 0.0;
            
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount + 1, 3 * k + 1, constraintWeight);
            }
            d[validRowCount + 1] = 0.0;
            
            for (int k = 0; k < N; k++) {
                G.setEntry(validRowCount + 2, 3 * k + 2, constraintWeight);
            }
            d[validRowCount + 2] = 0.0;
            
            return new Object[] { d, G };
        }
    }
    
    /**
     * Creates a partial derivative table for a cluster of events.
     * 
     * <p>The table structure is:
     * <ul>
     *   <li>First dimension: event index</li>
     *   <li>Second dimension: station index</li>
     *   <li>Third dimension: partial derivatives [longitude, latitude, depth, travel time]</li>
     * </ul>
     * 
     * @param cluster the cluster of events
     * @return three-dimensional array of partial derivatives
     */
    private double[][][] createPartialTblArray(Cluster<Point> cluster) {
        List<Point> points = cluster.getPoints();
        int numEvents = points.size();
        int numStations = stationTable.length;
        double[][][] partialTbl = new double[numEvents][numStations][4];
        
        if (numEvents <= 1 || numJobs <= 1) {
            int i = 0;
            for (Point point : points) {
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
                    String errorMsg = String.format(
                        "Error creating partial derivative table for point %d (time: %s) in cluster:\n" +
                        "  Error: %s\n" +
                        "  Point coordinates: lat=%.6f, lon=%.6f, dep=%.3f\n" +
                        "  Number of stations: %d",
                        i, point.getTime(), e.getMessage(), point.getLat(), point.getLon(), point.getDep(), numStations);
                    logger.severe(errorMsg);
                    if (e.getCause() != null) {
                        logger.severe("  Caused by: " + e.getCause().getMessage());
                    }
                    points.get(i).setType("ERR");
                } finally {
                    i++;
                }
            }
        } else {
            java.util.concurrent.ExecutorService executor = 
                java.util.concurrent.Executors.newFixedThreadPool(numJobs);
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
                            String errorMsg = String.format(
                                "Error creating partial derivative table for point %d (time: %s) in cluster:\n" +
                                "  Error: %s\n" +
                                "  Point coordinates: lat=%.6f, lon=%.6f, dep=%.3f\n" +
                                "  Number of stations: %d",
                                eventIndex, point.getTime(), e.getMessage(), point.getLat(), point.getLon(), point.getDep(), numStations);
                            logger.severe(errorMsg);
                            if (e.getCause() != null) {
                                logger.severe("  Caused by: " + e.getCause().getMessage());
                            }
                            points.get(eventIndex).setType("ERR");
                        }
                        return null;
                    });
                    futures.add(future);
                }
                
                for (java.util.concurrent.Future<Void> future : futures) {
                    try {
                        future.get();
                    } catch (java.util.concurrent.ExecutionException e) {
                        logger.warning("Error in parallel partial derivative calculation: " + e.getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warning("Interrupted while waiting for parallel partial derivative calculation");
                        throw new RuntimeException("Partial derivative calculation was interrupted", e);
                    }
                }
            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
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
     * Calculates the arithmetic mean of a list of values.
     * 
     * @param values the list of values
     * @return the mean value, or 0.0 if the list is empty
     */
    private double calculateMean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
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
    
    private void writePointsToCatalog(List<Point> points, String catalogFile) throws IOException {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(catalogFile))) {
            writer.println("time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid");
            for (Point p : points) {
                String filePath = p.getFilePath() != null ? p.getFilePath() : "";
                String type = p.getType() != null ? p.getType() : "";
                int cid = p.getCid();
                writer.printf("%s,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%s,%d%n",
                    p.getTime(), p.getLat(), p.getLon(), p.getDep(),
                    p.getElat(), p.getElon(), p.getEdep(), p.getRes(),
                    filePath, type, cid);
            }
        }
    }
}

