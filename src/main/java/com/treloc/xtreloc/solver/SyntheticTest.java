package com.treloc.xtreloc.solver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.service.CatalogLoader;
import com.treloc.xtreloc.io.AppConfig;

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
public class SyntheticTest extends SolverBase {
    private static final Logger logger = Logger.getLogger(SyntheticTest.class.getName());
    private String catalogFile;
    private String outputDirectory;
    private final double phsErr;
    private final double locErr;
    private final double minSelectRate;
    private final double maxSelectRate;
    private final int randomSeed;
    private final boolean addLocationPerturbation;
    private Random rand;

    /**
     * Constructs a SyntheticTest object with the specified configuration.
     *
     * @param appConfig the application configuration
     * @throws TauModelException if there is an error in the Tau model
     */
    public SyntheticTest(AppConfig appConfig) throws TauModelException {
        this(appConfig, 100, 0.1, 0.03, 0.2, 0.4, false);
    }
    
    /**
     * Constructs a SyntheticTest object with the specified configuration and parameters.
     *
     * @param appConfig the application configuration
     * @param randomSeed random seed value
     * @param phsErr phase error (standard deviation for lag time perturbation)
     * @param locErr location error (standard deviation for location perturbation)
     * @param minSelectRate minimum selection rate for phase pairs
     * @param maxSelectRate maximum selection rate for phase pairs
     * @param addLocationPerturbation whether to add perturbation to location
     * @throws TauModelException if there is an error in the Tau model
     */
    public SyntheticTest(AppConfig appConfig, int randomSeed, double phsErr, double locErr, 
                        double minSelectRate, double maxSelectRate, boolean addLocationPerturbation) 
                        throws TauModelException {
        super(appConfig);
        
        this.randomSeed = randomSeed;
        this.phsErr = phsErr;
        this.locErr = locErr;
        this.minSelectRate = minSelectRate;
        this.maxSelectRate = maxSelectRate;
        this.addLocationPerturbation = addLocationPerturbation;
        this.rand = new Random(randomSeed);
        
        logger.info("SyntheticTest initialized with seed=" + randomSeed + 
                   ", phsErr=" + phsErr + ", locErr=" + locErr + 
                   ", selectRate=" + minSelectRate + "-" + maxSelectRate +
                   ", locationPerturbation=" + addLocationPerturbation);
        
        if (appConfig.modes != null && appConfig.modes.containsKey("SYN")) {
            AppConfig.ModeConfig synConfig = appConfig.modes.get("SYN");
            if (synConfig != null && synConfig.catalogFile != null) {
                this.catalogFile = synConfig.catalogFile;
                if (synConfig.outDirectory != null) {
                    this.outputDirectory = synConfig.outDirectory.toString();
                } else {
                    File catalog = new File(catalogFile);
                    this.outputDirectory = catalog.getParent() != null ? catalog.getParent() : ".";
                }
            } else {
                throw new IllegalArgumentException("Catalog file is not specified in SYN mode config");
            }
        } else {
            throw new IllegalArgumentException("SYN mode config is not found");
        }
    }

