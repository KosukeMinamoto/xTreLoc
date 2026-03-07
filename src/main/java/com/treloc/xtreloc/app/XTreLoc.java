package com.treloc.xtreloc.app;

import java.util.Scanner;
import com.treloc.xtreloc.app.util.ModeAvailabilityChecker;

/**
 * Main entry point for xTreLoc application.
 * Allows users to choose between GUI, TUI, and CLI modes.
 *
 * @author K.Minamoto
 */
public class XTreLoc {
    
    /**
     * Checks if the application is running from a macOS app bundle.
     * App bundles don't have access to standard input, so TUI mode won't work.
     * 
     * @return true if running from an app bundle
     */
    private static boolean isRunningFromAppBundle() {
        // Check if standard input is available
        try {
            if (System.in.available() == 0 && System.console() == null) {
                // Try to read from stdin - if it fails, we're likely in an app bundle
                return true;
            }
        } catch (Exception e) {
            // If we can't check stdin, assume we're in an app bundle
            return true;
        }
        
        // Check classpath for .app bundle indicators
        String classpath = System.getProperty("java.class.path", "");
        if (classpath.contains(".app/Contents")) {
            return true;
        }
        
        // Check if we're on macOS and console is null (typical for app bundles)
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") && System.console() == null) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if standard input is available for interactive use.
     * 
     * @return true if stdin is available
     */
    private static boolean isStandardInputAvailable() {
        try {
            // Try to check if stdin is available
            if (System.console() != null) {
                return true;
            }
            // For app bundles, System.console() is typically null
            // and System.in may not be properly connected
            return !isRunningFromAppBundle();
        } catch (Exception e) {
            return false;
        }
    }
    
    public static void main(String[] args) {
        // If arguments are provided, check for mode switching or CLI mode
        if (args.length > 0) {
            String firstArg = args[0];
            
            // Check for mode switching commands
            if ("--gui".equals(firstArg) || "-g".equals(firstArg)) {
                launchGUI(args);
                return;
            } else if ("--tui".equals(firstArg) || "-t".equals(firstArg)) {
                launchTUI(args);
                return;
            } else if ("--cli".equals(firstArg) || "-c".equals(firstArg)) {
                launchCLI(args);
                return;
            } else if ("--help".equals(firstArg) || "-h".equals(firstArg)) {
                showHelp();
                return;
            } else if ("--version".equals(firstArg) || "-v".equals(firstArg)) {
                showVersion();
                return;
            }
            
            // If first argument is a mode name (GRD, LMO, etc.), launch CLI directly
            if (isModeName(firstArg)) {
                launchCLI(args);
                return;
            }
        }
        
        // Check if running from app bundle (no stdin available)
        boolean fromAppBundle = isRunningFromAppBundle();
        boolean stdinAvailable = isStandardInputAvailable();
        
        // If running from app bundle, launch GUI directly (most common use case)
        // TUI/CLI can be launched from command line: java -jar xTreLoc.jar --tui or --cli
        if (fromAppBundle) {
            launchGUI(new String[0]);
            return;
        }
        
        // No arguments - show interactive mode selection
        if (stdinAvailable) {
            // Show text-based mode selection
            showModeSelection();
        } else {
            // No stdin available - try GUI
            if (ModeAvailabilityChecker.isGUIAvailable()) {
                System.err.println("Standard input is not available. Launching GUI mode.");
                launchGUI(new String[0]);
            } else {
                System.err.println("ERROR: No usable interface mode available.");
                System.err.println("GUI mode is not available and standard input is not accessible.");
            }
        }
    }
    
    private static boolean isModeName(String arg) {
        String upper = arg.toUpperCase();
        return upper.equals("GRD") || upper.equals("LMO") || upper.equals("MCMC") || upper.equals("DE") ||
               upper.equals("TRD") || upper.equals("CLS") || upper.equals("SYN");
    }
    
    private static void showModeSelection() {
        // Check if stdin is available before trying to use Scanner
        if (!isStandardInputAvailable()) {
            System.err.println("Standard input is not available. Cannot show interactive mode selection.");
            if (ModeAvailabilityChecker.isGUIAvailable()) {
                System.err.println("Launching GUI mode instead.");
                launchGUI(new String[0]);
            }
            return;
        }
        
        Scanner scanner = new Scanner(System.in);
        
        try {
            boolean guiAvailable = ModeAvailabilityChecker.isGUIAvailable();
            boolean tuiAvailable = ModeAvailabilityChecker.isTUIAvailable();
            boolean cliAvailable = ModeAvailabilityChecker.isCLIAvailable();
            
            if (!guiAvailable && !tuiAvailable && !cliAvailable) {
                System.err.println("ERROR: No interface modes are available.");
                System.err.println("Please ensure the application is properly built.");
                return;
            }
            
            while (true) {
                System.out.println("");
                System.out.println("╔──────────────────────────────────────────────────────────────────────────────╗");
                System.out.println("│              MMP\"\"MM\"\"YMM                   `7MMF'                           │");
                System.out.println("│              P'   MM   `7                     MM                             │");
                System.out.println("│   `7M'   `MF'     MM      `7Mb,od8  .gP\"Ya    MM         ,pW\"Wq.   ,p6\"bo    │");
                System.out.println("│     `VA ,V'       MM        MM' \"' ,M'   Yb   MM        6W'   `Wb 6M'  OO    │");
                System.out.println("│       XMX         MM        MM     8M\"\"\"\"\"\"   MM      , 8M     M8 8M         │");
                System.out.println("│     ,V' VA.       MM        MM     YM.    ,   MM     ,M YA.   ,A9 YM.    ,   │");
                System.out.println("│   .AM.   .MA.   .JMML.    .JMML.    `Mbmmd' .JMMmmmmMMM  `Ybmd9'   YMbmd'    │");
                System.out.println("╚──────────────────────────────────────────────────────────────────────────────╝");
                // Slant
                // System.out.println("          ______                __");
                // System.out.println("   _  __ /_  __/ _____  ___    / /   ____   _____");
                // System.out.println("  | |/_/  / /   / ___/ / _ \\  / /   / __ \\ / ___/");
                // System.out.println(" _>  <   / /   / /    /  __/ / /___/ /_/ // /__");
                // System.out.println("/_/|_|  /_/   /_/     \\___/ /_____/\\____/ \\___/");
                // Cyberlarge
                // System.out.println("╔────────────────────────────────────────────────────────────╗");
                // System.out.println("│   _     _ _______  ______ _______         _____  _______   │");
                // System.out.println("│    \\___/     |    |_____/ |______ |      |     | |         │");
                // System.out.println("│   _/   \\_    |    |    \\_ |______ |_____ |_____| |_____    │");
                // System.out.println("╚────────────────────────────────────────────────────────────╝");
                System.out.println("");
                String version = com.treloc.xtreloc.util.VersionInfo.getVersionString();
                System.out.println(version);
                System.out.println("");
                // final int boxWidth = 51;
                // String border = "╔" + "═".repeat(boxWidth) + "╗";
                // String titleContent = " xTreLoc v" + version + " - Mode Selection ";
                // if (titleContent.length() > boxWidth) {
                //     titleContent = titleContent.substring(0, boxWidth);
                // } else {
                //     titleContent = String.format("%-" + boxWidth + "s", titleContent);
                // }
                // System.out.println(border);
                // System.out.println("║" + titleContent + "║");
                // System.out.println("╚" + "═".repeat(boxWidth) + "╝");
                // System.out.println();
                System.out.println("Select interface mode:");
                
                int optionNumber = 1;
                boolean tuiAvailableForSelection = tuiAvailable && isStandardInputAvailable();
                
                if (guiAvailable) {
                    System.out.println("  " + optionNumber + ". GUI - Graphical User Interface (recommended)");
                    optionNumber++;
                }
                if (tuiAvailableForSelection) {
                    System.out.println("  " + optionNumber + ". TUI - Text User Interface (interactive menu)");
                    optionNumber++;
                }
                if (cliAvailable) {
                    System.out.println("  " + optionNumber + ". CLI - Command Line Interface (batch processing)");
                    optionNumber++;
                }
                
                System.out.println();
                System.out.println("  0 or q. Exit");
                System.out.println();
                System.out.print("Enter your choice: ");
                
                String line = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
                if (line.isEmpty() && scanner.hasNext()) {
                    line = scanner.next().trim();
                    if (scanner.hasNextLine()) scanner.nextLine();
                }
                
                if ("q".equalsIgnoreCase(line) || "0".equals(line)) {
                    System.out.println("\nExiting. Goodbye!");
                    return;
                }
                
                int choice = -1;
                try {
                    choice = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                    System.out.println("\nInvalid choice. Enter 1, 2, 3, 0, or q.");
                    continue;
                }
                
                int currentOption = 1;
                if (guiAvailable && choice == currentOption++) {
                    System.out.println("\nLaunching GUI mode...");
                    launchGUI(new String[0]);
                    return;
                }
                if (tuiAvailableForSelection && choice == currentOption++) {
                    System.out.println("\nLaunching TUI mode...");
                    launchTUI(new String[0]);
                    return;
                }
                if (cliAvailable && choice == currentOption++) {
                    runCLIInteractive(scanner);
                    continue;
                }
                
                System.out.println("\nInvalid choice. Enter 1, 2, 3, 0, or q.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (scanner != null) {
                    scanner.close();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    private static void runCLIInteractive(Scanner scanner) {
        System.out.println("\n--- CLI - Run from config file ---");
        System.out.print("Solver mode (GRD/LMO/MCMC/DE/TRD/CLS/SYN) [GRD]: ");
        String modeLine = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        if (modeLine.isEmpty() && scanner.hasNext()) {
            modeLine = scanner.next().trim();
            if (scanner.hasNextLine()) scanner.nextLine();
        }
        if ("q".equalsIgnoreCase(modeLine)) {
            System.out.println("Cancelled.");
            return;
        }
        String mode = modeLine.isEmpty() ? "GRD" : modeLine.toUpperCase();
        
        System.out.print("Config path [config.json]: ");
        String configLine = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        if (configLine.isEmpty() && scanner.hasNext()) {
            configLine = scanner.next().trim();
            if (scanner.hasNextLine()) scanner.nextLine();
        }
        if ("q".equalsIgnoreCase(configLine)) {
            System.out.println("Cancelled.");
            return;
        }
        String configPath = configLine.isEmpty() ? "config.json" : configLine;
        
        System.out.println("\nRunning: mode=" + mode + ", config=" + configPath);
        try {
            com.treloc.xtreloc.app.cli.XTreLocCLI.runWithoutLogo(mode, configPath);
            System.out.println("\nCLI completed successfully.");
        } catch (Exception e) {
            System.err.println("\nCLI failed: " + e.getMessage());
        }
        System.out.println("");
    }
    
    private static void launchGUI(String[] args) {
        try {
            // Remove mode flag if present
            String[] guiArgs = removeModeFlag(args, "--gui", "-g");
            com.treloc.xtreloc.app.gui.XTreLocGUI.main(guiArgs);
        } catch (Exception e) {
            System.err.println("Failed to launch GUI mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void launchTUI(String[] args) {
        try {
            // Remove mode flag if present
            String[] tuiArgs = removeModeFlag(args, "--tui", "-t");
            com.treloc.xtreloc.app.tui.XTreLocTUI.main(tuiArgs);
        } catch (Exception e) {
            System.err.println("Failed to launch TUI mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void launchCLI(String[] args) {
        try {
            // Remove mode flag if present
            String[] cliArgs = removeModeFlag(args, "--cli", "-c");
            com.treloc.xtreloc.app.cli.XTreLocCLI.main(cliArgs);
        } catch (Exception e) {
            System.err.println("Failed to launch CLI mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String[] removeModeFlag(String[] args, String... flags) {
        if (args.length == 0) {
            return args;
        }
        
        java.util.List<String> result = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            boolean isFlag = false;
            for (String flag : flags) {
                if (flag.equals(args[i])) {
                    isFlag = true;
                    // If flag is followed by a value (like --config path), skip the value too
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        i++; // skip the value
                    }
                    break;
                }
            }
            if (!isFlag) {
                result.add(args[i]);
            }
        }
        return result.toArray(new String[0]);
    }
    
    private static void showHelp() {
        System.out.println("");
        System.out.println("xTreLoc - Multi-method hypocenter location software");
        System.out.println("");
        System.out.println("Usage:");
        System.out.println("  java -jar xTreLoc.jar [OPTIONS]");
        System.out.println("  java -jar xTreLoc.jar [MODE] [config.json]");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("  --gui, -g        Launch GUI mode");
        System.out.println("  --tui, -t        Launch TUI mode");
        System.out.println("  --cli, -c        Launch CLI mode");
        System.out.println("  --help, -h       Show this help message");
        System.out.println("  --version, -v    Show version information");
        System.out.println("");
        System.out.println("Modes (CLI):");
        System.out.println("  GRD              Grid search location");
        System.out.println("  LMO              Levenberg-Marquardt optimization");
        System.out.println("  MCMC             Markov Chain Monte Carlo location");
        System.out.println("  TRD              Triple difference relocation");
        System.out.println("  CLS              Spatial clustering");
        System.out.println("  SYN              Synthetic test data generation");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  java -jar xTreLoc.jar                    # Interactive mode selection");
        System.out.println("  java -jar xTreLoc.jar --gui              # Launch GUI");
        System.out.println("  java -jar xTreLoc.jar --tui              # Launch TUI");
        System.out.println("  java -jar xTreLoc.jar GRD config.json    # Run GRD mode via CLI");
        System.out.println("");
    }
    
    private static void showVersion() {
        String version = com.treloc.xtreloc.util.VersionInfo.getVersionString();
        System.out.println(version);
    }
    
}
