package com.treloc.xtreloc.solver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Logger;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

import com.treloc.xtreloc.app.gui.service.CatalogLoader;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.io.TripleDifferenceIO;
import com.treloc.xtreloc.io.TripleDifferenceIO.TripleDifference;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.util.CatalogFileNameGenerator;

import edu.sc.seis.TauP.TauModelException;

/**
 * SpatialClustering
 * Performs spatial clustering on a set of points using DBSCAN algorithm
 * and calculates triple differences for clustered events.
 * 
 * @version 0.1
 * @since 2025-02-22
 * @author K.M.
 */
public class SpatialClustering extends SolverBase {
    private static final Logger logger = Logger.getLogger(SpatialClustering.class.getName());
    
    private Point refPoint;
    private int minPts;
    private double eps;
    private Double epsPercentile; // Data inclusion rate when eps < 0 (0-1, null means use elbow method)
    private String catalogFile;
    private String outputDir;
    private String targetDir;
    private boolean useBinaryFormat;
    private Double rmsThreshold; // RMS threshold for catalog filtering (null means no filtering)
    private Double locErrThreshold; // Location error threshold for catalog filtering (xerr and yerr must both be <= this value, null means no filtering)
    
    /**
     * Constructs a SpatialClustering object with the specified configuration.
     *
     * @param appConfig the application configuration
     * @throws TauModelException if there is an error in the Tau model
     */
    public SpatialClustering(AppConfig appConfig) throws TauModelException {
        super(appConfig);
        
        // Get clustering parameters from config
        if (appConfig.modes != null && appConfig.modes.containsKey("CLS")) {
            var clsConfig = appConfig.modes.get("CLS");
            this.catalogFile = clsConfig.catalogFile;
            if (clsConfig.outDirectory != null) {
                try {
                    File outputDirFile = clsConfig.outDirectory.toFile();
                    this.outputDir = outputDirFile.getAbsolutePath();
                    if (this.outputDir.equals("/") || this.outputDir.isEmpty()) {
                        this.outputDir = ".";
                    }
                } catch (Exception e) {
                    logger.warning("Failed to convert output directory path: " + e.getMessage());
                    this.outputDir = ".";
                }
            } else {
                this.outputDir = ".";
            }
            if (clsConfig.datDirectory != null) {
                try {
                    java.nio.file.Path targetDirPath = clsConfig.datDirectory;
                    if (!java.nio.file.Files.exists(targetDirPath)) {
                        throw new IllegalArgumentException(
                            String.format("Target directory does not exist: %s\n" +
                                "  Please check the datDirectory path in config.json for CLS mode",
                                targetDirPath));
                    }
                    if (!java.nio.file.Files.isDirectory(targetDirPath)) {
                        throw new IllegalArgumentException(
                            String.format("Target path is not a directory: %s\n" +
                                "  Please check the datDirectory path in config.json for CLS mode",
                                targetDirPath));
                    }
                    File targetDirFile = targetDirPath.toFile();
                    this.targetDir = targetDirFile.getAbsolutePath();
                    if (this.targetDir.equals("/") || this.targetDir.isEmpty()) {
                        this.targetDir = ".";
                    }
                    logger.info("Using target directory from config: " + this.targetDir);
                } catch (IllegalArgumentException e) {
                    // Re-throw validation errors
                    throw e;
                } catch (Exception e) {
                    logger.warning("Failed to convert target directory path: " + e.getMessage());
                    this.targetDir = null;
                }
            } else {
                this.targetDir = null;
                logger.info("No target directory specified in config (will use catalog file directory)");
            }
            this.minPts = clsConfig.minPts != null ? clsConfig.minPts : 4;
            this.eps = clsConfig.eps != null ? clsConfig.eps : -1.0; // Negative means auto-estimate
            this.epsPercentile = clsConfig.epsPercentile; // null means use elbow method
            this.rmsThreshold = clsConfig.rmsThreshold; // null means no filtering
            this.locErrThreshold = clsConfig.locErrThreshold; // null means no filtering
            this.useBinaryFormat = true;
            logger.info("SpatialClustering initialized: outputDir=" + this.outputDir + 
                       ", targetDir=" + (this.targetDir != null ? this.targetDir : "null") +
                       ", catalogFile=" + this.catalogFile + 
                       ", minPts=" + this.minPts + ", eps=" + this.eps +
                       ", epsPercentile=" + (this.epsPercentile != null ? this.epsPercentile : "null (elbow method)") +
                       ", rmsThreshold=" + (this.rmsThreshold != null ? this.rmsThreshold : "null (no filtering)") +
                       ", locErrThreshold=" + (this.locErrThreshold != null ? this.locErrThreshold : "null (no filtering)"));
        } else {
            throw new IllegalArgumentException("CLS mode configuration not found");
        }
        
        // Calculate reference point from station table median
        double refLat = new Median().evaluate(stationTable[0]);
        double refLon = new Median().evaluate(stationTable[1]);
        this.refPoint = new Point("", refLat, refLon, 0, 0, 0, 0, 0, "", "REF", -999);
    }
    
