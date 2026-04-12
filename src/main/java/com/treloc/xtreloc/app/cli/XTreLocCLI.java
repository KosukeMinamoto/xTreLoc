package com.treloc.xtreloc.app.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.app.util.AsciiLogo;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.ConfigLoader;
import com.treloc.xtreloc.io.StationRepository;
import com.treloc.xtreloc.io.FileScanner;
import com.treloc.xtreloc.io.RunContext;
import com.treloc.xtreloc.io.RunContextFactory;
import com.treloc.xtreloc.util.BatchExecutorFactory;
import com.treloc.xtreloc.util.TimeFormatConverter;

/**
 * CLI entry point for xTreLoc
 *
 * @author K.Minamoto
 */
public final class XTreLocCLI {

    private static final Logger logger = Logger.getLogger(XTreLocCLI.class.getName());

    public static void main(String[] args) {

        if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            showLogo();
            showHelp();
            return;
        }

        if (args.length > 0 && ("--version".equals(args[0]) || "-v".equals(args[0]))) {
            showVersion();
            return;
        }

        if (args.length == 0) {
            showLogo();
            if (!isStdinLikelyInteractive()) {
                System.err.println("No arguments and no interactive terminal: showing help.");
                System.err.println("Run with a mode and config, e.g.: GRD config.json");
                System.err.println("Or use: --help");
                showHelp();
                return;
            }
            Scanner scanner = new Scanner(System.in);
            runInteractiveLoop(scanner, "Goodbye.");
            return;
        }

        String mode = args[0].toUpperCase();
        String configPath = (args.length >= 2)
                ? args[1]
                : "config.json";

        showLogo();

