package com.treloc.xtreloc.app.tui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.TerminalResizeListener;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.ConfigLoader;
import com.treloc.xtreloc.io.FileScanner;
import com.treloc.xtreloc.io.RunContext;
import com.treloc.xtreloc.io.RunContextFactory;
import com.treloc.xtreloc.util.LogInitializer;
import com.treloc.xtreloc.util.SolverLogger;

/**
 * TUI (Text User Interface) entry point for xTreLoc using Lanterna library.
 * Provides an interactive menu-driven interface for running location algorithms.
 * 
 * Layout: Full-screen input panel with separate log window
 *         - Main window: Full-screen input/control panel
 *         - Log window: Separate window that can be opened/closed as needed
 *
 * @author K.Minamoto
 */
public final class XTreLocTUI {
    
    private static final Logger logger = Logger.getLogger(XTreLocTUI.class.getName());
    private static final int MIN_COLS = 60;  // Reduced from 80 to allow smaller terminals
    private static final int MIN_ROWS = 15;  // Reduced from 24 to allow smaller terminals
    private static final int MAX_LOG_LINES = 1000;  // Maximum number of log lines to keep in buffer
    
    private AppConfig config;
    private String configPath = "config.json";
    private Screen screen;
    private WindowBasedTextGUI textGUI;
    private BasicWindow mainWindow;
    private Panel mainContainer;
    private Panel leftPanel;
    private TextBox logArea;
    private BasicWindow logWindow;
    private List<String> logBuffer;
    private String currentMode;
    private AppConfig currentInputConfig;
    private Thread currentExecutionThread;
    private AtomicBoolean isCancelled;
    
    /** Per-mode form state cache so that "Back to Parameters" restores previously entered values. */
    private final java.util.Map<String, java.util.Map<String, String>> tuiModeFormStateCache = new java.util.HashMap<>();

    /** Last batch run result (for cancelled state: export partial catalog). */
    private int lastBatchProcessedCount = 0;
    private java.nio.file.Path lastBatchOutDir = null;
    