    /**
     * Starts the clustering process.
     * Reads catalog, performs clustering, and calculates triple differences.
     * 
     * @param catalogFile the input catalog file path (not used, uses config)
     * @param outputDir the output directory path (not used, uses config)
     * @throws TauModelException if there is an error in the Tau model
     */
    public void start(String catalogFile, String outputDir) throws TauModelException {
        try {
            // Load points from catalog
            List<Point> allPoints = loadPointsFromCatalog(this.catalogFile);
            
            // Check if cluster IDs are already set in the catalog
            boolean hasExistingClusterIds = false;
            Set<Integer> existingClusterIds = new HashSet<>();
            for (Point p : allPoints) {
                int cid = p.getCid();
                if (cid >= 0) {
                    hasExistingClusterIds = true;
                    existingClusterIds.add(cid);
                }
            }
            
            Set<Point> clusteredPoints;
            String clusteredCatalogFile;
            
            if (hasExistingClusterIds) {
                // Cluster IDs are already set - skip clustering and use existing IDs
                int pointsWithCid = (int) allPoints.stream().filter(p -> p.getCid() >= 0).count();
                String skipMsg = String.format(
                    "INFO: Cluster IDs are already set in catalog. Skipping clustering and using existing cluster IDs.\n" +
                    "  Catalog file: %s\n" +
                    "  Total points: %d\n" +
                    "  Points with cluster ID >= 0: %d\n" +
                    "  Unique cluster IDs found: %s\n" +
                    "  Proceeding to calculate triple differences only.",
                    this.catalogFile, allPoints.size(), pointsWithCid, existingClusterIds.toString()
                );
                logger.info(skipMsg);
                System.out.println(skipMsg);
                System.err.println(skipMsg);
                
                clusteredPoints = new HashSet<>(allPoints);
                
                String safeOutputDir = this.outputDir;
                if (safeOutputDir == null || safeOutputDir.isEmpty() || safeOutputDir.equals("/")) {
                    safeOutputDir = ".";
                    logger.warning("outputDir is invalid (" + this.outputDir + "), using current directory");
                }
                File outputDirFile = new File(safeOutputDir);
                if (!outputDirFile.exists()) {
                    outputDirFile.mkdirs();
                    logger.info("Created output directory: " + outputDirFile.getAbsolutePath());
                }
                
                // Write dat files for all points to output directory
                int datFilesWritten = 0;
                int datFilesSkipped = 0;
                for (Point point : clusteredPoints) {
                    File outputDatFile = null;
                    try {
                        String datFileName;
                        if (point.getFilePath() != null && !point.getFilePath().isEmpty()) {
                            File datFilePath = new File(point.getFilePath());
                            datFileName = datFilePath.getName();
                            if (!datFileName.toLowerCase().endsWith(".dat")) {
                                datFileName = datFileName + ".dat";
                            }
                        } else {
                            String timeStr = point.getTime();
                            if (timeStr.contains("T") || timeStr.contains("-")) {
                                datFileName = convertTimeToDatFileName(timeStr) + ".dat";
                            } else {
                                datFileName = timeStr + ".dat";
                            }
                        }
                        
                        outputDatFile = new File(outputDirFile, datFileName);
                        if (outputDatFile.getParentFile() != null) {
                            outputDatFile.getParentFile().mkdirs();
                        }
                        
                        PointsHandler pointsHandler = new PointsHandler();
                        pointsHandler.setMainPoint(point);
                        pointsHandler.writeDatFile(outputDatFile.getAbsolutePath(), this.codeStrings);
                        
                        String relativePath = outputDatFile.getName();
                        point.setFilePath(relativePath);
                        
                        datFilesWritten++;
                    } catch (Exception e) {
                        StringBuilder errorMsg = new StringBuilder("Failed to write dat file for point in CLS mode:\n");
                        errorMsg.append("  Point time: ").append(point.getTime()).append("\n");
                        String outputPath = (outputDatFile != null) ? outputDatFile.getAbsolutePath() : "unknown";
                        errorMsg.append("  Output file: ").append(outputPath).append("\n");
                        errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
                        if (e.getCause() != null) {
                            errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
                        }
                        String errorStr = errorMsg.toString();
                        logger.warning(errorStr);
                        System.err.println("WARNING: " + errorStr);
                        datFilesSkipped++;
                    }
                }
                logger.info("Dat files written: " + datFilesWritten + ", skipped: " + datFilesSkipped);
                
                File outputCatalogFile = CatalogFileNameGenerator.generateCatalogFileName(
                    this.catalogFile, "CLS", new File(safeOutputDir));
                writePointsToCatalog(new ArrayList<>(clusteredPoints), outputCatalogFile.getAbsolutePath());
                logger.info("Catalog saved to: " + outputCatalogFile.getAbsolutePath());
                
                clusteredCatalogFile = outputCatalogFile.getAbsolutePath();
            } else {
                // No cluster IDs set - proceed with clustering
                logger.info("Info: Proceeding with clustering (parameters: eps=" + this.eps + ", minPts=" + this.minPts + ")...");
                
                // Run clustering
                List<Cluster<Point>> clusters = runClustering(allPoints, refPoint);
                
                // Update cluster IDs
                clusteredPoints = new HashSet<>();
                for (Cluster<Point> cluster : clusters) {
                    clusteredPoints.addAll(cluster.getPoints());
                    for (Point point : cluster.getPoints()) {
                        allPoints.remove(point); // allPoints -> Noise Points
                    }
                }
                for (Point noisePoint : allPoints) {
                    noisePoint.setCid(0);
                }
                clusteredPoints.addAll(allPoints);
                int totalClusteredEvents = clusteredPoints.size() - allPoints.size();
                int noiseEvents = allPoints.size();
                logger.info("Clustering completed: " + clusters.size() + " clusters with " + totalClusteredEvents + " events, " + 
                           noiseEvents + " noise points (CID=0)");
                
                String safeOutputDir = this.outputDir;
                if (safeOutputDir == null || safeOutputDir.isEmpty() || safeOutputDir.equals("/")) {
                    safeOutputDir = ".";
                    logger.warning("outputDir is invalid (" + this.outputDir + "), using current directory");
                }
                File outputDirFile = new File(safeOutputDir);
                if (!outputDirFile.exists()) {
                    outputDirFile.mkdirs();
                    logger.info("Created output directory: " + outputDirFile.getAbsolutePath());
                }
                
                // Write dat files for all points to output directory
                int datFilesWritten = 0;
                int datFilesSkipped = 0;
                for (Point point : clusteredPoints) {
                    File outputDatFile = null;
                    try {
                        String datFileName;
                        if (point.getFilePath() != null && !point.getFilePath().isEmpty()) {
                            File datFilePath = new File(point.getFilePath());
                            datFileName = datFilePath.getName();
                            if (!datFileName.toLowerCase().endsWith(".dat")) {
                                datFileName = datFileName + ".dat";
                            }
                        } else {
                            String timeStr = point.getTime();
                            if (timeStr.contains("T") || timeStr.contains("-")) {
                                datFileName = convertTimeToDatFileName(timeStr) + ".dat";
                            } else {
                                datFileName = timeStr + ".dat";
                            }
                        }
                        
                        outputDatFile = new File(outputDirFile, datFileName);
                        if (outputDatFile.getParentFile() != null) {
                            outputDatFile.getParentFile().mkdirs();
                        }
                        
                        PointsHandler pointsHandler = new PointsHandler();
                        pointsHandler.setMainPoint(point);
                        pointsHandler.writeDatFile(outputDatFile.getAbsolutePath(), this.codeStrings);
                        
                        String relativePath = outputDatFile.getName();
                        point.setFilePath(relativePath);
                        
                        datFilesWritten++;
                    } catch (Exception e) {
                        StringBuilder errorMsg = new StringBuilder("Failed to write dat file for point in CLS mode:\n");
                        errorMsg.append("  Point time: ").append(point.getTime()).append("\n");
                        String outputPath = (outputDatFile != null) ? outputDatFile.getAbsolutePath() : "unknown";
                        errorMsg.append("  Output file: ").append(outputPath).append("\n");
                        errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
                        if (e.getCause() != null) {
                            errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
                        }
                        String errorStr = errorMsg.toString();
                        logger.warning(errorStr);
                        System.err.println("WARNING: " + errorStr);
                        datFilesSkipped++;
                    }
                }
                logger.info("Dat files written: " + datFilesWritten + ", skipped: " + datFilesSkipped);
                
                File outputCatalogFile = CatalogFileNameGenerator.generateCatalogFileName(
                    this.catalogFile, "CLS", new File(safeOutputDir));
                writePointsToCatalog(new ArrayList<>(clusteredPoints), outputCatalogFile.getAbsolutePath());
                logger.info("Clustered catalog saved to: " + outputCatalogFile.getAbsolutePath());
                
                clusteredCatalogFile = outputCatalogFile.getAbsolutePath();
            }
            
            int clusterId = 1;
            int totalTripleDiffs = 0;
            logger.info("Starting triple difference calculation for clusters...");
            while (true) {
                List<Point> clusterPoints = loadPointsFromCatalogByCluster(clusteredCatalogFile, clusterId);
                if (clusterPoints.isEmpty()) {
                    logger.info("No more clusters found. Stopping at clusterId=" + clusterId);
                    break;
                }
                
                logger.info("Processing cluster " + clusterId + " with " + clusterPoints.size() + " points");
                
                // Check if points have lag tables
                int pointsWithLagTable = 0;
                for (Point p : clusterPoints) {
                    if (p.getLagTable() != null) {
                        pointsWithLagTable++;
                    }
                }
                logger.info("Cluster " + clusterId + ": " + pointsWithLagTable + " out of " + clusterPoints.size() + " points have lag tables");
                
                List<TripleDifference> trpDiff = calcTripleDifferences(clusterPoints, clusterId);
                logger.info("Cluster " + clusterId + ": calculated " + trpDiff.size() + " triple differences");
                
                if (trpDiff.isEmpty()) {
                    String warningMsg = "Cluster " + clusterId + ": No triple differences calculated. This may be because lag tables are missing or no matching pairs found.";
                    logger.warning(warningMsg);
                    System.err.println("WARNING: " + warningMsg);
                } else {
                    saveTripleDifferences(trpDiff, clusterId);
                    totalTripleDiffs += trpDiff.size();
                    logger.info("Cluster " + clusterId + ": " + trpDiff.size() + " triple differences saved");
                }
                clusterId++;
            }
            
            logger.info("Total triple differences saved: " + totalTripleDiffs);
            logger.info("Triple difference files saved to: " + this.outputDir);
            
        } catch (IOException e) {
            String errorMsg = "ERROR in clustering process: " + e.getMessage();
            if (e.getCause() != null) {
                errorMsg += "\n  Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
            }
            logger.severe(errorMsg);
            System.err.println(errorMsg);
            e.printStackTrace(System.err);
            throw new RuntimeException("Clustering failed", e);
        }
    }
    