        try {
            runWithoutLogo(mode, configPath);
        } catch (Exception e) {
            // Detailed error reporting for CLI
            System.err.println("\n========================================");
            System.err.println("ERROR: " + e.getMessage());
            System.err.println("========================================");
            System.err.println("Error type: " + e.getClass().getName());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            System.err.println("\nFor detailed error information (including stack trace), check the log file:");
            try {
                java.io.File logFile = com.treloc.xtreloc.util.AppLogFile.getLogFile();
                System.err.println("  " + logFile.getAbsolutePath());
            } catch (Exception logEx) {
            }
            System.err.println("========================================");
            
            logger.log(Level.SEVERE, "Fatal error (CLI)", e);
            System.exit(1);
        }
    }

    /**
     * Runs CLI with the given mode and config path without showing logo.
     * Used when invoking CLI from TUI. Throws on error (does not call System.exit).
     */
    public static void runWithoutLogo(String mode, String configPath) throws Exception {
        if (mode == null || mode.isEmpty()) {
            throw new IllegalArgumentException("Mode is required (GRD, LMO, MCMC, DE, TRD, CLS, SYN).");
        }
        mode = mode.trim().toUpperCase();
        if (configPath == null) configPath = "config.json";
        configPath = configPath.trim();

        java.io.File configFile = new java.io.File(configPath);
        if (!configFile.exists()) {
            throw new IllegalArgumentException("Configuration file not found: " + configPath);
        }

        ConfigLoader loader = new ConfigLoader(configPath);
        AppConfig config = loader.getConfig();
        RunContext context = RunContextFactory.fromCLI(mode, config);
        switch (mode) {
                case "GRD":
                    runBatch(context, config,
                            (dat, out, preLoaded) -> {
                                try {
                                    com.treloc.xtreloc.solver.HypoGridSearch solver = preLoaded != null
                                        ? new com.treloc.xtreloc.solver.HypoGridSearch(config, preLoaded)
                                        : new com.treloc.xtreloc.solver.HypoGridSearch(config);
                                    solver.start(dat, out);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "Grid search failed: " + dat, e);
                                    throw new RuntimeException(e);
                                }
                            });
                    break;

                case "LMO":
                    runBatch(context, config,
                            (dat, out, preLoaded) -> {
                                try {
                                    com.treloc.xtreloc.solver.HypoStationPairDiff solver = preLoaded != null
                                        ? new com.treloc.xtreloc.solver.HypoStationPairDiff(config, preLoaded)
                                        : new com.treloc.xtreloc.solver.HypoStationPairDiff(config);
                                    solver.start(dat, out);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "Levenberg-Marquardt optimization failed: " + dat, e);
                                    throw new RuntimeException(e);
                                }
                            });
                    break;

                case "MCMC":
                    runBatch(context, config,
                            (dat, out, preLoaded) -> {
                                try {
                                    com.treloc.xtreloc.solver.HypoMCMC solver = preLoaded != null
                                        ? new com.treloc.xtreloc.solver.HypoMCMC(config, preLoaded)
                                        : new com.treloc.xtreloc.solver.HypoMCMC(config);
                                    solver.start(dat, out);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "MCMC location failed: " + dat, e);
                                    throw new RuntimeException(e);
                                }
                            });
                    break;

                case "DE":
                    runBatch(context, config,
                            (dat, out, preLoaded) -> {
                                try {
                                    com.treloc.xtreloc.solver.HypoDifferentialEvolution solver = preLoaded != null
                                        ? new com.treloc.xtreloc.solver.HypoDifferentialEvolution(config, preLoaded)
                                        : new com.treloc.xtreloc.solver.HypoDifferentialEvolution(config);
                                    solver.start(dat, out);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "Differential Evolution location failed: " + dat, e);
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
            System.err.println("\n" + errorReport);
            throw new RuntimeException("Clustering failed", e);
        }
    }

    private static void runSyntheticTest(AppConfig config) {
        try {
            logger.info("Synthetic test started");
            
            int randomSeed = AppConfig.DEFAULT_SYN_RANDOM_SEED;
            double phsErr = AppConfig.DEFAULT_SYN_PHS_ERR;
            double locErr = AppConfig.DEFAULT_SYN_LOC_ERR;
            double minSelectRate = AppConfig.DEFAULT_SYN_MIN_SELECT_RATE;
            double maxSelectRate = AppConfig.DEFAULT_SYN_MAX_SELECT_RATE;
            boolean addLocationPerturbation = true;
            
            AppConfig.ModeConfig synConfig = null;
            if (config.getModes() != null && config.getModes().containsKey("SYN")) {
                synConfig = config.getModes().get("SYN");
                if (synConfig != null) {
                    if (synConfig.randomSeed != null) randomSeed = synConfig.randomSeed;
                    if (synConfig.phsErr != null) phsErr = synConfig.phsErr;
                    if (synConfig.locErr != null) locErr = synConfig.locErr;
                    if (synConfig.minSelectRate != null) minSelectRate = synConfig.minSelectRate;
                    if (synConfig.maxSelectRate != null) maxSelectRate = synConfig.maxSelectRate;
                    
                    if (synConfig.outDirectory != null) {
                        java.nio.file.Path outputDir = synConfig.outDirectory;
                        if (!java.nio.file.Files.exists(outputDir)) {
                            try {
                                java.nio.file.Files.createDirectories(outputDir);
                            } catch (Exception e) {
                                throw new IllegalArgumentException(
                                    String.format("Failed to create output directory: %s - %s", outputDir, e.getMessage()));
                            }
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
        AtomicInteger failureCount = new AtomicInteger(0);

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
            System.err.println("ERROR: " + errorMsg);
            throw new IllegalStateException("No .dat files to process for mode " + context.getMode());
        }

        ExecutorService executor = (numJobs > 1)
                ? BatchExecutorFactory.newFixedThreadPoolBounded(
                        numJobs, BatchExecutorFactory.suggestedQueueCapacity(datFiles.length))
                : null;

        Path outDir = context.getOutDir();
        if (outDir == null) {
            throw new IllegalStateException(
                String.format("Output directory is not set for mode %s. Please specify outDirectory in config.json", 
                    context.getMode()));
        }
        
        if (!java.nio.file.Files.exists(outDir)) {
            try {
                java.nio.file.Files.createDirectories(outDir);
            } catch (Exception e) {
                throw new IllegalStateException(
                    String.format("Failed to create output directory: %s\n  %s", outDir, e.getMessage()));
            }
        }
        if (!java.nio.file.Files.isDirectory(outDir)) {
            throw new IllegalStateException(
                String.format("Output path is not a directory: %s\n" +
                    "  Please check the outDirectory path in config.json for mode %s",
                    outDir, context.getMode()));
        }

        StationRepository preLoadedStations = null;
        if (executor != null && config.stationFile != null) {
            try {
                preLoadedStations = StationRepository.load(Path.of(config.stationFile));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load station file for parallel run: " + config.stationFile, e);
            }
        }

        try {
            final StationRepository sharedStations = preLoadedStations;
            for (Path datPath : datFiles) {

                Runnable job = () -> {
                    String dat = datPath.toString();
                    String out = outDir
                            .resolve(datPath.getFileName())
                            .toString();

                    try {
                        logger.info(String.format("Processing: %s", datPath.getFileName()));
                        System.out.println(String.format("Processing: %s", datPath.getFileName()));
                        task.run(dat, out, sharedStations);
                        logger.info("Successfully processed: " + dat);
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        String errorReport = buildErrorReport("Failed to process file", e, 
                            "Mode: " + context.getMode() + "\n" +
                            "Input file: " + dat + "\n" +
                            "Output file: " + out + "\n");
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

            int failures = failureCount.get();
            if (failures > 0) {
                String summary = String.format(
                    "%s batch finished with %d failed file(s) out of %d.",
                    context.getMode(), failures, datFiles.length);
                logger.severe(summary);
                System.err.println("\nERROR: " + summary);
                throw new RuntimeException(summary);
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
                
                String timeISO8601 = TimeFormatConverter.toISO8601(h.time);
                
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
        String logTitle = title + (additionalInfo.isEmpty() ? "" : "\n" + additionalInfo);
        logger.log(Level.SEVERE, logTitle, e);

        StringBuilder errorReport = new StringBuilder();
        errorReport.append("========================================\n");
        errorReport.append("ERROR: ").append(title).append("\n");
        errorReport.append("========================================\n");
        if (!additionalInfo.isEmpty()) {
            errorReport.append(additionalInfo);
        }
        errorReport.append("Error type: ").append(e.getClass().getName()).append("\n");
        errorReport.append("Error message: ").append(String.valueOf(e.getMessage())).append("\n");
        if (e.getCause() != null) {
            errorReport.append("Caused by: ").append(e.getCause().getClass().getName())
                       .append(": ").append(e.getCause().getMessage()).append("\n");
        }
        errorReport.append("\nFull stack trace is in the application log file.\n");
        errorReport.append("========================================\n");
        return errorReport.toString();
    }

    /**
     * Interactive CLI: prompts for mode and config in a loop. Typing {@code q} at a prompt exits the loop.
     *
     * @param quitMessage line printed when user types {@code q} (e.g. {@code "Goodbye."} or menu return text)
     */
    public static void runInteractiveLoop(Scanner scanner, String quitMessage) {
        System.out.println("--- CLI (interactive) ---");
        System.out.println("Enter solver mode and config path. At any prompt, type q + Enter to quit.");
        System.out.println("For batch usage without prompts: xtreloc <MODE> [config.json]");
        while (true) {
            System.out.println();
            System.out.print("Solver mode (GRD/LMO/MCMC/DE/TRD/CLS/SYN) [GRD]: ");
            String modeLine = readLine(scanner);
            if ("q".equalsIgnoreCase(modeLine)) {
                System.out.println(quitMessage);
                return;
            }
            String mode = modeLine.isEmpty() ? "GRD" : modeLine.toUpperCase();
            if (!isKnownSolverMode(mode)) {
                System.err.println("Unknown mode: " + mode + ". Use GRD, LMO, MCMC, DE, TRD, CLS, or SYN.");
                continue;
            }

            System.out.print("Config path [config.json]: ");
            String configLine = readLine(scanner);
            if ("q".equalsIgnoreCase(configLine)) {
                System.out.println(quitMessage);
                return;
            }
            String configPath = configLine.isEmpty() ? "config.json" : configLine;

            System.out.println();
            System.out.println("Running: mode=" + mode + ", config=" + configPath);
            try {
                runWithoutLogo(mode, configPath);
                System.out.println("\nCompleted successfully.");
            } catch (Exception e) {
                System.err.println("\nFailed: " + e.getMessage());
                logger.log(Level.WARNING, "Interactive CLI run failed", e);
            }
        }
    }

    private static String readLine(Scanner scanner) {
        if (!scanner.hasNextLine()) {
            return "q";
        }
        String line = scanner.nextLine().trim();
        if (line.isEmpty() && scanner.hasNext()) {
            line = scanner.next().trim();
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }
        }
        return line;
    }

    private static boolean isKnownSolverMode(String mode) {
        if (mode == null || mode.isEmpty()) {
            return false;
        }
        switch (mode.toUpperCase()) {
            case "GRD":
            case "LMO":
            case "MCMC":
            case "DE":
            case "TRD":
            case "CLS":
            case "SYN":
                return true;
            default:
                return false;
        }
    }

    private static boolean isStdinLikelyInteractive() {
        try {
            if (System.console() != null) {
                return true;
            }
            return System.in.available() >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void showHelp() {
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  xtreloc                          Interactive CLI (prompts for mode and config)");
        System.out.println("  xtreloc <MODE> [config.json]     Batch: run once and exit");
        System.out.println("  java -jar xTreLoc.jar ...        Same as above");
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
        String version = com.treloc.xtreloc.util.VersionInfo.getVersionString();
        System.out.println(version);
    }

    private static void showLogo() {
        AsciiLogo.print(System.out);
    }

    @FunctionalInterface
    private interface LocationTask {
        void run(String datFile, String outFile, StationRepository preLoadedStations) throws Exception;
    }
}