    public static void main(String[] args) {
        XTreLocTUI tui = new XTreLocTUI();
        
        if (args.length > 0 && "--config".equals(args[0]) && args.length > 1) {
            tui.configPath = args[1];
        }
        
        try {
            tui.initialize();
            tui.run();
        } catch (Exception e) {
            System.err.println("\nFatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            tui.cleanup();
        }
    }
    
    private void initialize() throws Exception {
        
        try {
            isCancelled = new AtomicBoolean(false);
            
            File logFile = com.treloc.xtreloc.app.gui.util.LogHistoryManager.getLogFile();
            com.treloc.xtreloc.app.gui.util.AppSettings appSettings = null;
            try {
                appSettings = com.treloc.xtreloc.app.gui.util.AppSettings.load();
            } catch (Exception ignored) { }
            if (appSettings != null) {
                LogInitializer.setup(logFile.getAbsolutePath(), java.util.logging.Level.INFO,
                    appSettings.getLogLimitBytes(), appSettings.getLogCount());
            } else {
                LogInitializer.setup(logFile.getAbsolutePath(), java.util.logging.Level.INFO);
            }
            
            String version = com.treloc.xtreloc.app.gui.util.VersionInfo.getVersionString();
            logger.info("========================================");
            logger.info("xTreLoc TUI mode started");
            logger.info(version);
            logger.info("Log file: " + logFile.getAbsolutePath());
            logger.info("========================================");
            
            // Initialize Lanterna terminal
            Terminal terminal = new DefaultTerminalFactory().createTerminal();
            screen = new TerminalScreen(terminal);
            screen.startScreen();
            
            // Create GUI
            textGUI = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLUE));
            
            // Initialize log buffer (ring buffer)
            logBuffer = new ArrayList<>(MAX_LOG_LINES);
            
            // Set TUI mode for SolverLogger
            SolverLogger.setMode(false, false, true);
            SolverLogger.setCallback((message, level) -> {
                // Only display INFO, WARNING, and SEVERE in TUI
                // FINE, FINER, FINEST (debug info) go only to file
                if (level.intValue() >= java.util.logging.Level.INFO.intValue()) {
                    appendToLog(message);
                }
            });
            
            // Create main window with responsive layout (this initializes logArea and leftPanel)
            createMainWindow();
            
            // Add terminal resize listener (after main window is created)
            terminal.addResizeListener(new TerminalResizeListener() {
                @Override
                public void onResized(Terminal terminal, TerminalSize newSize) {
                    textGUI.getGUIThread().invokeLater(() -> {
                        String savedModeBeforeResize = currentMode;
                        updateLayout();
                        // Restore view state after resize
                        if (savedModeBeforeResize == null && leftPanel != null) {
                            showSolverSelection();
                        }
                    });
                }
            });
            
            // Check terminal size and warn if too small (after logArea is initialized)
            TerminalSize initialSize = screen.getTerminalSize();
            if (initialSize.getColumns() < MIN_COLS || initialSize.getRows() < MIN_ROWS) {
                // Use Lanterna's message dialog instead of blocking stdin
                // This avoids issues with stdin being closed
                appendToLog("WARNING: Terminal size is small (" + 
                    initialSize.getColumns() + "x" + initialSize.getRows() + 
                    "). Recommended: at least " + MIN_COLS + "x" + MIN_ROWS);
                appendToLog("The interface may not display optimally.");
            }
            
            // TUI mode: Do not load config file automatically
            // All configuration will be entered interactively when running modes
        } catch (Exception e) {
            System.err.println("Failed to initialize TUI: " + e.getMessage());
            throw e;
        }
    }
    
    private void createMainWindow() {
        mainWindow = new BasicWindow("xTreLoc TUI");
        mainWindow.setHints(java.util.Arrays.asList(Window.Hint.FULL_SCREEN));
        mainWindow.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean hasBeenHandled) {
                if (key.getKeyType() == KeyType.Character && (key.getCharacter() == 'L' || key.getCharacter() == 'l')) {
                    showLogWindow();
                    hasBeenHandled.set(true);
                }
            }
        });
        updateLayout();
        
        // Show solver selection initially (after layout is set up)
        // Call directly to ensure it's shown immediately
        if (leftPanel != null && currentMode == null && leftPanel.getChildCount() == 0) {
            showSolverSelection();
        }
    }
    
    private void updateLayout() {
        TerminalSize terminalSize = screen.getTerminalSize();
        int cols = terminalSize.getColumns();
        int rows = terminalSize.getRows();
        
        // Main container - create only if it doesn't exist
        if (mainContainer == null) {
            mainContainer = new Panel();
        }
        
        // Simple vertical layout for single panel
        mainContainer.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        
        // Check if we need to rebuild
        boolean needsRebuild = false;
        if (mainContainer.getChildCount() == 0) {
            needsRebuild = true;
        } else {
            java.util.List<Component> children = mainContainer.getChildrenList();
            if (children.isEmpty() || children.get(0) != leftPanel) {
                needsRebuild = true;
            }
        }
        
        // Store current mode state before rebuilding
        String savedMode = currentMode;
        
        if (needsRebuild) {
            // Clear existing components
            mainContainer.removeAllComponents();
            
            // Create or reuse left panel (full screen input)
            if (leftPanel == null) {
                leftPanel = new Panel();
                leftPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
            }
            
            // Set full screen size
            leftPanel.setPreferredSize(new TerminalSize(cols - 2, rows - 2));
            
            // Add to main container
            mainContainer.addComponent(leftPanel);
            
            logArea = null;
        } else {
            // Just update size
            if (leftPanel != null) {
                leftPanel.setPreferredSize(new TerminalSize(cols - 2, rows - 2));
            }
            currentMode = savedMode;
            return;
        }
        
        // Set component only if not already set
        if (mainWindow.getComponent() != mainContainer) {
            mainWindow.setComponent(mainContainer);
        }
        
        if (!textGUI.getWindows().contains(mainWindow)) {
            textGUI.addWindow(mainWindow);
        }
        
        // Restore current mode state
        currentMode = savedMode;
        
        // If leftPanel was just initialized and we're in initial state, show solver selection
        if (leftPanel != null && savedMode == null && leftPanel.getChildCount() == 0) {
            showSolverSelection();
        }
    }
    
    private void showSolverSelection() {
        if (leftPanel == null) {
            // Panel not initialized yet, wait for layout update
            return;
        }
        
        // Ensure UI updates happen on GUI thread
        textGUI.getGUIThread().invokeLater(() -> {
            if (leftPanel == null) {
                return;
            }
            
            leftPanel.removeAllComponents();
            currentMode = null;
            
            String version = com.treloc.xtreloc.app.gui.util.VersionInfo.getVersionString();
            Label titleLabel = new Label("xTreLoc " + version);
            titleLabel.setForegroundColor(TextColor.ANSI.CYAN);
            leftPanel.addComponent(titleLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Label locationLabel = new Label("Select Location Method:");
            locationLabel.setForegroundColor(TextColor.ANSI.YELLOW);
            leftPanel.addComponent(locationLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Button grdButton = new Button("GRD - Grid search", () -> {
                currentMode = "GRD";
                showParameterInput("GRD");
            });
            Button lmoButton = new Button("LMO - Levenberg-Marquardt", () -> {
                currentMode = "LMO";
                showParameterInput("LMO");
            });
            Button mcmcButton = new Button("MCMC - Monte Carlo", () -> {
                currentMode = "MCMC";
                showParameterInput("MCMC");
            });
            Button deButton = new Button("DE - Differential Evolution", () -> {
                currentMode = "DE";
                showParameterInput("DE");
            });
            Button trdButton = new Button("TRD - Triple difference", () -> {
                currentMode = "TRD";
                showParameterInput("TRD");
            });
            Button clsButton = new Button("CLS - Spatial clustering", () -> {
                currentMode = "CLS";
                showParameterInput("CLS");
            });
            Button synButton = new Button("SYN - Synthetic test", () -> {
                currentMode = "SYN";
                showParameterInput("SYN");
            });
            
            leftPanel.addComponent(grdButton);
            leftPanel.addComponent(lmoButton);
            leftPanel.addComponent(mcmcButton);
            leftPanel.addComponent(deButton);
            leftPanel.addComponent(trdButton);
            leftPanel.addComponent(clsButton);
            leftPanel.addComponent(synButton);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Label configLabel = new Label("Configuration:");
            configLabel.setForegroundColor(TextColor.ANSI.YELLOW);
            leftPanel.addComponent(configLabel);
            
            Button configButton = new Button("Load config file", () -> {
                changeConfig();
            });
            leftPanel.addComponent(configButton);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Label logLabel = new Label("Log Window:");
            logLabel.setForegroundColor(TextColor.ANSI.YELLOW);
            leftPanel.addComponent(logLabel);
            
            Button logButton = new Button("Show Log Window", () -> {
                showLogWindow();
            });
            leftPanel.addComponent(logButton);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Label modeLabel = new Label("Mode Switching:");
            modeLabel.setForegroundColor(TextColor.ANSI.YELLOW);
            leftPanel.addComponent(modeLabel);
            
            Button guiButton = new Button("Switch to GUI", () -> {
                switchToGUI();
            });
            leftPanel.addComponent(guiButton);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Button exitButton = new Button("Exit", () -> {
                showExitDialog();
            });
            leftPanel.addComponent(exitButton);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            if (config != null) {
                Label configStatus = new Label("Config: " + configPath);
                configStatus.setForegroundColor(TextColor.ANSI.GREEN);
                leftPanel.addComponent(configStatus);
            } else {
                Label configStatus = new Label("Config: (not loaded)");
                configStatus.setForegroundColor(TextColor.ANSI.YELLOW);
                leftPanel.addComponent(configStatus);
            }
            
            appendToLog("xTreLoc TUI started. Select a location method to begin.");
            appendToLog("NOTE: Log output will be shown in a separate window. Press 'L' to open log window.");

            grdButton.takeFocus();
        });
    }

    private void showParameterInput(String mode) {

        textGUI.getGUIThread().invokeLater(() -> {

            if (leftPanel == null) {
                appendToLog("ERROR: Left panel not initialized.");
                return;
            }

            leftPanel.removeAllComponents();

            currentMode = mode;

            Panel paramPanel = new Panel();
            paramPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

            Label title = new Label("Parameter Input : " + mode);
            title.setForegroundColor(TextColor.ANSI.CYAN);
            paramPanel.addComponent(title);

            paramPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

            ConfigInputHelper inputHelper;
            try {
                inputHelper = new ConfigInputHelper(
                        textGUI,
                        leftPanel,
                        paramPanel,
                        logArea,
                        tuiModeFormStateCache);
            } catch (Exception e) {
                appendToLog("ERROR: Failed to initialize parameter form: " + e.getMessage());
                showSolverSelection();
                return;
            }

            try {
                inputHelper.createParameterForm(
                        mode,
                        () -> {
                            currentInputConfig = inputHelper.getCurrentConfig();
                            showExecutionPanel(mode);
                        },
                        () -> {
                            currentInputConfig = null;
                            showSolverSelection();
                        },
                        null);
            } catch (Exception e) {
                appendToLog("ERROR: Failed to create parameter form: " + e.getMessage());
                showSolverSelection();
                return;
            }

            leftPanel.addComponent(paramPanel);
            leftPanel.invalidate();
            focusFirstInteractable(paramPanel);
            if (mainWindow != null) {
                mainWindow.invalidate();
            }
            textGUI.setActiveWindow(mainWindow);
        });
    }
    
    /**
     * Finds the first focusable component in the panel tree and gives it focus.
     * Fixes the issue where "Back to Parameters" left the UI unresponsive.
     */
    private boolean focusFirstInteractable(Component root) {
        if (root == null) return false;
        if (root instanceof com.googlecode.lanterna.gui2.Interactable) {
            ((com.googlecode.lanterna.gui2.Interactable) root).takeFocus();
            return true;
        }
        if (root instanceof Panel) {
            for (Component child : ((Panel) root).getChildrenList()) {
                if (focusFirstInteractable(child)) return true;
            }
        }
        return false;
    }

    private void showExecutionPanel(String mode) {
        // Ensure UI updates happen on GUI thread
        textGUI.getGUIThread().invokeLater(() -> {
            // Execution panel with run button
            Panel execPanel = new Panel();
            execPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
            
            Label execLabel = new Label("Ready to execute: " + mode);
            execLabel.setForegroundColor(TextColor.ANSI.GREEN);
            execPanel.addComponent(execLabel);
            
            execPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Button runButton = new Button("Run " + mode, () -> {
                runMode(mode);
            });
            execPanel.addComponent(runButton);
            
            Button backButton = new Button("Back to Parameters", () -> {
                showParameterInput(mode);
            });
            execPanel.addComponent(backButton);
            
            Button cancelButton = new Button("Cancel", () -> {
                showSolverSelection();
            });
            execPanel.addComponent(cancelButton);
            
            leftPanel.removeAllComponents();
            leftPanel.addComponent(execPanel);

            runButton.takeFocus();
        });
    }
    
    private void run() {
        // Main window is already shown, run event loop
        try {
            while (textGUI.getActiveWindow() != null) {
                // Process events and update screen
                if (!textGUI.getGUIThread().processEventsAndUpdate()) {
                    // No events to process, sleep briefly to avoid busy-waiting
                    Thread.sleep(10);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error processing GUI events", e);
            appendToLog("ERROR: Failed to process GUI events: " + e.getMessage());
        }
    }
    
    private void runMode(String mode) {
        // Get config: priority: currentInputConfig > loaded config
        final AppConfig finalConfig;
        if (currentInputConfig != null) {
            finalConfig = currentInputConfig;
        } else if (config != null) {
            finalConfig = config;
        } else {
            appendToLog("ERROR: Configuration not set. Please input parameters first or load a config file.");
            return;
        }
        
        // Show running status
        showRunningStatus(mode);
        
        isCancelled.set(false);
        lastBatchProcessedCount = 0;
        lastBatchOutDir = null;
        
        currentExecutionThread = new Thread(() -> {
            try {
                RunContext context = RunContextFactory.fromCLI(mode, finalConfig);
                
                com.treloc.xtreloc.util.SolverLogger.info("Mode: " + context.getMode());
                
                switch (mode) {
                    case "GRD":
                        runBatch(context, finalConfig, (dat, out) -> {
                            com.treloc.xtreloc.solver.HypoGridSearch solver = 
                                new com.treloc.xtreloc.solver.HypoGridSearch(finalConfig);
                            solver.start(dat, out);
                        });
                        break;
                        
                    case "LMO":
                        runBatch(context, finalConfig, (dat, out) -> {
                            com.treloc.xtreloc.solver.HypoStationPairDiff solver = 
                                new com.treloc.xtreloc.solver.HypoStationPairDiff(finalConfig);
                            solver.start(dat, out);
                        });
                        break;
                        
                    case "MCMC":
                        runBatch(context, finalConfig, (dat, out) -> {
                            com.treloc.xtreloc.solver.HypoMCMC solver = 
                                new com.treloc.xtreloc.solver.HypoMCMC(finalConfig);
                            solver.start(dat, out);
                        });
                        break;
                        
                    case "DE":
                        runBatch(context, finalConfig, (dat, out) -> {
                            com.treloc.xtreloc.solver.HypoDifferentialEvolution solver = 
                                new com.treloc.xtreloc.solver.HypoDifferentialEvolution(finalConfig);
                            solver.start(dat, out);
                        });
                        break;
                        
                    case "TRD":
                        runTripleDiff(finalConfig);
                        break;
                        
                    case "CLS":
                        runClustering(finalConfig);
                        break;
                        
                    case "SYN":
                        runSyntheticTest(finalConfig);
                        break;
                }
                
                if (!isCancelled.get()) {
                    showCompletedStatus(mode);
                    com.treloc.xtreloc.util.SolverLogger.info(mode + " Mode Completed Successfully");
                } else {
                    appendToLog("Execution cancelled.");
                    showCancelledStatus(mode, lastBatchProcessedCount, lastBatchOutDir);
                }
                
            } catch (Exception e) {
                if (!isCancelled.get()) {
                    showErrorStatus(mode, e.getMessage());
                    com.treloc.xtreloc.util.SolverLogger.severe(mode + " mode failed: " + e.getMessage());
                    logger.log(Level.SEVERE, mode + " mode failed", e);
                } else {
                    showCancelledStatus(mode, lastBatchProcessedCount, lastBatchOutDir);
                }
            }
        });
        currentExecutionThread.start();
    }
    
    private void showCancelledStatus(String mode, int processedCount, java.nio.file.Path outDir) {
        textGUI.getGUIThread().invokeLater(() -> {
            leftPanel.removeAllComponents();
            
            Label statusLabel = new Label("Execution cancelled");
            statusLabel.setForegroundColor(TextColor.ANSI.YELLOW);
            leftPanel.addComponent(statusLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Label countLabel = new Label(processedCount > 0
                ? processedCount + " file(s) were processed before cancellation."
                : "No files were completed.");
            countLabel.setForegroundColor(TextColor.ANSI.WHITE);
            leftPanel.addComponent(countLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            if (outDir != null && processedCount > 0) {
                Button exportButton = new Button("Export completed to catalog", () -> {
                    try {
                        generateCatalogFromDatFiles(outDir, mode);
                        appendToLog("Catalog exported from completed files.");
                    } catch (Exception e) {
                        appendToLog("ERROR: Failed to export catalog: " + e.getMessage());
                    }
                });
                leftPanel.addComponent(exportButton);
                leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            }
            
            Button backToParamsButton = new Button("Back to Parameters", () -> {
                showParameterInput(mode);
            });
            leftPanel.addComponent(backToParamsButton);
            
            Button backToSelectionButton = new Button("Back to Selection", () -> {
                showSolverSelection();
            });
            leftPanel.addComponent(backToSelectionButton);
            
            if (outDir != null && processedCount > 0) {
                backToParamsButton.takeFocus();
            } else {
                backToSelectionButton.takeFocus();
            }
        });
    }
    
    private void showRunningStatus(String mode) {
        // Ensure UI updates happen on GUI thread
        textGUI.getGUIThread().invokeLater(() -> {
            leftPanel.removeAllComponents();
            
            Label statusLabel = new Label("Executing " + mode + " Mode");
            statusLabel.setForegroundColor(TextColor.ANSI.CYAN);
            leftPanel.addComponent(statusLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Label progressLabel = new Label("Progress: Initializing...");
            progressLabel.setForegroundColor(TextColor.ANSI.GREEN);
            leftPanel.addComponent(progressLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Label logHintLabel = new Label("For detailed log, press 'L' to open Log Window");
            logHintLabel.setForegroundColor(TextColor.ANSI.YELLOW);
            leftPanel.addComponent(logHintLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 2)));
            
            Button cancelButton = new Button("Cancel Execution", () -> {
                isCancelled.set(true);
                if (currentExecutionThread != null) {
                    currentExecutionThread.interrupt();
                }
                appendToLog("Cancellation requested. Waiting for current tasks to stop...");
            });
            leftPanel.addComponent(cancelButton);

            cancelButton.takeFocus();
        });
    }
    
    private void showCompletedStatus(String mode) {
        // Ensure UI updates happen on GUI thread
        textGUI.getGUIThread().invokeLater(() -> {
            leftPanel.removeAllComponents();
            
            Label statusLabel = new Label(mode + " completed!");
            statusLabel.setForegroundColor(TextColor.ANSI.GREEN);
            leftPanel.addComponent(statusLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Button backButton = new Button("Back to Selection", () -> {
                showSolverSelection();
            });
            leftPanel.addComponent(backButton);

            backButton.takeFocus();
        });
    }
    
    private void showErrorStatus(String mode, String errorMessage) {
        // Ensure UI updates happen on GUI thread (especially important since this is called from background thread)
        textGUI.getGUIThread().invokeLater(() -> {
            leftPanel.removeAllComponents();
            
            Label statusLabel = new Label(mode + " failed!");
            statusLabel.setForegroundColor(TextColor.ANSI.RED);
            leftPanel.addComponent(statusLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            // Truncate long error messages for display
            String shortError = errorMessage.length() > 40 ? 
                errorMessage.substring(0, 37) + "..." : errorMessage;
            Label errorLabel = new Label("Error: " + shortError);
            errorLabel.setForegroundColor(TextColor.ANSI.RED);
            leftPanel.addComponent(errorLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Label logLabel = new Label("See log for details");
            logLabel.setForegroundColor(TextColor.ANSI.YELLOW);
            leftPanel.addComponent(logLabel);
            
            leftPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
            
            Button backButton = new Button("Back to Selection", () -> {
                showSolverSelection();
            });
            leftPanel.addComponent(backButton);

            backButton.takeFocus();
        });
    }
    
    private void runBatch(RunContext context, AppConfig configToUse, LocationTask task) {
        Path[] datFiles = context.getDatFiles();
        int numJobs = Math.max(configToUse.numJobs, 1);
        AtomicInteger progress = new AtomicInteger(0);
        
        ExecutorService executor = (numJobs > 1)
                ? Executors.newFixedThreadPool(numJobs)
                : null;
        
        com.treloc.xtreloc.util.SolverLogger.info("Files to process: " + datFiles.length);
        com.treloc.xtreloc.util.SolverLogger.info("Parallel jobs: " + numJobs);
        
        if (datFiles.length == 0) {
            appendToLog("WARNING: No .dat files found in the input directory.");
            return;
        }
        
        Path outDir = context.getOutDir();
        if (outDir == null || !java.nio.file.Files.exists(outDir) || !java.nio.file.Files.isDirectory(outDir)) {
            appendToLog("ERROR: Invalid output directory");
            return;
        }
        
        try {
            for (Path datPath : datFiles) {
                if (isCancelled.get()) {
                    break;
                }
                Runnable job = () -> {
                    if (isCancelled.get()) return;
                    String dat = datPath.toString();
                    String out = outDir.resolve(datPath.getFileName()).toString();
                    
                    try {
                        com.treloc.xtreloc.util.SolverLogger.fine("Processing: " + datPath.getFileName());
                        task.run(dat, out);
                    } catch (Exception e) {
                        appendToLog("ERROR: Failed to process " + datPath.getFileName());
                        appendToLog(getStackTrace(e));
                        logger.log(Level.SEVERE, "Failed to process file", e);
                    } finally {
                        int done = progress.incrementAndGet();
                        updateProgress(done, datFiles.length, datPath.getFileName().toString());
                    }
                };
                
                if (executor != null) {
                    executor.submit(job);
                } else {
                    job.run();
                }
            }
            
            if (executor != null) {
                if (isCancelled.get()) {
                    executor.shutdownNow();
                } else {
                    executor.shutdown();
                }
                try {
                    executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    executor.shutdownNow();
                }
            }
            
            if (isCancelled.get()) {
                lastBatchProcessedCount = progress.get();
                lastBatchOutDir = outDir;
                appendToLog("Cancelled. " + lastBatchProcessedCount + " of " + datFiles.length + " files processed.");
                return;
            }
            
            appendToLog("Completed: " + datFiles.length + " files processed");
            
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
    
    private void runTripleDiff(AppConfig configToUse) {
        try {
            com.treloc.xtreloc.util.SolverLogger.info("Starting triple difference relocation...");
            
            com.treloc.xtreloc.solver.HypoTripleDiff solver = 
                new com.treloc.xtreloc.solver.HypoTripleDiff(configToUse);
            solver.start("", "");
            
            com.treloc.xtreloc.util.SolverLogger.info("Triple difference relocation completed successfully.");
        } catch (Exception e) {
            appendToLog("ERROR: Triple difference relocation failed");
            appendToLog(getStackTrace(e));
            logger.log(Level.SEVERE, "Triple difference relocation failed", e);
            throw new RuntimeException("Triple difference relocation failed", e);
        }
    }
    
    private void runClustering(AppConfig configToUse) {
        try {
            com.treloc.xtreloc.util.SolverLogger.info("Starting spatial clustering...");
            
            com.treloc.xtreloc.solver.SpatialClustering clustering = 
                new com.treloc.xtreloc.solver.SpatialClustering(configToUse);
            clustering.start("", "");
            
            com.treloc.xtreloc.util.SolverLogger.info("Spatial clustering completed successfully.");
        } catch (Exception e) {
            appendToLog("ERROR: Clustering failed");
            appendToLog(getStackTrace(e));
            logger.log(Level.SEVERE, "Clustering failed", e);
            throw new RuntimeException("Clustering failed", e);
        }
    }
    
    private void runSyntheticTest(AppConfig configToUse) {
        try {
            com.treloc.xtreloc.util.SolverLogger.info("Starting synthetic test...");
            
            int randomSeed = 100;
            double phsErr = 0.1;
            double locErr = 0.03;
            double minSelectRate = 0.2;
            double maxSelectRate = 0.4;
            boolean addLocationPerturbation = true;
            
            AppConfig.ModeConfig synConfig = null;
            if (configToUse.modes != null && configToUse.modes.containsKey("SYN")) {
                synConfig = configToUse.modes.get("SYN");
                if (synConfig != null) {
                    if (synConfig.randomSeed != null) randomSeed = synConfig.randomSeed;
                    if (synConfig.phsErr != null) phsErr = synConfig.phsErr;
                    if (synConfig.locErr != null) locErr = synConfig.locErr;
                    if (synConfig.minSelectRate != null) minSelectRate = synConfig.minSelectRate;
                    if (synConfig.maxSelectRate != null) maxSelectRate = synConfig.maxSelectRate;
                }
            }
            
            com.treloc.xtreloc.solver.SyntheticTest syntheticTest = 
                new com.treloc.xtreloc.solver.SyntheticTest(configToUse, randomSeed, phsErr, locErr,
                    minSelectRate, maxSelectRate, addLocationPerturbation);
            
            syntheticTest.generateDataFromCatalog();
            
            com.treloc.xtreloc.util.SolverLogger.info("Synthetic test completed successfully.");
            
            if (synConfig != null && synConfig.outDirectory != null) {
                try {
                    generateCatalogFromDatFiles(synConfig.outDirectory, "SYN");
                } catch (Exception e) {
                    logger.warning("Failed to auto-generate catalog for SYN mode: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            appendToLog("ERROR: Synthetic test failed");
            appendToLog(getStackTrace(e));
            logger.log(Level.SEVERE, "Synthetic test failed", e);
            throw new RuntimeException("Synthetic test failed", e);
        }
    }
    
    private void generateCatalogFromDatFiles(Path outDir, String mode) throws IOException {
        Path[] outputDatFiles = FileScanner.scan(outDir);
        if (outputDatFiles.length == 0) {
            logger.warning("No .dat files found in output directory for catalog generation: " + outDir);
            return;
        }
        
        appendToLog("Generating catalog.csv from " + outputDatFiles.length + " .dat files...");
        
        java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> allHypocenters = new java.util.ArrayList<>();
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
        
        appendToLog("Catalog auto-generated: " + catalogFile.getAbsolutePath() + 
            " (" + allHypocenters.size() + " entries)");
    }
    
    private java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> loadHypocentersFromDatFile(
            Path datFile, Path catalogBaseDir) throws IOException {
        
        java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = new java.util.ArrayList<>();
        
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
    
    private String extractTimeFromFilename(String filename) {
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
    
    private String convertTimeToISO8601(String timeStr) {
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
    
    private void updateProgress(int current, int total, String fileName) {
        int percent = (int) (100.0 * current / total);
        
        // Log progress only (not detailed output)
        String progressMsg = String.format("Progress: %d%% (%d/%d files)", percent, current, total);
        
        // Update via SolverLogger so it goes to file only (not TUI display)
        com.treloc.xtreloc.util.SolverLogger.fine(progressMsg + " - " + fileName);
    }
    
    private void appendToLog(String message) {
        // Initialize buffer if not already done (safety check)
        if (logBuffer == null) {
            logBuffer = new ArrayList<>(MAX_LOG_LINES);
        }
        
        // Add to ring buffer (thread-safe operation)
        synchronized (logBuffer) {
            // Split multi-line messages
            String[] lines = message.split("\n");
            for (String line : lines) {
                if (line != null && !line.isEmpty()) {
                    logBuffer.add(line);
                    
                    // Remove oldest lines if buffer exceeds maximum
                    while (logBuffer.size() > MAX_LOG_LINES) {
                        logBuffer.remove(0);
                    }
                }
            }
        }
        
        // Update UI on GUI thread
        textGUI.getGUIThread().invokeLater(() -> {
            if (logWindow == null) {
                // Create log window
                logWindow = new BasicWindow("Log Output");
                logWindow.setHints(java.util.Arrays.asList(Window.Hint.NO_DECORATIONS));

                logWindow.addWindowListener(new WindowListenerAdapter() {
                    @Override
                    public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean hasBeenHandled) {
                        if (key.getKeyType() == KeyType.Escape) {
                            basePane.close();
                            textGUI.setActiveWindow(mainWindow);
                            hasBeenHandled.set(true);
                        }
                    }
                });

                Panel logPanel = new Panel();
                logPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
                
                Label logLabel = new Label("Log Output (Press ESC to close):");
                logLabel.setForegroundColor(TextColor.ANSI.YELLOW);
                logPanel.addComponent(logLabel);
                
                TerminalSize terminalSize = screen.getTerminalSize();
                int logCols = Math.max(60, terminalSize.getColumns() - 4);
                int logRows = Math.max(15, terminalSize.getRows() - 6);
                
                logArea = new TextBox(new TerminalSize(logCols, logRows), TextBox.Style.MULTI_LINE);
                logArea.setReadOnly(true);
                logPanel.addComponent(logArea);
                
                logWindow.setComponent(logPanel);
                
                // Position window (centered) - use CENTERED hint instead
                logWindow.setHints(java.util.Arrays.asList(Window.Hint.CENTERED));
                
                // Don't add window yet - will be shown when needed
            }
            
            if (logArea != null && logBuffer != null) {
                // Update TextBox with current buffer contents
                synchronized (logBuffer) {
                    if (logBuffer.isEmpty()) {
                        logArea.setText("");
                    } else {
                        // Limit the text to a reasonable size for TextBox
                        // TextBox can handle large text, but we'll keep it manageable
                        // by only showing the most recent lines if buffer is very large
                        int maxDisplayLines = 5000;  // Maximum lines to display in TextBox
                        String fullText;
                        
                        if (logBuffer.size() > maxDisplayLines) {
                            // Show only the most recent lines
                            int startIndex = logBuffer.size() - maxDisplayLines;
                            List<String> recentLines = new ArrayList<>(
                                logBuffer.subList(startIndex, logBuffer.size())
                            );
                            fullText = "... (" + (logBuffer.size() - maxDisplayLines) + " older lines) ...\n" + 
                                       String.join("\n", recentLines);
                        } else {
                            // Show all lines
                            fullText = String.join("\n", logBuffer);
                        }
                        
                        logArea.setText(fullText);
                    }
                }
                
                // Scroll to bottom
                String text = logArea.getText();
                if (text != null && !text.isEmpty()) {
                    logArea.setCaretPosition(text.length());
                }
                
                // Don't auto-show log window - user can open it manually via button
            } else {
                // Fallback: print to console
                System.out.println(message);
            }
        });
    }
    
    private void showLogWindow() {
        if (logWindow != null && logArea != null) {
            textGUI.getGUIThread().invokeLater(() -> {
                if (!textGUI.getWindows().contains(logWindow)) {
                    textGUI.addWindow(logWindow);
                }
            });
        }
    }
    
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    private void changeConfig() {
        BasicWindow window = new BasicWindow("Change Configuration File");
        window.setHints(java.util.Arrays.asList(Window.Hint.CENTERED));

        Panel contentPanel = new Panel();
        contentPanel.setLayoutManager(new GridLayout(2));

        contentPanel.addComponent(new Label("Configuration file path:"));
        TextBox pathBox = new TextBox(new TerminalSize(40, 1));
        if (configPath != null) {
            pathBox.setText(configPath);
        }
        contentPanel.addComponent(pathBox);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2));

        Button okButton = new Button("OK", () -> {
            textGUI.getGUIThread().invokeLater(() -> {
                try {
                    String newPath = pathBox.getText();
                    if (newPath != null && !newPath.trim().isEmpty()) {
                        ConfigLoader loader = new ConfigLoader(newPath.trim());
                        config = loader.getConfig();
                        configPath = newPath.trim();
                        appendToLog("Configuration loaded: " + configPath);
                    }
                } catch (Exception e) {
                    appendToLog("ERROR: " + e.getMessage());
                } finally {
                    window.close();
                }
            });
        });

        Button cancelButton = new Button("Cancel", () -> {
            window.close();
            textGUI.setActiveWindow(mainWindow);
            pathBox.takeFocus();
        });

        buttonPanel.addComponent(okButton);
        buttonPanel.addComponent(cancelButton);

        contentPanel.addComponent(new EmptySpace());
        contentPanel.addComponent(buttonPanel);

        window.setComponent(contentPanel);
        textGUI.addWindow(window);
    }
    
    private void switchToGUI() {
        appendToLog("Switching to GUI mode...");
        try {
            cleanup();
            String[] args = {};
            com.treloc.xtreloc.app.gui.XTreLocGUI.main(args);
        } catch (Exception e) {
            appendToLog("ERROR: Failed to start GUI mode: " + e.getMessage());
            appendToLog(getStackTrace(e));
        }
    }
    
    private void showExitDialog() {
        BasicWindow window = new BasicWindow("Exit");
        window.setHints(java.util.Arrays.asList(Window.Hint.CENTERED));

        Panel contentPanel = new Panel(new LinearLayout(Direction.VERTICAL));

        contentPanel.addComponent(new Label("Exiting xTreLoc TUI. Goodbye!"));
        contentPanel.addComponent(new EmptySpace());

        Button okButton = new Button("OK", () -> {
            window.close();
            mainWindow.close();
        });

        contentPanel.addComponent(okButton);
        window.setComponent(contentPanel);

        textGUI.addWindow(window);
    }
    
    private void cleanup() {
        try {
            if (screen != null) {
                screen.stopScreen();
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @FunctionalInterface
    private interface LocationTask {
        void run(String datFile, String outFile) throws Exception;
    }
}