    /**
     * Runs the clustering process using DBSCAN with distances in kilometers.
     *
     * @param points   the list of points to be clustered
     * @param refPoint the reference point for clustering
     * @return a list of clusters formed
     */
    public List<Cluster<Point>> runClustering(List<Point> points, Point refPoint) {
        // Use this.eps to ensure we use the current configuration value
        double currentEps = this.eps;
        
        if (currentEps <= 0) {
            List<Double> kDistances = computeKDistance(points, this.minPts);
            double estimatedEps;
            
            if (this.epsPercentile != null && this.epsPercentile > 0 && this.epsPercentile <= 1) {
                // Use percentile method: select epsilon that includes the specified percentage of data
                int percentileIndex = (int) Math.round((kDistances.size() - 1) * this.epsPercentile);
                percentileIndex = Math.max(0, Math.min(percentileIndex, kDistances.size() - 1));
                estimatedEps = kDistances.get(percentileIndex);
                
                logger.info(
                    "Negative 'clsEps' (=" + currentEps + ") is set" +
                    "\nUsing percentile method: " + (this.epsPercentile * 100) + "% of data included" +
                    "\nEstimated epsilon: " + estimatedEps + " km" +
                    "\nMin samples: " + this.minPts
                );
            } else {
                // Use elbow method (default)
                estimatedEps = findElbowWithDist(kDistances);
                
                logger.info(
                    "Negative 'clsEps' (=" + currentEps + ") is set" +
                    "\nUsing elbow method" +
                    "\nEstimated epsilon: " + estimatedEps + " km" +
                    "\nMin samples: " + this.minPts
                );
            }
            
            // Store k-distances for plotting (will be accessed via getter)
            this.kDistances = kDistances;
            this.estimatedEps = estimatedEps;
            currentEps = estimatedEps;
        } else {
            logger.info(
                "Given epsilon: " + currentEps + " km" +
                "\nMin samples: " + this.minPts
            );
        }
        
        DBSCANClusterer<Point> clusterer = new DBSCANClusterer<>(currentEps, this.minPts, new HaversineDistance());
        List<Cluster<Point>> clusters = clusterer.cluster(points);
        int clusterId = 1;
        for (Cluster<Point> cluster : clusters) {
            for (Point point : cluster.getPoints()) {
                point.setCid(clusterId);
            }
            clusterId++;
        }
        
        logger.info("There are " + clusters.size() + " clusters.");
        for (Cluster<Point> cluster : clusters) {
            logger.info("CID-" + cluster.getPoints().get(0).getCid() + " has " + cluster.getPoints().size() + " events.");
        }
        return clusters;
    }
    
