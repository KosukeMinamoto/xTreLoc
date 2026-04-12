package com.treloc.xtreloc.app;

import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.app.util.AsciiLogo;
import com.treloc.xtreloc.app.util.ModeAvailabilityChecker;

/**
 * Main entry point for xTreLoc (also the {@code xtreloc} command from {@code installDist}).
 * <p>
 * Default with no arguments: interactive CLI (boxed logo, then prompts for mode and config).
 * Use {@code --gui} or {@code --tui}
 * for other interfaces. {@code --interactive} offers the legacy menu when stdin is available.
 * With a solver mode name (e.g. GRD) and optional config path, runs CLI directly.
 * From a macOS app bundle (no stdin), launches GUI when available.
 *
 * @author K.Minamoto
 */
public class XTreLoc {

    private static final Logger LOG = Logger.getLogger(XTreLoc.class.getName());

    private static final String GUI_CLASS = "com.treloc.xtreloc.app.gui.XTreLocGUI";
    private static final String TUI_CLASS = "com.treloc.xtreloc.app.tui.XTreLocTUI";

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
            } else if ("--interactive".equals(firstArg) || "-i".equals(firstArg)) {
                if (isStandardInputAvailable()) {
                    showModeSelection();
                } else {
                    System.err.println("Interactive mode selection requires a terminal (stdin).");
                    System.err.println("Use: xtreloc --gui   or   xtreloc --tui   or   xtreloc GRD config.json");
                    System.exit(1);
                }
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
        // No arguments (or only unknown — treat as CLI)
        if (args.length > 0) {
            System.err.println("Unknown option: " + args[0]);
            System.err.println("Run: xtreloc --help");
            System.exit(1);
            return;
        }
        boolean fromAppBundle = isRunningFromAppBundle();
        if (fromAppBundle) {
            if (ModeAvailabilityChecker.isGUIAvailable()) {
                launchGUI(new String[0]);
            } else {
                printModeBuildGuide("GUI");
                System.exit(1);
            }
            return;
        }
        // Default: command-line mode (CLI help or batch usage documented there)
        launchCLI(new String[0]);
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
                    AsciiLogo.print(System.out);
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
            LOG.log(Level.SEVERE, "Interactive mode selection failed", e);
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
        com.treloc.xtreloc.app.cli.XTreLocCLI.runInteractiveLoop(scanner,
            "Exiting CLI. Returning to interface menu.");
    }
    
    private static void launchGUI(String[] args) {
        if (!ModeAvailabilityChecker.isGUIAvailable()) {
            printModeBuildGuide("GUI");
            System.exit(1);
            return;
        }
        try {
            String[] guiArgs = removeModeFlag(args, "--gui", "-g");
            invokeStaticMain(GUI_CLASS, guiArgs);
        } catch (Throwable t) {
            Throwable u = unwrapThrowable(t);
            System.err.println("Failed to launch GUI mode: " + u.getMessage());
            LOG.log(Level.SEVERE, "Failed to launch GUI mode", u);
            System.exit(1);
        }
    }
    
    private static void launchTUI(String[] args) {
        if (!ModeAvailabilityChecker.isTUIAvailable()) {
            printModeBuildGuide("TUI");
            System.exit(1);
            return;
        }
        try {
            String[] tuiArgs = removeModeFlag(args, "--tui", "-t");
            invokeStaticMain(TUI_CLASS, tuiArgs);
        } catch (Throwable t) {
            Throwable u = unwrapThrowable(t);
            System.err.println("Failed to launch TUI mode: " + u.getMessage());
            LOG.log(Level.SEVERE, "Failed to launch TUI mode", u);
            System.exit(1);
        }
    }

    private static void invokeStaticMain(String className, String[] mainArgs) throws Exception {
        Class<?> c = Class.forName(className);
        java.lang.reflect.Method m = c.getMethod("main", String[].class);
        m.invoke(null, (Object) mainArgs);
    }

    private static Throwable unwrapThrowable(Throwable t) {
        if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    /**
     * Explains that GUI or TUI is missing from this build and how to obtain a full build.
     */
    private static void printModeBuildGuide(String mode) {
        System.err.println();
        System.err.println("========================================");
        System.err.println(mode + " mode is not available in this build.");
        System.err.println("========================================");
        System.err.println();
        System.err.println("This usually means you are using a CLI-only JAR (e.g. xTreLoc-CLI.jar)");
        System.err.println("or a distribution built without Swing / GeoTools / Lanterna on the classpath.");
        System.err.println();
        System.err.println("To use " + mode + ":");
        System.err.println("  - Build the full application:  ./gradlew installDist");
        System.err.println("  - Run:  build/install/xtreloc/bin/xtreloc --" + mode.toLowerCase());
        System.err.println("  - Or fat JAR:  ./gradlew uberJar  then  java -jar build/libs/xTreLoc-*-all.jar --" + mode.toLowerCase());
        System.err.println();
        System.err.println("Command-line mode is always available, e.g.:");
        System.err.println("  xtreloc GRD config.json");
        System.err.println("  xtreloc --help");
        System.err.println();
    }
    
    private static void launchCLI(String[] args) {
        try {
            String[] cliArgs = removeModeFlag(args, "--cli", "-c");
            com.treloc.xtreloc.app.cli.XTreLocCLI.main(cliArgs);
        } catch (Exception e) {
            System.err.println("Failed to launch CLI mode: " + e.getMessage());
            LOG.log(Level.SEVERE, "Failed to launch CLI mode", e);
            System.exit(1);
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
        System.out.println("  xtreloc [OPTIONS]");
        System.out.println("  xtreloc [MODE] [config.json]");
        System.out.println("  java -jar xTreLoc.jar ...   (same as above)");
        System.out.println("");
        System.out.println("Default (no arguments): interactive CLI (prompts for mode and config).");
        System.out.println("Install: ./gradlew installDist  →  build/install/xtreloc/bin/xtreloc");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("  --gui, -g           Graphical user interface");
        System.out.println("  --tui, -t           Text user interface (terminal menu)");
        System.out.println("  --cli, -c           Command-line mode (same as default with no args)");
        System.out.println("  --interactive, -i   Interactive menu to choose GUI / TUI / CLI (needs stdin)");
        System.out.println("  --help, -h          Show this help message");
        System.out.println("  --version, -v       Show version information");
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
        System.out.println("  xtreloc                          # Interactive CLI (default)");
        System.out.println("  xtreloc --gui                    # Launch GUI (requires full build)");
        System.out.println("  xtreloc --tui                    # Launch TUI");
        System.out.println("  xtreloc --interactive            # Choose interface in terminal");
        System.out.println("  xtreloc GRD config.json          # Run GRD via CLI");
        System.out.println("");
    }

    /** Prints version string to stdout. */
    private static void showVersion() {
        String version = com.treloc.xtreloc.util.VersionInfo.getVersionString();
        System.out.println(version);
    }
    
}
