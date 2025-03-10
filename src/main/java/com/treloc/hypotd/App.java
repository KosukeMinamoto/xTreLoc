package com.treloc.hypotd;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.SimpleFormatter;
import java.util.logging.LogRecord;
import java.util.Calendar;

/**
 * The main app of the xTreLoc, hypocenter re-location program
 * 
 * @author K.Minamoto
 * @since 2025-02-22
 * @version 0.1
 */

public class App {
	private static final Logger logger = Logger.getLogger("com.treloc.hypotd");
	public static double deg2km = 111.32;
	public static double deg2rad = Math.PI / 180;
	private static String mode;

	public static void main(String[] args) {
		if (args.length == 0 || "--help".equals(args[0])) {
			showLogo();
			showHelp();
			return;
		}
		
		LogHandler logHandler = new LogHandler();
		logHandler.setupLogger("treloc.log");

		try {
			String configFilePath = "config.json";
			ConfigLoader config = new ConfigLoader(configFilePath);
			mode = args[0];
			config.setMode(mode);

			Object runner;

			switch (mode) {
				case "GRD":
					runner = new HypoGridSearch(config);
					runLocation(config, runner);
					break;
				case "STD":
					runner = new HypoStationPairDiff(config);
					runLocation(config, runner);
					break;
				case "SYN":
					SyntheticTest tester = new SyntheticTest(config);
					tester.generateDataFromCatalog();
					break;
				case "CLS":
					new SpatialClustering(config);
					break;
				case "TRD":
					HypoTripleDiff hypoTripleDiff = new HypoTripleDiff(config);
					hypoTripleDiff.start();
					break;
				case "MAP":
					new EarthquakeMapView(config);
					break;
				default:
					showHelp();
					throw new IllegalArgumentException("Invalid argument: " + args[0]);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void showHelp() {
		System.out.println("");
		System.out.println("Usage: java -jar path/to/target/xtreloc-1.0-SNAPSHOT-jar-with-dependencies.jar <mode>");
		System.out.println("");
		System.out.println("Modes:");
		System.out.println("  GRD    - Location by grid search");
		System.out.println("  STD    - Location by Station-pair DD");
		System.out.println("  CLS    - Spatial clustering & create triple-diff");
		System.out.println("  TRD    - Re-location by Triple Difference");
		System.out.println("  SYN    - Create dat files for synthetic test");
		System.out.println("  MAP    - View location results on map");
	}

	private static void showLogo() {
		System.out.println("");
		System.out.println("          ______                __");
		System.out.println("   _  __ /_  __/ _____  ___    / /   ____   _____");
		System.out.println("  | |/_/  / /   / ___/ / _ \\  / /   / __ \\ / ___/");
		System.out.println(" _>  <   / /   / /    /  __/ / /___/ /_/ // /__");
		System.out.println("/_/|_|  /_/   /_/     \\___/ /_____/\\____/ \\___/");
		System.out.println("");
	}

	public static void runLocation (ConfigLoader config, Object locator) {
		ExecutorService executor = null;
		Path[] filePaths = config.getDatPaths(mode);
		AtomicInteger progress = new AtomicInteger(0);
		int numTasks = filePaths.length;
		try {
			int numJobs = config.getNumJobs();
			if (numJobs == 1) {
				for (Path filePath : filePaths) {
					String datFile = filePath.toString();
					String outFile = config.getOutDir().resolve(filePath.getFileName()).toString();
					((HypoStationPairDiff) locator).start(datFile, outFile);
				}
			} else {
				executor = Executors.newFixedThreadPool(numJobs);
				for (Path filePath : filePaths) {
					executor.submit(() -> {
						String datFile = filePath.toString();
						String outFile = config.getOutDir().resolve(filePath.getFileName()).toString();
						try {
							if (locator instanceof HypoGridSearch) {
								((HypoGridSearch) locator).start(datFile, outFile);
							} else if (locator instanceof HypoStationPairDiff) {
								((HypoStationPairDiff) locator).start(datFile, outFile);
							}
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							int completedTasks = progress.incrementAndGet();
							logger.info(String.format("%s (%d/%d) completed", filePath, completedTasks, numTasks));
						}
					});
				}
			}
			logger.info("Starting location process with " + numJobs + " jobs.");
		} catch (Exception e) {
			logger.severe("Error: Location process failed: " + e.getMessage());
		} finally {
			if (executor != null) {
				logger.info("Shutting down executor service...");
				executor.shutdown();
			}
		}
	}
}


final class LogHandler extends Formatter {

	private Calendar calendar = Calendar.getInstance();
	private FileHandler fileHandler;
	private Logger logger = Logger.getLogger("com.treloc.hypotd");

	public void setupLogger(String logFile) {
		try {
			ConfigLoader config = new ConfigLoader("config.json");
			String logLevel = config.getLogLevel();
			Level level = Level.parse(logLevel.toUpperCase());

			fileHandler = new FileHandler(logFile, true);
			fileHandler.setFormatter(new SimpleFormatter());
			logger.addHandler(fileHandler);
			logger.setLevel(level);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized String format(LogRecord record) {
		StringBuilder message = new StringBuilder(100);
		calendar.setTimeInMillis(record.getMillis());

		message.append(
				String.format(
						"%1$tD %1$tT %2$s ",
						calendar,
						record.getLevel().toString()));

		if (null != record.getSourceClassName()) {
			message.append(
					record.getSourceClassName());
		} else {
			message.append(
					record.getLoggerName());
		}
		message.append(' ');

		if (null != record.getSourceMethodName()) {
			message.append(
					String.format(
							"[%s]",
							record.getSourceMethodName()));
		}
		message.append(' ');

		message.append(formatMessage(record));
		message.append('\n');

		if (null != record.getThrown()) {
			message.append(
					record.getThrown());
		}
		return message.toString();
	}
}