    // Store k-distances for plotting
    private List<Double> kDistances;
    private double estimatedEps;
    
    /**
     * Gets the k-distances for plotting.
     * 
     * @return list of k-distances
     */
    public List<Double> getKDistances() {
        return kDistances;
    }
    
    /**
     * Gets the estimated epsilon value.
     * 
     * @return estimated epsilon
     */
    public double getEstimatedEps() {
        return estimatedEps;
    }
    
    /**
     * Computes the k-distance for each point in the list.
     *
     * @param points the list of points
     * @param k      the number of nearest neighbors to consider
     * @return a list of k-distances
     */
    private static List<Double> computeKDistance(List<Point> points, int k) {
        HaversineDistance distance = new HaversineDistance();
        List<Double> kDistances = new ArrayList<>();
        
        for (Point p : points) {
            List<Double> distances = points.stream()
                    .filter(other -> other != p) // Exclude itself
                    .map(other -> distance.compute(p.getPoint(), other.getPoint()))
                    .sorted()
                    .collect(Collectors.toList());
            
            if (distances.size() >= k) {
                kDistances.add(distances.get(k - 1));
            }
        }
        kDistances.sort(Double::compareTo);
        return kDistances;
    }
    
    /**
     * Finds the elbow point in the list of values using distance calculation.
     *
     * @param values the list of values
     * @return the elbow point value
     */
    public static double findElbowWithDist(List<Double> values) {
        int n = values.size();
        
        int kMin = 1, kMax = n;
        double valMin = values.get(0), valMax = values.get(n - 1);
        
        double a = valMax - valMin;
        double b = kMin - kMax;
        double c = kMax * valMin - kMin * valMax;
        double normFactor = Math.sqrt(a * a + b * b);
        
        // Calculate distance between each point and regression line
        int elbowIndex = 1;
        double maxDistance = 0;
        
        for (int i = 1; i < n - 1; i++) {
            int k = i + 1;
            double val = values.get(i);
            double distance = Math.abs(a * k + b * val + c) / normFactor;
            
            if (distance > maxDistance) {
                maxDistance = distance;
                elbowIndex = k;
            }
        }
        return values.get(elbowIndex);
    }
    