    /**
     * Generates synthetic data from the catalog file.
     * It reads each line from the catalog and generates data based on the parsed information.
     */
    public void generateDataFromCatalog() throws TauModelException, IOException {
        java.io.File outputDirFile = new java.io.File(outputDirectory);
        if (!outputDirFile.exists()) {
            throw new IOException(
                String.format("Output directory does not exist: %s\n" +
                    "  Please create the directory before running SYN mode.",
                    outputDirFile.getAbsolutePath()));
        }
        if (!outputDirFile.isDirectory()) {
            throw new IOException(
                String.format("Output path is not a directory: %s\n" +
                    "  Please check the outDirectory path in config.json for SYN mode.",
                    outputDirFile.getAbsolutePath()));
        }
        
        List<Point> points = loadPointsFromCatalog(catalogFile);
        int total = points.size();
        logger.info(String.format("Generating synthetic data for %d events", total));
        System.out.println(String.format("Generating synthetic data for %d events...", total));
        
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            generateData(point, minSelectRate, maxSelectRate);
            printProgressBar(i + 1, total, point.getTime());
        }
        System.out.println();
        logger.info(String.format("Synthetic data generation completed: %d events", total));
        System.out.println(String.format("Synthetic data generation completed: %d events", total));
    }
    
    private void printProgressBar(int current, int total, String eventTime) {
        int percent = (int) (100.0 * current / total);
        int barWidth = 50;
        int filled = (int) (barWidth * current / total);
        
        StringBuilder bar = new StringBuilder();
        bar.append("[");
        for (int i = 0; i < barWidth; i++) {
            if (i < filled) {
                bar.append("#");
            } else {
                bar.append(" ");
            }
        }
        bar.append("]");
        
        String progressMsg = String.format("%s %d/%d (%d%%) - %s", 
            bar.toString(), current, total, percent, eventTime);
        logger.info(progressMsg);
        System.out.print("\r" + progressMsg);
    }

    /**
     * Loads points from a catalog file.
     * 
     * @param catalogFile the path to the catalog file
     * @return a list of Point objects
     * @throws IOException if there is an error reading the file
     */
    private List<Point> loadPointsFromCatalog(String catalogFile) throws IOException {
        List<Point> points = new ArrayList<>();
        File file = new File(catalogFile);
        
        List<Hypocenter> hypocenters = CatalogLoader.load(file);
        
        for (Hypocenter h : hypocenters) {
            String fileName;
            if (h.time.contains("T") && h.time.length() >= 19) {
                String year = h.time.substring(2, 4);
                String month = h.time.substring(5, 7);
                String day = h.time.substring(8, 10);
                String hour = h.time.substring(11, 13);
                String minute = h.time.substring(14, 16);
                String second = h.time.substring(17, 19);
                fileName = year + month + day + "." + hour + minute + second + ".dat";
            } else if (h.time.contains(".") && h.time.length() >= 13) {
                fileName = h.time + ".dat";
            } else {
                if (h.time.length() == 12) {
                    fileName = h.time.substring(0, 6) + "." + h.time.substring(6) + ".dat";
                } else {
                    fileName = h.time + ".dat";
                }
            }
            String fullPath = new File(outputDirectory, fileName).getPath();
            
            String pointType = (h.type != null && !h.type.isEmpty()) ? h.type : "SYN";
            Point point = new Point(
                h.time,
                h.lat,
                h.lon,
                h.depth,
                0.0,
                0.0,
                0.0,
                0.0,
                fullPath,
                pointType,
                h.clusterId != null ? h.clusterId : -1
            );
            points.add(point);
        }
        
        return points;
    }

    /**
     * Generates synthetic data for a given set of parameters.
     *
     * @param pointTrue      the true point of the event
     * @param minSelectRate   the minimum selection rate for phase pairs
     * @param maxSelectRate   the maximum selection rate for phase pairs
     * @throws TauModelException if there is an error in the Tau model
     */
    public void generateData(Point pointTrue, double minSelectRate, double maxSelectRate) throws TauModelException {
        PointsHandler pointsHandler = new PointsHandler();
        
        boolean isRef = "REF".equals(pointTrue.getType());
        boolean addPerturbation = !isRef;
        
        double[][] lagTable = randomLagTime(pointTrue, minSelectRate, maxSelectRate, addPerturbation);

        double lat = pointTrue.getLat();
        double lon = pointTrue.getLon();
        double dep = pointTrue.getDep();
        
        if (addLocationPerturbation && !isRef) {
            lat = pointTrue.getLat() + rand.nextGaussian() * locErr;
            double latRad = Math.toRadians(pointTrue.getLat());
            lon = pointTrue.getLon() + rand.nextGaussian() * locErr / Math.cos(latRad);
            dep = pointTrue.getDep() + rand.nextGaussian() * locErr * HypoUtils.getDeg2Km();
            logger.fine("Location perturbation applied: lat=" + lat + ", lon=" + lon + ", dep=" + dep);
        }
        
        Point pointPerturbed = new Point(
            pointTrue.getTime(),
            lat,
            lon,
            dep,
            0.0,
            0.0,
            0.0,
            0.0,
            pointTrue.getFilePath(),
            "SYN",
            -1
        );
        
        int[] codeIdx = IntStream.rangeClosed(0, stationTable.length - 1).toArray();
        double[] sWaveTravelTime = travelTime(stationTable, codeIdx, pointPerturbed);
        
        double[] travelTimeResidual = new double[lagTable.length];
        for (int i = 0; i < lagTable.length; i++) {
            int idxK = (int) lagTable[i][0];
            int idxL = (int) lagTable[i][1];
            double observedLag = lagTable[i][2];
            double calculatedLag = sWaveTravelTime[idxL] - sWaveTravelTime[idxK];
            travelTimeResidual[i] = observedLag - calculatedLag;
        }
        
        double meanResidual = 0.0;
        for (double r : travelTimeResidual) {
            meanResidual += r;
        }
        meanResidual /= travelTimeResidual.length;
        
        double variance = 0.0;
        for (double r : travelTimeResidual) {
            variance += (r - meanResidual) * (r - meanResidual);
        }
        double res = Math.sqrt(variance / travelTimeResidual.length);
        
        if (res <= 0.0 || res < 1e-6) {
            res = 999.0;
            logger.fine("Residual is too small (" + res + "), setting to 999.0 for GRD mode");
        }
        
        String outputType = isRef ? "REF" : "SYN";
        Point point_output = new Point(
            pointTrue.getTime(),
            lat,
            lon,
            dep,
            0.0,
            0.0,
            0.0,
            res,
            pointTrue.getFilePath(),
            outputType,
            pointTrue.getCid()
        );
        point_output.setLagTable(lagTable);
        pointsHandler.setMainPoint(point_output);
        try {
            pointsHandler.writeDatFile(pointTrue.getFilePath(), codeStrings);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to write dat file in SYN mode:\n");
            errorMsg.append("  Output file: ").append(pointTrue.getFilePath()).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
            logger.severe(errorMsg.toString());
            throw new RuntimeException("Failed to write dat file: " + pointTrue.getFilePath(), e);
        }
        
        String perturbationInfo = addLocationPerturbation ? " (lag time + location perturbation)" : " (lag time perturbation only)";
        logger.info("Generated synthetic data: " + pointTrue.getFilePath() + 
                   " (pairs: " + lagTable.length + perturbationInfo + ")");
    }

    /**
     * Generates random lag times for a given hypocenter and selection rates.
     *
     * @param point          the true hypocenter coordinates
     * @param minSelectRate  the minimum selection rate for phase pairs
     * @param maxSelectRate  the maximum selection rate for phase pairs
     * @param addPurturb     whether to add perturbation to lag times
     * @return a 2D array of selected lag times
     * @throws TauModelException if there is an error in the Tau model
     */
    private double[][] randomLagTime(Point point, double minSelectRate, double maxSelectRate, boolean addPurturb) throws TauModelException {
        int[] codeIdx = IntStream.rangeClosed(0, stationTable.length - 1).toArray();
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
        int[] indices = IntStream.rangeClosed(0, numAllPairs - 1).toArray();
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

