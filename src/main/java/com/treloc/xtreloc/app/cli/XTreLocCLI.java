package com.treloc.xtreloc.app.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.ConfigLoader;
import com.treloc.xtreloc.io.FileScanner;
import com.treloc.xtreloc.io.RunContext;
import com.treloc.xtreloc.io.RunContextFactory;
import com.treloc.xtreloc.util.LogInitializer;

/**
 * CLI entry point for xTreLoc
 *
 * @author K.Minamoto
 */
public final class XTreLocCLI {

    private static final Logger logger = Logger.getLogger(XTreLocCLI.class.getName());

    public static void main(String[] args) {

        if (args.length == 0 || "--help".equals(args[0])) {
            showLogo();
            showHelp();
            return;
        }
        
        if ("--version".equals(args[0]) || "-v".equals(args[0])) {
            showVersion();
            return;
        }

        String mode = args[0].toUpperCase();
        String configPath = (args.length >= 2)
                ? args[1]
                : "config.json";

        showLogo();

        try {
            java.io.File logFile = com.treloc.xtreloc.app.gui.util.LogHistoryManager.getLogFile();
            LogInitializer.setup(logFile.getAbsolutePath(), configPath);
            
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger("com.treloc.xtreloc");
            String version = com.treloc.xtreloc.app.gui.util.VersionInfo.getVersionString();
            logger.info("========================================");
            logger.info("xTreLoc CLI mode started");
            logger.info(version);
            logger.info("Log file: " + logFile.getAbsolutePath());
            logger.info("========================================");

            ConfigLoader loader = new ConfigLoader(configPath);
            AppConfig config = loader.getConfig();
            RunContext context = RunContextFactory.fromCLI(mode, config);
            switch (mode) {
                case "GRD":
                    runBatch(context, config,
                            (dat, out) -> {
                                try {
                                    com.treloc.xtreloc.solver.HypoGridSearch solver = 
                                        new com.treloc.xtreloc.solver.HypoGridSearch(config);
                                    solver.start(dat, out);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "Grid search failed: " + dat, e);
                                    throw new RuntimeException(e);
                                }
                            });
                    break;

                case "LMO":
                    runBatch(context, config,
                            (dat, out) -> {
                                try {
                                    com.treloc.xtreloc.solver.HypoStationPairDiff solver = 
                                        new com.treloc.xtreloc.solver.HypoStationPairDiff(config);
                                    solver.start(dat, out);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "Levenberg-Marquardt optimization failed: " + dat, e);
                                    throw new RuntimeException(e);
                                }
                            });
                    break;

                case "MCMC":
                    runBatch(context, config,
                            (dat, out) -> {
                                try {
                                    com.treloc.xtreloc.solver.HypoMCMC solver = 
                                        new com.treloc.xtreloc.solver.HypoMCMC(config);
                                    solver.start(dat, out);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "MCMC location failed: " + dat, e);
                                    throw new RuntimeException(e);
                                }
                            });
                    break;

                case "TRD":
                    runTripleDiff(config);
                    break;

                case "CLS":
                    runClustering(config);
                    break;