    /**
     * Calculates the triple differences for a cluster and returns the results.
     *
     * @param points   the list of points in the cluster
     * @param clusterId the ID of the cluster
     * @return the list of calculated triple-difference objects
     */
    private static List<TripleDifference> calcTripleDifferences(List<Point> points, int clusterId) {
        List<TripleDifference> tripleDifferences = new ArrayList<>();
        int skippedNoLagTable = 0;
        int skippedType = 0;
        int pairsProcessed = 0;
        
        for (int eid1 = 0; eid1 < points.size(); eid1++) {
            for (int eid2 = eid1 + 1; eid2 < points.size(); eid2++) {
                Point p1 = points.get(eid1);
                Point p2 = points.get(eid2);
                pairsProcessed++;
                
                if (p1.getType().equals("REF") && p2.getType().equals("REF")) {
                    skippedType++;
                    continue;
                } else if (p1.getType().equals("ERR") || p2.getType().equals("ERR")) {
                    skippedType++;
                    continue;
                }
                
                GeodesicData g = Geodesic.WGS84.Inverse(p1.getLat(), p1.getLon(), p2.getLat(), p2.getLon());
                double distKm = g.s12 / 1000.0; // Distance in km
                double[][] lagTable1 = p1.getLagTable();
                double[][] lagTable2 = p2.getLagTable();
                
                if (lagTable1 == null || lagTable2 == null) {
                    skippedNoLagTable++;
                    continue;
                }
                
                for (double[] row1 : lagTable1) {
                    for (double[] row2 : lagTable2) {
                        if (row1[0] == row2[0] && row1[1] == row2[1]) {
                            double diff = row2[2] - row1[2];
                            tripleDifferences.add(new TripleDifference(
                                eid1, eid2, (int) row1[0], (int) row1[1], 
                                diff, distKm, clusterId));
                        }
                    }
                }
            }
        }
        
        Logger logger = Logger.getLogger(SpatialClustering.class.getName());
        logger.info("Cluster " + clusterId + " triple diff calculation: " + 
                   pairsProcessed + " pairs processed, " + 
                   skippedNoLagTable + " skipped (no lag table), " +
                   skippedType + " skipped (type), " +
                   tripleDifferences.size() + " triple differences found");
        
        tripleDifferences.sort((a, b) -> Double.compare(a.distKm, b.distKm));
        return tripleDifferences;
    }
    
