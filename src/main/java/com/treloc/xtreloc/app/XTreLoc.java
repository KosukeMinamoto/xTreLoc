package com.treloc.xtreloc.app;

import java.util.Scanner;
import com.treloc.xtreloc.app.util.ModeAvailabilityChecker;

/**
 * Main entry point for xTreLoc.
 * <p>
 * With no arguments and a TTY, shows interactive mode selection (GUI / TUI / CLI).
 * With {@code --gui}, {@code --tui}, or {@code --cli} launches the corresponding mode.
 * With a solver mode name (e.g. GRD) and optional config path, runs CLI directly.
 * From an macOS app bundle, launches GUI directly (no stdin).
 *
 * @author K.Minamoto
 */
public class XTreLoc {

    private static boolean isRunningFromAppBundle() {
        try {
            if (System.in.available() == 0 && System.console() == null) {
                return true;
            }
        } catch (Exception e) {
            return true;
        }
        String classpath = System.getProperty("java.class.path", "");
        if (classpath.contains(".app/Contents")) {
            return true;
        }
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") && System.console() == null) {
            return true;
        }
        return false;
    }

    /**
     * Returns whether standard input is available for interactive mode selection.
     */
    private static boolean isStandardInputAvailable() {
        try {
            if (System.console() != null) return true;
            return !isRunningFromAppBundle();
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            String firstArg = args[0];
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
            
            if (isModeName(firstArg)) {
                launchCLI(args);
                return;
            }
        }
        boolean fromAppBundle = isRunningFromAppBundle();
        boolean stdinAvailable = isStandardInputAvailable();
        if (fromAppBundle) {
            launchGUI(new String[0]);
            return;
        }
        if (stdinAvailable) {
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

    /** Returns true if the argument is a known solver mode name (GRD, LMO, MCMC, DE, TRD, CLS, SYN). */
    private static boolean isModeName(String arg) {
        String upper = arg.toUpperCase();
        return upper.equals("GRD") || upper.equals("LMO") || upper.equals("MCMC") || upper.equals("DE") ||
               upper.equals("TRD") || upper.equals("CLS") || upper.equals("SYN");
    }
    
    private static void showModeSelection() {
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

            boolean tuiAvailableForSelection = tuiAvailable && isStandardInputAvailable();
            boolean showLogo = true;

            while (true) {
                if (showLogo) {
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
                    System.out.println("");
                    String version = com.treloc.xtreloc.util.VersionInfo.getVersionString();
                    System.out.println(version);
                    System.out.println("");
                    showLogo = false;
                }
                printModeSelection(guiAvailable, tuiAvailableForSelection, cliAvailable);
                
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
                continue;
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

    /**
     * Prints the interface mode menu (no logo). Used after invalid choice or when returning from CLI.
     *
     * @param guiAvailable whether GUI mode is available
     * @param tuiAvailableForSelection whether TUI is available and stdin is usable
     * @param cliAvailable whether CLI mode is available
     */
    private static void printModeSelection(boolean guiAvailable, boolean tuiAvailableForSelection, boolean cliAvailable) {
        System.out.println("Select interface mode:");
        int optionNumber = 1;
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
    }
    /**
     * Runs CLI in a loop: prompts for solver mode and config path, runs once, then prompts again.
     * Typing q at either prompt exits back to the interface mode menu.
     */
    private static void runCLIInteractive(Scanner scanner) {
        System.out.println("\n--- CLI - Run from config file ---");
        System.out.println("Enter solver mode or config; use q + Enter to exit CLI and return to menu.");
        while (true) {
            System.out.println("");
            System.out.print("Solver mode (GRD/LMO/MCMC/DE/TRD/CLS/SYN) [GRD]: ");
            String modeLine = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (modeLine.isEmpty() && scanner.hasNext()) {
                modeLine = scanner.next().trim();
                if (scanner.hasNextLine()) scanner.nextLine();
            }
            if ("q".equalsIgnoreCase(modeLine)) {
                System.out.println("Exiting CLI.");
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
                System.out.println("Exiting CLI.");
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
    }
    
    private static void launchGUI(String[] args) {
        try {
            String[] guiArgs = removeModeFlag(args, "--gui", "-g");
            com.treloc.xtreloc.app.gui.XTreLocGUI.main(guiArgs);
        } catch (Exception e) {
            System.err.println("Failed to launch GUI mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void launchTUI(String[] args) {
        try {
            String[] tuiArgs = removeModeFlag(args, "--tui", "-t");
            com.treloc.xtreloc.app.tui.XTreLocTUI.main(tuiArgs);
        } catch (Exception e) {
            System.err.println("Failed to launch TUI mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void launchCLI(String[] args) {
        try {
            String[] cliArgs = removeModeFlag(args, "--cli", "-c");
            com.treloc.xtreloc.app.cli.XTreLocCLI.main(cliArgs);
        } catch (Exception e) {
            System.err.println("Failed to launch CLI mode: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Removes the given mode flag (and its value if present) from args. */
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
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        i++;
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

    /** Prints command-line help to stdout. */
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

    /** Prints version string to stdout. */
    private static void showVersion() {
        String version = com.treloc.xtreloc.util.VersionInfo.getVersionString();
        System.out.println(version);
    }
    
}
