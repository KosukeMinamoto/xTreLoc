package com.treloc.hypotd;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/* 
 * The main app of the xTreLoc, hypocenter re-location program
 * 
 * @author: K.Minamoto
 * @date: 2025/02/11
 * @version: 0.1
 * @description: The class is used to calculate the hypocenter location 
 * @usage: 
 */

public class App {
	public static double deg2km = 111.32;
	public static double deg2rad = Math.PI / 180;
	public static void main(String[] args) {
		if (args.length == 0 || "--help".equals(args[0])) {
			showLogo();
			showHelp();
			return;
		}
		try {
			String configFilePath = "config.json";
			ConfigLoader config = new ConfigLoader(configFilePath);

			Object runner;
			switch (args[0]) {
				case "GRS":
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
				case "SEE":
					new HypoViewer(config);
					break;
				case "CLS":
					new SpatialClustering(config);
					break;
				case "TRD":
					HypoTripleDiff hypoTripleDiff = new HypoTripleDiff(config);
					hypoTripleDiff.start();
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
		System.out.println("  GRS    - Location by grid search");
		System.out.println("  STD    - Location by Station-pair DD");
		System.out.println("  CLS    - Spatial clustering & create triple-diff");
		System.out.println("  TRD    - Re-location by Triple Difference");
		System.out.println("  SYN    - Create dat files for synthetic test");
		System.out.println("  SEE    - View location results");
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
		Path[] filePaths = config.getDatPaths();
		AtomicInteger progress = new AtomicInteger(0);
		int numTasks = filePaths.length;
		try {
			int numJobs = config.getNumJobs();
			if (numJobs == 1) {
				for (Path filePath : filePaths) {
					String datFile = filePath.toString();
					String outFile = "./dat-out/" + filePath.getFileName();
					((HypoStationPairDiff) locator).start(datFile, outFile);
				}
			} else {
				executor = Executors.newFixedThreadPool(numJobs);
				for (Path filePath : filePaths) {
					executor.submit(() -> {
						// Path parentDir = filePath.getParent();
						String datFile = filePath.toString();
						String outFile = "./dat-out/" + filePath.getFileName();
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
							System.out.printf("%s (%d/%d)%n", filePath, completedTasks, numTasks);
						}
					});
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (executor != null) {
				executor.shutdown();
			}
		}
	}


}