    /**
     * Saves the triple differences to a file (binary or CSV).
     *
     * @param tripleDifferences the list of triple differences
     * @param clusterId         the ID of the cluster
     */
    private void saveTripleDifferences(List<TripleDifference> tripleDifferences, int clusterId) {
        try {
            String safeOutputDir = this.outputDir;
            if (safeOutputDir == null || safeOutputDir.isEmpty() || safeOutputDir.equals("/")) {
                safeOutputDir = ".";
                logger.warning("outputDir is invalid (" + this.outputDir + "), using current directory");
            }
            
            File outputDirFile = new File(safeOutputDir);
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs();
                logger.info("Created output directory: " + outputDirFile.getAbsolutePath());
            }
            
            logger.info("Saving triple differences: useBinaryFormat=" + this.useBinaryFormat + ", clusterId=" + clusterId);
            
            File outputFile;
            if (this.useBinaryFormat) {
                outputFile = new File(safeOutputDir, "triple_diff_" + clusterId + ".bin");
                TripleDifferenceIO.saveBinary(tripleDifferences, outputFile);
                logger.info("Triple differences (binary) saved to: " + outputFile.getAbsolutePath());
            } else {
                outputFile = new File(safeOutputDir, "triple_diff_" + clusterId + ".csv");
                TripleDifferenceIO.saveCSV(tripleDifferences, outputFile);
                logger.info("Triple differences (CSV) saved to: " + outputFile.getAbsolutePath());
            }
        } catch (IOException e) {
            String errorMsg = "ERROR: Writing triple differences: " + e.getMessage();
            if (e.getCause() != null) {
                errorMsg += "\n  Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
            }
            logger.severe(errorMsg);
            System.err.println(errorMsg);
            e.printStackTrace(System.err);
        } catch (Exception e) {
            String errorMsg = "ERROR: Unexpected error writing triple differences: " + e.getMessage();
            if (e.getCause() != null) {
                errorMsg += "\n  Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
            }
            logger.severe(errorMsg);
            System.err.println(errorMsg);
            e.printStackTrace(System.err);
        }
    }
    
    /**
     * Loads points from a catalog file.
     * 
     * @param catalogFile the catalog file path
     * @return list of Point objects
     * @throws IOException if I/O error occurs
     */
    private List<Point> loadPointsFromCatalog(String catalogFile) throws IOException {
        List<Point> points = new ArrayList<>();
        File file = new File(catalogFile);
        
        // Load hypocenters from catalog
        List<Hypocenter> hypocenters = CatalogLoader.load(file);
        
        // Filter hypocenters based on thresholds
        int filteredByRms = 0;
        int filteredByLocErr = 0;
        int totalBeforeFilter = hypocenters.size();
        
        List<Hypocenter> filteredHypocenters = new ArrayList<>();
        for (Hypocenter h : hypocenters) {
            // Apply RMS threshold filter
            if (this.rmsThreshold != null && h.rms > this.rmsThreshold) {
                filteredByRms++;
                continue;
            }
            
            // Apply location error threshold filter (both xerr and yerr must be <= threshold)
            if (this.locErrThreshold != null) {
                if (h.xerr > this.locErrThreshold || h.yerr > this.locErrThreshold) {
                    filteredByLocErr++;
                    continue;
                }
            }
            
            filteredHypocenters.add(h);
        }
        
        logger.info(String.format("Catalog filtering: total=%d, passed=%d, filtered by RMS=%d, filtered by locErr=%d",
            totalBeforeFilter, filteredHypocenters.size(), filteredByRms, filteredByLocErr));
        
        // Convert Hypocenter to Point and load lag tables from .dat files
        int pointsWithLagTable = 0;
        int pointsWithoutLagTable = 0;
        for (Hypocenter h : filteredHypocenters) {
            int cid = (h.clusterId != null && h.clusterId >= 0) ? h.clusterId : -1;
            Point point = new Point(
                h.time, h.lat, h.lon, h.depth,
                h.xerr, h.yerr, h.zerr, h.rms,
                h.datFilePath != null ? h.datFilePath : "", h.type != null ? h.type : "", cid
            );
            
            // Try to load lag table from corresponding .dat file
            File datFile = null;
            
            // First, try in target directory (if specified) - this is the primary search location
            if (this.targetDir != null && !this.targetDir.isEmpty()) {
                File targetDirFile = new File(this.targetDir);
                if (targetDirFile.exists() && targetDirFile.isDirectory()) {
                    // Try using datFilePath if available
                    if (h.datFilePath != null && !h.datFilePath.isEmpty()) {
                        String datFilePath = h.datFilePath;
                        if (!datFilePath.endsWith(".dat")) {
                            datFilePath = datFilePath + ".dat";
                        }
                        datFile = new File(targetDirFile, datFilePath);
                    }
                    // If not found, try constructing from time
                    if ((datFile == null || !datFile.exists())) {
                        String datFileName = convertTimeToDatFileName(h.time) + ".dat";
                        datFile = new File(targetDirFile, datFileName);
                    }
                }
            }
            
            // If not found in target directory, try using datFilePath if available (relative path from catalog)
            if ((datFile == null || !datFile.exists()) && h.datFilePath != null && !h.datFilePath.isEmpty()) {
                File catalogDir = file.getParentFile();
                String datFilePath = h.datFilePath;
                if (!datFilePath.endsWith(".dat")) {
                    datFilePath = datFilePath + ".dat";
                }
                // First, try in original catalog file's directory (where .dat files are usually located)
                File originalCatalogDir = new File(this.catalogFile).getParentFile();
                if (originalCatalogDir != null && originalCatalogDir.exists()) {
                    datFile = new File(originalCatalogDir, datFilePath);
                }
                // If not found, try in current catalog file's directory
                if ((datFile == null || !datFile.exists()) && catalogDir != null) {
                    datFile = new File(catalogDir, datFilePath);
                }
                // If still not found, try as absolute path
                if (datFile == null || !datFile.exists()) {
                    datFile = new File(datFilePath);
                }
            }
            
            if (datFile == null || !datFile.exists()) {
                String datFileName = convertTimeToDatFileName(h.time) + ".dat";
                // First, try in original catalog file's directory (where .dat files are usually located)
                File originalCatalogDir = new File(this.catalogFile).getParentFile();
                if (originalCatalogDir != null && originalCatalogDir.exists()) {
                    datFile = new File(originalCatalogDir, datFileName);
                }
                // If not found, try in current catalog file's directory
                if ((datFile == null || !datFile.exists()) && file.getParentFile() != null) {
                    File catalogDir = file.getParentFile();
                    datFile = new File(catalogDir, datFileName);
                }
                // If still not found, try as absolute path
                if (datFile == null || !datFile.exists()) {
                    datFile = new File(datFileName);
                }
            }
            
            // If still not found, try in current directory
            if (datFile == null || !datFile.exists()) {
                String datFileName = convertTimeToDatFileName(h.time) + ".dat";
                datFile = new File(datFileName);
            }
            
            // If still not found, try in output directory (for CLS mode)
            if (datFile == null || !datFile.exists()) {
                String datFileName = convertTimeToDatFileName(h.time) + ".dat";
                File outputDirFile = new File(this.outputDir);
                if (outputDirFile.exists() && outputDirFile.isDirectory()) {
                    datFile = new File(outputDirFile, datFileName);
                }
            }
            
            if (datFile != null && datFile.exists()) {
                try {
                    com.treloc.xtreloc.solver.PointsHandler handler = new com.treloc.xtreloc.solver.PointsHandler();
                    handler.readDatFile(datFile.getAbsolutePath(), codeStrings, threshold);
                    Point datPoint = handler.getMainPoint();
                    if (datPoint != null && datPoint.getLagTable() != null) {
                        point.setLagTable(datPoint.getLagTable());
                        point.setUsedIdx(datPoint.getUsedIdx());
                        pointsWithLagTable++;
                    } else {
                        pointsWithoutLagTable++;
                        String warningMsg = "Lag table is null for " + datFile.getName();
                        logger.warning(warningMsg);
                        System.err.println("WARNING: " + warningMsg);
                    }
                } catch (Exception e) {
                    pointsWithoutLagTable++;
                    String warningMsg = "Failed to load lag table from " + datFile.getAbsolutePath() + ": " + e.getMessage();
                    logger.warning(warningMsg);
                    System.err.println("WARNING: " + warningMsg);
                }
            } else {
                pointsWithoutLagTable++;
                String warningMsg = "Dat file not found for time=" + h.time + " (tried: " + 
                             (datFile != null ? datFile.getAbsolutePath() : "null") + ")";
                logger.warning(warningMsg);
                System.err.println("WARNING: " + warningMsg);
            }
            
            points.add(point);
        }
        
        logger.info("Loaded " + points.size() + " points: " + pointsWithLagTable + " with lag tables, " + 
                   pointsWithoutLagTable + " without lag tables");
        
        return points;
    }
    
    /**
     * Loads points from a catalog file filtered by cluster ID.
     * 
     * @param catalogFile the catalog file path
     * @param clusterId the cluster ID to filter (-1 for all points, including noise)
     * @return list of Point objects with the specified cluster ID
     * @throws IOException if I/O error occurs
     */
    public List<Point> loadPointsFromCatalogByCluster(String catalogFile, int clusterId) throws IOException {
        List<Point> allPoints = loadPointsFromCatalog(catalogFile);
        if (clusterId < 0) {
            return allPoints;
        }
        return allPoints.stream()
            .filter(p -> p.getCid() == clusterId)
            .collect(Collectors.toList());
    }
    
    /**
     * Writes points to a catalog file with cluster IDs.
     * 
     * @param points the list of points
     * @param catalogFile the catalog file path
     * @throws IOException if I/O error occurs
     */
    private void writePointsToCatalog(List<Point> points, String catalogFile) throws IOException {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(catalogFile))) {
            writer.println("time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid");
            for (Point p : points) {
                String filePath = p.getFilePath() != null && !p.getFilePath().isEmpty() ? p.getFilePath() : "";
                String mode = p.getType() != null && !p.getType().isEmpty() ? p.getType() : "";
                String cid = p.getCid() >= 0 ? String.valueOf(p.getCid()) : "";
                String timeStr = convertTimeFormat(p.getTime());
                writer.printf("%s,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%s,%s%n",
                    timeStr, p.getLat(), p.getLon(), p.getDep(),
                    p.getElat(), p.getElon(), p.getEdep(), p.getRes(),
                    filePath, mode, cid);
            }
        }
    }
    
    /**
     * Converts time format from ISO 8601 to yymmdd.hhmmss format for .dat file names.
     * If the time is already in yymmdd.hhmmss format, returns it as is.
     * 
     * @param timeStr time string in ISO 8601 or yymmdd.hhmmss format
     * @return time string in yymmdd.hhmmss format
     */
    private String convertTimeToDatFileName(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "";
        }
        
        if (timeStr.contains(".") && timeStr.length() >= 13 && !timeStr.contains("T") && !timeStr.contains("-")) {
            return timeStr;
        }
        
        try {
            if (timeStr.contains("T") && timeStr.length() >= 19) {
                String year = timeStr.substring(2, 4);
                String month = timeStr.substring(5, 7);
                String day = timeStr.substring(8, 10);
                String hour = timeStr.substring(11, 13);
                String minute = timeStr.substring(14, 16);
                String second = timeStr.substring(17, 19);
                return year + month + day + "." + hour + minute + second;
            }
        } catch (Exception e) {
            logger.warning("Failed to convert time format for dat file name: " + timeStr + " - " + e.getMessage());
        }
        
        return timeStr;
    }
    
    /**
     * Converts time format from yymmdd.hhmmss to ISO 8601 format (YYYY-MM-DDTHH:MM:SS).
     * If the time is already in ISO 8601 format, returns it as is.
     * 
     * @param timeStr time string in yymmdd.hhmmss or ISO 8601 format
     * @return time string in ISO 8601 format
     */
    private String convertTimeFormat(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "";
        }
        
        if (timeStr.contains("T") && timeStr.contains("-")) {
            return timeStr;
        }
        
        try {
            if (timeStr.length() >= 13 && timeStr.contains(".")) {
                String datePart = timeStr.substring(0, 6);
                String timePart = timeStr.substring(7, 13);
                
                int yy = Integer.parseInt(datePart.substring(0, 2));
                int mm = Integer.parseInt(datePart.substring(2, 4));
                int dd = Integer.parseInt(datePart.substring(4, 6));
                int hh = Integer.parseInt(timePart.substring(0, 2));
                int min = Integer.parseInt(timePart.substring(2, 4));
                int ss = Integer.parseInt(timePart.substring(4, 6));
                
                int year = (yy < 50) ? (2000 + yy) : (1900 + yy);
                
                return String.format("%04d-%02d-%02dT%02d:%02d:%02d", year, mm, dd, hh, min, ss);
            }
        } catch (Exception e) {
            logger.warning("Failed to convert time format: " + timeStr + " - " + e.getMessage());
        }
        
        return timeStr;
    }
}