                case "SYN":
                    runSyntheticTest(config);
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Unknown mode: " + mode);
            }

        } catch (Exception e) {
            // Detailed error reporting for CLI
            System.err.println("\n========================================");
            System.err.println("ERROR: " + e.getMessage());
            System.err.println("========================================");
            System.err.println("Error type: " + e.getClass().getName());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            System.err.println("\nStack trace:");
            e.printStackTrace(System.err);
            System.err.println("\nFor detailed error information, check the log file:");
            try {
                java.io.File logFile = com.treloc.xtreloc.app.gui.util.LogHistoryManager.getLogFile();
                System.err.println("  " + logFile.getAbsolutePath());
            } catch (Exception logEx) {
            }
            System.err.println("========================================");
            
            // Log to file with full stack trace
            logger.log(Level.SEVERE, "Fatal error", e);
            System.exit(1);
        }
    }

    private static void runTripleDiff(AppConfig config) {
        try {
            com.treloc.xtreloc.solver.HypoTripleDiff solver = 
                new com.treloc.xtreloc.solver.HypoTripleDiff(config);
            solver.start("", "");
            logger.info("Triple difference relocation completed");
            System.out.println("Triple difference relocation completed successfully.");
        } catch (Exception e) {
            System.err.println("\nERROR: Triple difference relocation failed");
            System.err.println("Error: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
            }
            logger.log(Level.SEVERE, "Triple difference relocation failed", e);
            throw new RuntimeException("Triple difference relocation failed", e);
        }
    }

    private static void runClustering(AppConfig config) {
        try {
            com.treloc.xtreloc.solver.SpatialClustering clustering = 
                new com.treloc.xtreloc.solver.SpatialClustering(config);
            clustering.start("", "");
            logger.info("Spatial clustering completed");
            System.out.println("Spatial clustering completed successfully.");
            
            java.util.List<Double> kDistances = clustering.getKDistances();
            if (kDistances != null && !kDistances.isEmpty()) {
                double estimatedEps = clustering.getEstimatedEps();
                logger.info("Estimated epsilon: " + estimatedEps + " km");
            }
        } catch (Exception e) {
            String errorReport = buildErrorReport("Clustering failed", e);
            logger.severe(errorReport);
            System.err.println("\n" + errorReport);
            throw new RuntimeException("Clustering failed", e);
        }
    }

    private static void runSyntheticTest(AppConfig config) {
        try {
            logger.info("Synthetic test started");
            
            int randomSeed = 100;
            double phsErr = 0.1;
            double locErr = 0.03;
            double minSelectRate = 0.2;
            double maxSelectRate = 0.4;
            boolean addLocationPerturbation = true;
            
            AppConfig.ModeConfig synConfig = null;
            if (config.modes != null && config.modes.containsKey("SYN")) {
                synConfig = config.modes.get("SYN");
                if (synConfig != null) {
                    if (synConfig.randomSeed != null) randomSeed = synConfig.randomSeed;
                    if (synConfig.phsErr != null) phsErr = synConfig.phsErr;
                    if (synConfig.locErr != null) locErr = synConfig.locErr;
                    if (synConfig.minSelectRate != null) minSelectRate = synConfig.minSelectRate;
                    if (synConfig.maxSelectRate != null) maxSelectRate = synConfig.maxSelectRate;
                    
                    if (synConfig.outDirectory != null) {
                        java.nio.file.Path outputDir = synConfig.outDirectory;
                        if (!java.nio.file.Files.exists(outputDir)) {
                            throw new IllegalArgumentException(
                                String.format("Output directory does not exist: %s\n" +
                                    "  Please create the directory or check the outDirectory path in config.json for SYN mode",
                                    outputDir));
                        }
                        if (!java.nio.file.Files.isDirectory(outputDir)) {
                            throw new IllegalArgumentException(
                                String.format("Output path is not a directory: %s\n" +
                                    "  Please check the outDirectory path in config.json for SYN mode",
                                    outputDir));
                        }
                    } else {
                        throw new IllegalArgumentException(
                            "Output directory is not set for SYN mode. Please specify outDirectory in config.json");
                    }
                }
            }
            
            logger.info(String.format(
                "SYN parameters: seed=%d, phsErr=%.3f, locErr=%.3f, selectRate=%.2f-%.2f",
                randomSeed, phsErr, locErr, minSelectRate, maxSelectRate));
            
            com.treloc.xtreloc.solver.SyntheticTest syntheticTest = 
                new com.treloc.xtreloc.solver.SyntheticTest(config, randomSeed, phsErr, locErr,
                    minSelectRate, maxSelectRate, addLocationPerturbation);
            
            syntheticTest.generateDataFromCatalog();
            logger.info("Synthetic test completed");
            
            if (synConfig != null && synConfig.outDirectory != null) {
                try {
                    generateCatalogFromDatFiles(synConfig.outDirectory, "SYN");
                } catch (Exception e) {
                    logger.warning("Failed to auto-generate catalog for SYN mode: " + e.getMessage());
                }
            }
            
            System.out.println("Synthetic test completed successfully.");
        } catch (Exception e) {
            String errorReport = buildErrorReport("Synthetic test failed", e);
            logger.severe(errorReport);
            System.err.println("\n" + errorReport);
            throw new RuntimeException("Synthetic test failed", e);
        }
    }

    private static void runBatch(
            RunContext context,
            AppConfig config,
            LocationTask task) {

        Path[] datFiles = context.getDatFiles();
        int numJobs = Math.max(config.numJobs, 1);
        AtomicInteger progress = new AtomicInteger(0);

        ExecutorService executor = (numJobs > 1)
                ? Executors.newFixedThreadPool(numJobs)
                : null;

        logger.info(String.format("Start %s (%d files, jobs=%d)", context.getMode(), datFiles.length, numJobs));

        if (datFiles.length == 0) {
            String errorMsg = String.format(
                "No .dat files found in the input directory for mode %s.\n" +
                "  Please check that:\n" +
                "  1. The datDirectory in config.json is correct\n" +
                "  2. The directory contains .dat files\n" +
                "  3. The directory path is accessible",
                context.getMode());
            logger.warning(errorMsg);
            System.err.println("WARNING: " + errorMsg);
            return;
        }

        Path outDir = context.getOutDir();
        if (outDir == null) {
            throw new IllegalStateException(
                String.format("Output directory is not set for mode %s. Please specify outDirectory in config.json", 
                    context.getMode()));
        }
        
        if (!java.nio.file.Files.exists(outDir)) {
            throw new IllegalStateException(
                String.format("Output directory does not exist: %s\n" +
                    "  Please create the directory or check the outDirectory path in config.json for mode %s",
                    outDir, context.getMode()));
        }
        if (!java.nio.file.Files.isDirectory(outDir)) {
            throw new IllegalStateException(
                String.format("Output path is not a directory: %s\n" +
                    "  Please check the outDirectory path in config.json for mode %s",
                    outDir, context.getMode()));
        }

        try {
            for (Path datPath : datFiles) {

                Runnable job = () -> {
                    String dat = datPath.toString();
                    String out = outDir
                            .resolve(datPath.getFileName())
                            .toString();

                    try {
                        logger.info(String.format("Processing: %s", datPath.getFileName()));
                        System.out.println(String.format("Processing: %s", datPath.getFileName()));
                        task.run(dat, out);
                        logger.info("Successfully processed: " + dat);
                    } catch (Exception e) {
                        String errorReport = buildErrorReport("Failed to process file", e, 
                            "Mode: " + context.getMode() + "\n" +
                            "Input file: " + dat + "\n" +
                            "Output file: " + out + "\n");
                        logger.severe(errorReport);
                        System.err.println("\n" + errorReport);
                    } finally {
                        int done = progress.incrementAndGet();
                        printProgressBar(done, datFiles.length, datPath.getFileName().toString());
                    }
                };

                if (executor != null) {
                    executor.submit(job);
                } else {
                    job.run();
                }
            }
            
            if (executor != null) {
                executor.shutdown();
                try {
                    executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warning("Interrupted while waiting for batch processing to complete");
                }
            }

            logger.info(String.format("%s completed: %d files processed", context.getMode(), datFiles.length));
            printProgressBar(datFiles.length, datFiles.length, "All files");
            
            try {
                generateCatalogFromDatFiles(outDir, context.getMode());
            } catch (Exception e) {
                logger.warning("Failed to auto-generate catalog: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Batch processing failed", e);
            throw new RuntimeException("Batch processing failed", e);
        }
    }
    
    private static void generateCatalogFromDatFiles(Path outDir, String mode) throws IOException {
        Path[] outputDatFiles = FileScanner.scan(outDir);
        if (outputDatFiles.length == 0) {
            logger.warning("No .dat files found in output directory for catalog generation: " + outDir);
            return;
        }
        
        logger.info("Generating catalog.csv from " + outputDatFiles.length + " .dat files...");
        
        List<com.treloc.xtreloc.app.gui.model.Hypocenter> allHypocenters = new java.util.ArrayList<>();
        for (Path datFile : outputDatFiles) {
            try {
                allHypocenters.addAll(loadHypocentersFromDatFile(datFile, outDir));
            } catch (Exception e) {
                logger.warning("Failed to load hypocenter from " + datFile.getFileName() + ": " + e.getMessage());
            }
        }
        
        if (allHypocenters.isEmpty()) {
            logger.warning("No hypocenter data loaded from .dat files");
            return;
        }
        
        java.io.File catalogFile = com.treloc.xtreloc.util.CatalogFileNameGenerator.generateCatalogFileName(
            null, mode, outDir.toFile());
        try (java.io.FileWriter writer = new java.io.FileWriter(catalogFile)) {
            writer.write("time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid\n");
            
            for (com.treloc.xtreloc.app.gui.model.Hypocenter h : allHypocenters) {
                String type = h.type;
                if (mode.equals("SYN") && (type == null || type.isEmpty())) {
                    type = "SYN";
                } else if (type == null || type.isEmpty()) {
                    type = mode;
                }
                
                String timeISO8601 = convertTimeToISO8601(h.time);
                
                writer.write(String.format("%s,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%s,%s\n",
                    timeISO8601, h.lat, h.lon, h.depth, h.xerr, h.yerr, h.zerr, h.rms,
                    h.datFilePath != null ? h.datFilePath : "",
                    type,
                    h.clusterId != null ? String.valueOf(h.clusterId) : ""));
            }
        }
        
        logger.info("Catalog auto-generated: " + catalogFile.getAbsolutePath() + " (" + allHypocenters.size() + " entries)");
        System.out.println("Catalog auto-generated: " + catalogFile.getAbsolutePath() + " (" + allHypocenters.size() + " entries)");
    }
    
    private static List<com.treloc.xtreloc.app.gui.model.Hypocenter> loadHypocentersFromDatFile(
            Path datFile, Path catalogBaseDir) throws IOException {
        
        List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = new java.util.ArrayList<>();
        
        try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(datFile)) {
            String line1 = br.readLine();
            if (line1 == null) {
                return hypocenters;
            }
            
            String[] parts1 = line1.trim().split("\\s+");
            if (parts1.length < 3) {
                return hypocenters;
            }
            
            double lat = Double.parseDouble(parts1[0]);
            double lon = Double.parseDouble(parts1[1]);
            double depth = Double.parseDouble(parts1[2]);
            String type = parts1.length > 3 ? parts1[3] : null;
            
            String line2 = br.readLine();
            double xerr = 0.0, yerr = 0.0, zerr = 0.0, rms = 0.0;
            
            if (line2 != null && !line2.trim().isEmpty()) {
                String[] parts2 = line2.trim().split("\\s+");
                try {
                    Double.parseDouble(parts2[0]);
                    if (parts2.length >= 4) {
                        xerr = Double.parseDouble(parts2[0]);
                        yerr = Double.parseDouble(parts2[1]);
                        zerr = Double.parseDouble(parts2[2]);
                        rms = Double.parseDouble(parts2[3]);
                    }
                } catch (NumberFormatException e) {
                }
            }
            
            String datFilePath = datFile.getFileName().toString();
            try {
                Path relativePath = catalogBaseDir.relativize(datFile);
                datFilePath = relativePath.toString().replace(java.io.File.separator, "/");
            } catch (Exception e) {
                datFilePath = datFile.getFileName().toString();
            }
            
            String time = extractTimeFromFilename(datFile.getFileName().toString());
            hypocenters.add(new com.treloc.xtreloc.app.gui.model.Hypocenter(
                time, lat, lon, depth, xerr, yerr, zerr, rms, null, datFilePath, type));
        }
        
        return hypocenters;
    }
    
    private static String extractTimeFromFilename(String filename) {
        String baseName = filename.endsWith(".dat") ? filename.substring(0, filename.length() - 4) : filename;
        
        if (baseName.contains(".") && baseName.length() >= 13) {
            String[] parts = baseName.split("\\.");
            if (parts.length == 2 && parts[0].length() == 6 && parts[1].length() == 6) {
                if (parts[0].matches("\\d{6}") && parts[1].matches("\\d{6}")) {
                    return baseName;
                }
            }
        }
        
        if (baseName.matches("\\d{14}")) {
            return baseName.substring(0, 6) + "." + baseName.substring(6, 14);
        }
        String timeWithoutDot = baseName.replace(".", "");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{14})");
        java.util.regex.Matcher matcher = pattern.matcher(timeWithoutDot);
        if (matcher.find()) {
            String time14 = matcher.group(1);
            return time14.substring(0, 6) + "." + time14.substring(6, 14);
        }
        
        return "";
    }
    
    /**
     * Converts time format from yymmdd.hhmmss to ISO 8601 format (YYYY-MM-DDTHH:MM:SS).
     * If the time is already in ISO 8601 format, returns it as is.
     * 
     * @param timeStr time string in yymmdd.hhmmss or ISO 8601 format
     * @return time string in ISO 8601 format
     */
    private static String convertTimeToISO8601(String timeStr) {
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

    private static void printProgressBar(int current, int total, String fileName) {
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
            bar.toString(), current, total, percent, fileName);
        logger.info(progressMsg);
        System.out.print("\r" + progressMsg);
        if (current == total) {
            System.out.println();
        }
    }
    
    private static String buildErrorReport(String title, Exception e) {
        return buildErrorReport(title, e, "");
    }
    
    private static String buildErrorReport(String title, Exception e, String additionalInfo) {
        StringBuilder errorReport = new StringBuilder();
        errorReport.append("========================================\n");
        errorReport.append("ERROR: ").append(title).append("\n");
        errorReport.append("========================================\n");
        if (!additionalInfo.isEmpty()) {
            errorReport.append(additionalInfo);
        }
        errorReport.append("Error type: ").append(e.getClass().getName()).append("\n");
        errorReport.append("Error message: ").append(e.getMessage()).append("\n");
        if (e.getCause() != null) {
            errorReport.append("Caused by: ").append(e.getCause().getClass().getName())
                       .append(": ").append(e.getCause().getMessage()).append("\n");
        }
        errorReport.append("\nStack trace:\n");
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        errorReport.append(sw.toString());
        errorReport.append("========================================\n");
        return errorReport.toString();
    }

    private static void showHelp() {
        System.out.println();
        System.out.println(
                "Usage: java -jar xtreloc.jar <MODE> [config.json]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --help, -h    Show this help message");
        System.out.println("  --version, -v Show version information");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  GRD   Grid search location");
        System.out.println("  LMO   Levenberg-Marquardt optimization");
        System.out.println("  MCMC  Markov Chain Monte Carlo location");
        System.out.println("  TRD   Triple difference relocation");
        System.out.println("  CLS   Spatial clustering");
        System.out.println("  SYN   Synthetic test data generation");
    }

    private static void showVersion() {
        String version = com.treloc.xtreloc.app.gui.util.VersionInfo.getVersionString();
        System.out.println(version);
    }

    private static void showLogo() {
        System.out.println("");
        System.out.println("          ______                __");
        System.out.println("   _  __ /_  __/ _____  ___    / /   ____   _____");
        System.out.println("  | |/_/  / /   / ___/ / _ \\  / /   / __ \\ / ___/");
        System.out.println(" _>  <   / /   / /    /  __/ / /___/ /_/ // /__");
        System.out.println("/_/|_|  /_/   /_/     \\___/ /_____/\\____/ \\___/");
        System.out.println("");
        String version = com.treloc.xtreloc.app.gui.util.VersionInfo.getVersionString();
        System.out.println("  " + version);
        System.out.println("");
    }

    @FunctionalInterface
    private interface LocationTask {
        void run(String datFile, String outFile) throws Exception;
    }
}
