package com.treloc.hypotd;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
// import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/* 
 * Hypocenter location program
 * Cross-correlation based tremor re-location tool
 * 
 * @author: K.M.
 * @date: 2025/01/26
 * @version: 0.1
 * @description: The class is used to calculate the hypocenter location 
 * using Levenberg-Marquardt algorithm.
 * @usage: 
 */

public class App {
	public static double deg2km = 111.32;
	public static double deg2rad = Math.PI / 180;
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("          ______                __");
			System.out.println("   _  __ /_  __/ _____  ___    / /   ____   _____");
			System.out.println("  | |/_/  / /   / ___/ / _ \\  / /   / __ \\ / ___/");
			System.out.println(" _>  <   / /   / /    /  __/ / /___/ /_/ // /__");
			System.out.println("/_/|_|  /_/   /_/     \\___/ /_____/\\____/ \\___/");
			System.out.println("");
			System.out.println("Usage: java App <mode>");
			System.out.println("Modes:");
			System.out.println("  GRS    - Location by grid search");
			System.out.println("  STD    - Location by Station-pair DD");
			System.out.println("  SYN    - Create dat files for synthetic test");
			System.out.println("  SEE    - View location results");
			System.out.println("  TRD    - Re-location by Triple Difference");
			return;
		}
		try {
			String configFilePath = "config.json";
			AppConfig config = new AppConfig().readConfig(configFilePath);

			Object runner;
			switch (args[0]) {
				case "GRS":
					runner = new HypoGridSearch(config);
					runLocation(config, runner);
					break;
				case "STD":
					runner = new HypoLevenbergMarquardt(config);
					runLocation(config, runner);
					break;
				case "SYN":
					SyntheticTest tester = new SyntheticTest(config);
					tester.generateDataFromCatalog();
					break;
				case "SEE":
					new HypoViewer(config);
					break;
				case "TRD":
					new CalcTripleDiff(config);
					System.out.println("=== Under construction ===");
					break;
				default:
					throw new IllegalArgumentException("Invalid argument: " + args[0]);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void runLocation (AppConfig config, Object locator) {
		ExecutorService executor = null;
		Path[] filePaths = config.getDatPath();
		AtomicInteger progress = new AtomicInteger(0);
		int numTasks = filePaths.length;
		try {
			int numJobs = config.getNumJobs();
			if (numJobs == 1) {
				for (Path filePath : filePaths) {
					String datFile = filePath.toString();
					String outFile = "./dat-out/" + filePath.getFileName();
					((HypoLevenbergMarquardt) locator).start(datFile, outFile);
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
							} else if (locator instanceof HypoLevenbergMarquardt) {
								((HypoLevenbergMarquardt) locator).start(datFile, outFile);
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
