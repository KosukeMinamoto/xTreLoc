package com.treloc.xtreloc.app.tui;

// import java.nio.file.Path;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
// import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
// import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.treloc.xtreloc.io.AppConfig;

/**
 * Helper class for interactive configuration input in TUI mode using Lanterna.
 * Allows users to input all configuration parameters without requiring a config.json file.
 * 
 * @author K.Minamoto
 */
public class ConfigInputHelper {
    
    private final WindowBasedTextGUI gui;
    private final Panel leftPanel;
    private final Panel paramPanel;
    private final TextBox logArea;
    private AppConfig currentConfig;
    
    /** Per-mode form state cache (shared with TUI so "Back to Parameters" restores values). */
    private final Map<String, Map<String, String>> modeFormStateCache;
    
    public ConfigInputHelper(WindowBasedTextGUI gui, Panel leftPanel, Panel paramPanel, TextBox logArea) {
        this(gui, leftPanel, paramPanel, logArea, new HashMap<String, Map<String, String>>());
    }
    
    /**
     * @param modeFormStateCache shared cache so that when returning to parameter form (e.g. Back to Parameters) the same cache is used and values are restored. If null, a new map is used.
     */
    public ConfigInputHelper(WindowBasedTextGUI gui, Panel leftPanel, Panel paramPanel, TextBox logArea,
            Map<String, Map<String, String>> modeFormStateCache) {
        this.gui = gui;
        this.leftPanel = leftPanel;
        this.paramPanel = paramPanel;
        this.logArea = logArea;
        this.modeFormStateCache = modeFormStateCache != null ? modeFormStateCache : new HashMap<String, Map<String, String>>();
    }
    
    /**
     * Creates parameter input form in the left panel.
     * 
     * @param mode the mode for which to create configuration
     * @param onOk callback when OK is clicked (stores config and shows execution panel)
     * @param onCancel callback when Cancel is clicked
     * @param onRun optional callback when Run is clicked directly (if null, uses onOk)
     */
    public void createParameterForm(String mode, Runnable onOk, Runnable onCancel) {
        createParameterForm(mode, onOk, onCancel, null);
    }
    
    /**
     * Creates parameter input form in the left panel with optional Run button.
     * 
     * @param mode the mode for which to create configuration
     * @param onOk callback when OK is clicked (stores config and shows execution panel)
     * @param onCancel callback when Cancel is clicked
     * @param onRun optional callback when Run is clicked directly
     */
    public void createParameterForm(String mode, Runnable onOk, Runnable onCancel, Runnable onRun) {
        paramPanel.removeAllComponents();
        currentConfig = new AppConfig();
        
        paramPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        
        // Common parameters
        Label commonLabel = new Label("=== Common Parameters ===");
        commonLabel.setForegroundColor(TextColor.ANSI.YELLOW);
        paramPanel.addComponent(commonLabel);
        
        // Calculate text box width based on available space (use smaller size for small terminals)
        int textBoxWidth = Math.min(45, Math.max(30, gui.getScreen().getTerminalSize().getColumns() - 20));
        
        paramPanel.addComponent(new Label("Station file path *:"));
        TextBox stationFileBox = new TextBox(new TerminalSize(textBoxWidth, 1));
        paramPanel.addComponent(stationFileBox);
        
        paramPanel.addComponent(new Label("TauP velocity model (e.g., 'prem') *:"));
        TextBox taupFileBox = new TextBox(new TerminalSize(textBoxWidth, 1));
        paramPanel.addComponent(taupFileBox);
        
        paramPanel.addComponent(new Label("Hypocenter bottom depth (km):"));
        TextBox hypBottomBox = new TextBox(new TerminalSize(textBoxWidth, 1));
        hypBottomBox.setText("100.0");
        paramPanel.addComponent(hypBottomBox);
        
        paramPanel.addComponent(new Label("Threshold:"));
        TextBox thresholdBox = new TextBox(new TerminalSize(textBoxWidth, 1));
        thresholdBox.setText("0.0");
        paramPanel.addComponent(thresholdBox);
        
        paramPanel.addComponent(new Label("Number of parallel jobs:"));
        TextBox numJobsBox = new TextBox(new TerminalSize(textBoxWidth, 1));
        numJobsBox.setText("1");
        paramPanel.addComponent(numJobsBox);
        
        paramPanel.addComponent(new Label("Log level (INFO/DEBUG/WARNING/SEVERE):"));
        TextBox logLevelBox = new TextBox(new TerminalSize(textBoxWidth, 1));
        logLevelBox.setText("INFO");
        paramPanel.addComponent(logLevelBox);
        
        paramPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        
        // Mode-specific parameters
        Label modeLabel = new Label("=== Mode-Specific Parameters ===");
        modeLabel.setForegroundColor(TextColor.ANSI.YELLOW);
        paramPanel.addComponent(modeLabel);
        
        // Create mode-specific input fields
        List<Component> modeComponents = new ArrayList<>();
        
        switch (mode.toUpperCase()) {
            case "GRD":
            case "LMO":
            case "MCMC":
            case "DE":
                paramPanel.addComponent(new Label("Input .dat files directory *:"));
                TextBox datDirBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                paramPanel.addComponent(datDirBox);
                modeComponents.add(datDirBox);
                
                paramPanel.addComponent(new Label("Output directory *:"));
                TextBox outDirBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                paramPanel.addComponent(outDirBox);
                modeComponents.add(outDirBox);
                break;
                
            case "TRD":
                paramPanel.addComponent(new Label("Catalog file path *:"));
                TextBox catalogFileBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                paramPanel.addComponent(catalogFileBox);
                modeComponents.add(catalogFileBox);
                
                paramPanel.addComponent(new Label("Input .dat files directory *:"));
                TextBox trdDatDirBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                paramPanel.addComponent(trdDatDirBox);
                modeComponents.add(trdDatDirBox);
                
                paramPanel.addComponent(new Label("Output directory *:"));
                TextBox trdOutDirBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                paramPanel.addComponent(trdOutDirBox);
                modeComponents.add(trdOutDirBox);
                break;
                
            case "CLS":
                paramPanel.addComponent(new Label("Catalog file path *:"));
                TextBox clsCatalogBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                paramPanel.addComponent(clsCatalogBox);
                modeComponents.add(clsCatalogBox);
                
                paramPanel.addComponent(new Label("Output directory *:"));
                TextBox clsOutDirBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                paramPanel.addComponent(clsOutDirBox);
                modeComponents.add(clsOutDirBox);
                
                paramPanel.addComponent(new Label("Minimum points (minPts):"));
                TextBox minPtsBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                minPtsBox.setText("3");
                paramPanel.addComponent(minPtsBox);
                modeComponents.add(minPtsBox);
                
                paramPanel.addComponent(new Label("Epsilon distance (km, or -1 for auto):"));
                TextBox epsBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                epsBox.setText("30.0");
                paramPanel.addComponent(epsBox);
                modeComponents.add(epsBox);
                break;
                
            case "SYN":
                paramPanel.addComponent(new Label("Input catalog file path *:"));
                TextBox synCatalogBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                paramPanel.addComponent(synCatalogBox);
                modeComponents.add(synCatalogBox);
                
                paramPanel.addComponent(new Label("Output directory *:"));
                TextBox synOutDirBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                paramPanel.addComponent(synOutDirBox);
                modeComponents.add(synOutDirBox);
                
                paramPanel.addComponent(new Label("Random seed:"));
                TextBox randomSeedBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                randomSeedBox.setText("100");
                paramPanel.addComponent(randomSeedBox);
                modeComponents.add(randomSeedBox);
                
                paramPanel.addComponent(new Label("Phase error (s):"));
                TextBox phsErrBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                phsErrBox.setText("0.1");
                paramPanel.addComponent(phsErrBox);
                modeComponents.add(phsErrBox);
                
                paramPanel.addComponent(new Label("Location error (km):"));
                TextBox locErrBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                locErrBox.setText("0.03");
                paramPanel.addComponent(locErrBox);
                modeComponents.add(locErrBox);
                
                paramPanel.addComponent(new Label("Minimum selection rate:"));
                TextBox minSelectRateBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                minSelectRateBox.setText("0.2");
                paramPanel.addComponent(minSelectRateBox);
                modeComponents.add(minSelectRateBox);
                
                paramPanel.addComponent(new Label("Maximum selection rate:"));
                TextBox maxSelectRateBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                maxSelectRateBox.setText("0.4");
                paramPanel.addComponent(maxSelectRateBox);
                modeComponents.add(maxSelectRateBox);
                break;
        }
        
        paramPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        
        // Solver-specific parameters
        List<Component> solverComponents = new ArrayList<>();
        
        switch (mode.toUpperCase()) {
            case "GRD":
                Label solverLabel = new Label("=== Solver Parameters ===");
                solverLabel.setForegroundColor(TextColor.ANSI.YELLOW);
                paramPanel.addComponent(solverLabel);
                
                paramPanel.addComponent(new Label("Total grids for grid search:"));
                TextBox totalGridsBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                totalGridsBox.setText("300");
                paramPanel.addComponent(totalGridsBox);
                solverComponents.add(totalGridsBox);
                
                paramPanel.addComponent(new Label("Number of focus iterations:"));
                TextBox numFocusBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                numFocusBox.setText("3");
                paramPanel.addComponent(numFocusBox);
                solverComponents.add(numFocusBox);
                break;
                
            case "MCMC":
                Label mcmcSolverLabel = new Label("=== Solver Parameters ===");
                mcmcSolverLabel.setForegroundColor(TextColor.ANSI.YELLOW);
                paramPanel.addComponent(mcmcSolverLabel);
                
                paramPanel.addComponent(new Label("Number of MCMC samples:"));
                TextBox nSamplesBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                nSamplesBox.setText("1000");
                paramPanel.addComponent(nSamplesBox);
                solverComponents.add(nSamplesBox);
                
                paramPanel.addComponent(new Label("Burn-in samples:"));
                TextBox burnInBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                burnInBox.setText("200");
                paramPanel.addComponent(burnInBox);
                solverComponents.add(burnInBox);
                
                paramPanel.addComponent(new Label("Step size:"));
                TextBox stepSizeBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                stepSizeBox.setText("0.1");
                paramPanel.addComponent(stepSizeBox);
                solverComponents.add(stepSizeBox);
                
                paramPanel.addComponent(new Label("Temperature:"));
                TextBox temperatureBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                temperatureBox.setText("1.0");
                paramPanel.addComponent(temperatureBox);
                solverComponents.add(temperatureBox);
                break;
                
            case "DE":
                Label deSolverLabel = new Label("=== Solver Parameters ===");
                deSolverLabel.setForegroundColor(TextColor.ANSI.YELLOW);
                paramPanel.addComponent(deSolverLabel);
                
                paramPanel.addComponent(new Label("Population size:"));
                TextBox popSizeBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                popSizeBox.setText("20");
                paramPanel.addComponent(popSizeBox);
                solverComponents.add(popSizeBox);
                
                paramPanel.addComponent(new Label("Number of iterations:"));
                TextBox deIterBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                deIterBox.setText("100");
                paramPanel.addComponent(deIterBox);
                solverComponents.add(deIterBox);
                
                paramPanel.addComponent(new Label("Mutation factor (F):"));
                TextBox mutationFBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                mutationFBox.setText("0.8");
                paramPanel.addComponent(mutationFBox);
                solverComponents.add(mutationFBox);
                
                paramPanel.addComponent(new Label("Crossover probability (CR):"));
                TextBox crossoverBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                crossoverBox.setText("0.9");
                paramPanel.addComponent(crossoverBox);
                solverComponents.add(crossoverBox);
                break;
                
            case "TRD":
                Label trdSolverLabel = new Label("=== Solver Parameters ===");
                trdSolverLabel.setForegroundColor(TextColor.ANSI.YELLOW);
                paramPanel.addComponent(trdSolverLabel);
                
                paramPanel.addComponent(new Label("Iteration numbers (comma-separated, e.g., '10,10'):"));
                TextBox iterNumBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                iterNumBox.setText("10,10");
                paramPanel.addComponent(iterNumBox);
                solverComponents.add(iterNumBox);
                
                paramPanel.addComponent(new Label("Distance thresholds in km (e.g., '50,20'):"));
                TextBox distKmBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                distKmBox.setText("50,20");
                paramPanel.addComponent(distKmBox);
                solverComponents.add(distKmBox);
                
                paramPanel.addComponent(new Label("Damping factors (e.g., '0,1'):"));
                TextBox dampFactBox = new TextBox(new TerminalSize(textBoxWidth, 1));
                dampFactBox.setText("0,1");
                paramPanel.addComponent(dampFactBox);
                solverComponents.add(dampFactBox);
                break;
        }
        
        paramPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        
        // Restore previously entered values for this mode if any
        applyFormStateForMode(mode, stationFileBox, taupFileBox, hypBottomBox, thresholdBox, numJobsBox, logLevelBox, modeComponents, solverComponents);
        
        // Buttons
        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));
        
        // Helper method to validate and set config (checks empty, file existence, extensions)
        java.util.function.Supplier<Boolean> validateAndSetConfig = () -> {
            // Validate and set common parameters
            if (stationFileBox.getText().trim().isEmpty()) {
                appendToLog("ERROR: Station file path is required.");
                return false;
            }
            String stationPath = stationFileBox.getText().trim();
            File stationFile = new File(stationPath);
            if (!stationFile.exists()) {
                appendToLog("ERROR: Station file does not exist: " + stationPath);
                return false;
            }
            if (!stationFile.isFile()) {
                appendToLog("ERROR: Station path is not a file: " + stationPath);
                return false;
            }
            currentConfig.stationFile = stationPath;
            
            if (taupFileBox.getText().trim().isEmpty()) {
                appendToLog("ERROR: TauP velocity model file is required.");
                return false;
            }
            String taupPath = taupFileBox.getText().trim();
            // Built-in model names (prem, iasp91, etc.) or path to file
            if (!isBuiltinTaupModel(taupPath)) {
                File taupFile = new File(taupPath);
                if (!taupFile.exists()) {
                    appendToLog("ERROR: TauP model file does not exist: " + taupPath);
                    return false;
                }
                if (!taupFile.isFile()) {
                    appendToLog("ERROR: TauP model path is not a file: " + taupPath);
                    return false;
                }
                String ext = getFileExtension(taupPath);
                if (!ext.isEmpty() && !"nd".equalsIgnoreCase(ext) && !"taup".equalsIgnoreCase(ext) && !"tvel".equalsIgnoreCase(ext)) {
                    appendToLog("ERROR: TauP model file should have extension .nd, .taup or .tvel: " + taupPath);
                    return false;
                }
            }
            currentConfig.taupFile = taupPath;
            
            try {
                currentConfig.hypBottom = Double.parseDouble(hypBottomBox.getText().trim());
            } catch (NumberFormatException e) {
                currentConfig.hypBottom = 100.0;
            }
            
            try {
                currentConfig.threshold = Double.parseDouble(thresholdBox.getText().trim());
            } catch (NumberFormatException e) {
                currentConfig.threshold = 0.0;
            }
            
            try {
                currentConfig.numJobs = Integer.parseInt(numJobsBox.getText().trim());
            } catch (NumberFormatException e) {
                currentConfig.numJobs = 1;
            }
            
            currentConfig.logLevel = logLevelBox.getText().trim().isEmpty() ? "INFO" : logLevelBox.getText().trim();
            
            // Set mode-specific parameters
            currentConfig.modes = new HashMap<>();
            AppConfig.ModeConfig modeConfig = new AppConfig.ModeConfig();
            
            switch (mode.toUpperCase()) {
                case "GRD":
                case "LMO":
                case "MCMC":
                case "DE":
                    if (modeComponents.size() >= 2) {
                        TextBox datDir = (TextBox) modeComponents.get(0);
                        TextBox outDir = (TextBox) modeComponents.get(1);
                        if (datDir.getText().trim().isEmpty() || outDir.getText().trim().isEmpty()) {
                            appendToLog("ERROR: Input directory and output directory are required.");
                            return false;
                        }
                        File datDirFile = new File(datDir.getText().trim());
                        File outDirFile = new File(outDir.getText().trim());
                        if (!datDirFile.exists() || !datDirFile.isDirectory()) {
                            appendToLog("ERROR: Input directory does not exist or is not a directory: " + datDir.getText().trim());
                            return false;
                        }
                        if (!outDirFile.exists() || !outDirFile.isDirectory()) {
                            appendToLog("ERROR: Output directory does not exist or is not a directory: " + outDir.getText().trim());
                            return false;
                        }
                        modeConfig.datDirectory = Paths.get(datDir.getText().trim());
                        modeConfig.outDirectory = Paths.get(outDir.getText().trim());
                    }
                    break;
                    
                case "TRD":
                    if (modeComponents.size() >= 3) {
                        TextBox catalog = (TextBox) modeComponents.get(0);
                        TextBox datDir = (TextBox) modeComponents.get(1);
                        TextBox outDir = (TextBox) modeComponents.get(2);
                        if (catalog.getText().trim().isEmpty() || datDir.getText().trim().isEmpty() || 
                            outDir.getText().trim().isEmpty()) {
                            appendToLog("ERROR: Catalog file, input directory, and output directory are required.");
                            return false;
                        }
                        
                        // Validate catalog file exists and is a file
                        File catalogFile = new File(catalog.getText().trim());
                        if (!catalogFile.exists()) {
                            appendToLog("ERROR: Catalog file does not exist: " + catalog.getText().trim());
                            return false;
                        }
                        if (!catalogFile.isFile()) {
                            appendToLog("ERROR: Catalog path is not a file (it may be a directory): " + catalog.getText().trim());
                            return false;
                        }
                        
                        File trdDatDir = new File(datDir.getText().trim());
                        File trdOutDir = new File(outDir.getText().trim());
                        if (!trdDatDir.exists() || !trdDatDir.isDirectory()) {
                            appendToLog("ERROR: Input directory does not exist or is not a directory: " + datDir.getText().trim());
                            return false;
                        }
                        if (!trdOutDir.exists() || !trdOutDir.isDirectory()) {
                            appendToLog("ERROR: Output directory does not exist or is not a directory: " + outDir.getText().trim());
                            return false;
                        }
                        modeConfig.catalogFile = catalog.getText().trim();
                        modeConfig.datDirectory = Paths.get(datDir.getText().trim());
                        modeConfig.outDirectory = Paths.get(outDir.getText().trim());
                    }
                    break;
                    
                case "CLS":
                    if (modeComponents.size() >= 4) {
                        TextBox catalog = (TextBox) modeComponents.get(0);
                        TextBox outDir = (TextBox) modeComponents.get(1);
                        TextBox minPts = (TextBox) modeComponents.get(2);
                        TextBox eps = (TextBox) modeComponents.get(3);
                        if (catalog.getText().trim().isEmpty() || outDir.getText().trim().isEmpty()) {
                            appendToLog("ERROR: Catalog file and output directory are required.");
                            return false;
                        }
                        
                        // Validate catalog file exists and is a file
                        File catalogFile = new File(catalog.getText().trim());
                        if (!catalogFile.exists()) {
                            appendToLog("ERROR: Catalog file does not exist: " + catalog.getText().trim());
                            return false;
                        }
                        if (!catalogFile.isFile()) {
                            appendToLog("ERROR: Catalog path is not a file (it may be a directory): " + catalog.getText().trim());
                            return false;
                        }
                        
                        File clsOutDir = new File(outDir.getText().trim());
                        if (!clsOutDir.exists() || !clsOutDir.isDirectory()) {
                            appendToLog("ERROR: Output directory does not exist or is not a directory: " + outDir.getText().trim());
                            return false;
                        }
                        modeConfig.catalogFile = catalog.getText().trim();
                        modeConfig.outDirectory = Paths.get(outDir.getText().trim());
                        try {
                            modeConfig.minPts = Integer.parseInt(minPts.getText().trim());
                        } catch (NumberFormatException e) {
                            modeConfig.minPts = 3;
                        }
                        try {
                            modeConfig.eps = Double.parseDouble(eps.getText().trim());
                        } catch (NumberFormatException e) {
                            modeConfig.eps = 30.0;
                        }
                    }
                    break;
                    
                case "SYN":
                    if (modeComponents.size() >= 7) {
                        TextBox catalog = (TextBox) modeComponents.get(0);
                        TextBox outDir = (TextBox) modeComponents.get(1);
                        TextBox randomSeed = (TextBox) modeComponents.get(2);
                        TextBox phsErr = (TextBox) modeComponents.get(3);
                        TextBox locErr = (TextBox) modeComponents.get(4);
                        TextBox minSelectRate = (TextBox) modeComponents.get(5);
                        TextBox maxSelectRate = (TextBox) modeComponents.get(6);
                        if (catalog.getText().trim().isEmpty() || outDir.getText().trim().isEmpty()) {
                            appendToLog("ERROR: Catalog file and output directory are required.");
                            return false;
                        }
                        
                        // Validate catalog file exists and is a file
                        File catalogFile = new File(catalog.getText().trim());
                        if (!catalogFile.exists()) {
                            appendToLog("ERROR: Catalog file does not exist: " + catalog.getText().trim());
                            return false;
                        }
                        if (!catalogFile.isFile()) {
                            appendToLog("ERROR: Catalog path is not a file (it may be a directory): " + catalog.getText().trim());
                            return false;
                        }
                        File synOutDir = new File(outDir.getText().trim());
                        if (!synOutDir.exists() || !synOutDir.isDirectory()) {
                            appendToLog("ERROR: Output directory does not exist or is not a directory: " + outDir.getText().trim());
                            return false;
                        }
                        modeConfig.catalogFile = catalog.getText().trim();
                        modeConfig.outDirectory = Paths.get(outDir.getText().trim());
                        try {
                            modeConfig.randomSeed = Integer.parseInt(randomSeed.getText().trim());
                        } catch (NumberFormatException e) {
                            modeConfig.randomSeed = 100;
                        }
                        try {
                            modeConfig.phsErr = Double.parseDouble(phsErr.getText().trim());
                        } catch (NumberFormatException e) {
                            modeConfig.phsErr = 0.1;
                        }
                        try {
                            modeConfig.locErr = Double.parseDouble(locErr.getText().trim());
                        } catch (NumberFormatException e) {
                            modeConfig.locErr = 0.03;
                        }
                        try {
                            modeConfig.minSelectRate = Double.parseDouble(minSelectRate.getText().trim());
                        } catch (NumberFormatException e) {
                            modeConfig.minSelectRate = 0.2;
                        }
                        try {
                            modeConfig.maxSelectRate = Double.parseDouble(maxSelectRate.getText().trim());
                        } catch (NumberFormatException e) {
                            modeConfig.maxSelectRate = 0.4;
                        }
                    }
                    break;
            }
            
            currentConfig.modes.put(mode, modeConfig);
            
            // Set solver-specific parameters
            currentConfig.solver = new HashMap<>();
            ObjectMapper mapper = new ObjectMapper();
            
            switch (mode.toUpperCase()) {
                case "GRD":
                    if (solverComponents.size() >= 2) {
                        ObjectNode grdSolver = mapper.createObjectNode();
                        try {
                            grdSolver.put("totalGrids", Integer.parseInt(((TextBox) solverComponents.get(0)).getText().trim()));
                        } catch (NumberFormatException e) {
                            grdSolver.put("totalGrids", 300);
                        }
                        try {
                            grdSolver.put("numFocus", Integer.parseInt(((TextBox) solverComponents.get(1)).getText().trim()));
                        } catch (NumberFormatException e) {
                            grdSolver.put("numFocus", 3);
                        }
                        currentConfig.solver.put("GRD", grdSolver);
                    }
                    break;
                    
                case "MCMC":
                    if (solverComponents.size() >= 4) {
                        ObjectNode mcmcSolver = mapper.createObjectNode();
                        try {
                            mcmcSolver.put("nSamples", Integer.parseInt(((TextBox) solverComponents.get(0)).getText().trim()));
                        } catch (NumberFormatException e) {
                            mcmcSolver.put("nSamples", 1000);
                        }
                        try {
                            mcmcSolver.put("burnIn", Integer.parseInt(((TextBox) solverComponents.get(1)).getText().trim()));
                        } catch (NumberFormatException e) {
                            mcmcSolver.put("burnIn", 200);
                        }
                        try {
                            mcmcSolver.put("stepSize", Double.parseDouble(((TextBox) solverComponents.get(2)).getText().trim()));
                        } catch (NumberFormatException e) {
                            mcmcSolver.put("stepSize", 0.1);
                        }
                        try {
                            mcmcSolver.put("temperature", Double.parseDouble(((TextBox) solverComponents.get(3)).getText().trim()));
                        } catch (NumberFormatException e) {
                            mcmcSolver.put("temperature", 1.0);
                        }
                        currentConfig.solver.put("MCMC", mcmcSolver);
                    }
                    break;
                    
                case "DE":
                    if (solverComponents.size() >= 4) {
                        ObjectNode deSolver = mapper.createObjectNode();
                        try {
                            deSolver.put("populationSize", Integer.parseInt(((TextBox) solverComponents.get(0)).getText().trim()));
                        } catch (NumberFormatException e) {
                            deSolver.put("populationSize", 20);
                        }
                        try {
                            deSolver.put("iterationNumber", Integer.parseInt(((TextBox) solverComponents.get(1)).getText().trim()));
                        } catch (NumberFormatException e) {
                            deSolver.put("iterationNumber", 100);
                        }
                        try {
                            deSolver.put("mutationFactor", Double.parseDouble(((TextBox) solverComponents.get(2)).getText().trim()));
                        } catch (NumberFormatException e) {
                            deSolver.put("mutationFactor", 0.8);
                        }
                        try {
                            deSolver.put("crossoverProbability", Double.parseDouble(((TextBox) solverComponents.get(3)).getText().trim()));
                        } catch (NumberFormatException e) {
                            deSolver.put("crossoverProbability", 0.9);
                        }
                        currentConfig.solver.put("DE", deSolver);
                    }
                    break;
                    
                case "TRD":
                    if (solverComponents.size() >= 3) {
                        ObjectNode trdSolver = mapper.createObjectNode();
                        String iterNumStr = ((TextBox) solverComponents.get(0)).getText().trim();
                        ArrayNode iterNumArray = mapper.createArrayNode();
                        for (String val : iterNumStr.split(",")) {
                            try {
                                iterNumArray.add(Integer.parseInt(val.trim()));
                            } catch (NumberFormatException e) {
                                iterNumArray.add(10);
                            }
                        }
                        trdSolver.set("iterNum", iterNumArray);
                        
                        String distKmStr = ((TextBox) solverComponents.get(1)).getText().trim();
                        ArrayNode distKmArray = mapper.createArrayNode();
                        for (String val : distKmStr.split(",")) {
                            try {
                                distKmArray.add(Integer.parseInt(val.trim()));
                            } catch (NumberFormatException e) {
                                distKmArray.add(50);
                            }
                        }
                        trdSolver.set("distKm", distKmArray);
                        
                        String dampFactStr = ((TextBox) solverComponents.get(2)).getText().trim();
                        ArrayNode dampFactArray = mapper.createArrayNode();
                        for (String val : dampFactStr.split(",")) {
                            try {
                                dampFactArray.add(Integer.parseInt(val.trim()));
                            } catch (NumberFormatException e) {
                                dampFactArray.add(0);
                            }
                        }
                        trdSolver.set("dampFact", dampFactArray);
                        
                        trdSolver.put("lsqrAtol", 1e-6);
                        trdSolver.put("lsqrBtol", 1e-6);
                        trdSolver.put("lsqrConlim", 1e8);
                        trdSolver.put("lsqrIterLim", 1000);
                        trdSolver.put("lsqrShowLog", true);
                        
                        currentConfig.solver.put("TRD", trdSolver);
                    }
                    break;
            }
            
            appendToLog("Configuration parameters set successfully.");
            return true;
        };
        
        Button okButton = new Button("OK", () -> {
            if (validateAndSetConfig.get()) {
                saveFormStateForMode(mode, stationFileBox, taupFileBox, hypBottomBox, thresholdBox, numJobsBox, logLevelBox, modeComponents, solverComponents);
                onOk.run();
            }
        });
        
        Button cancelButton = new Button("Cancel", () -> {
            onCancel.run();
        });
        
        buttonPanel.addComponent(okButton);
        buttonPanel.addComponent(cancelButton);
        
        paramPanel.addComponent(buttonPanel);
        
        // Add param panel to left panel
        leftPanel.removeAllComponents();
        leftPanel.addComponent(paramPanel);
    }
    
    public AppConfig getCurrentConfig() {
        return currentConfig;
    }
    
    private void saveFormStateForMode(String mode, TextBox stationFileBox, TextBox taupFileBox, TextBox hypBottomBox,
            TextBox thresholdBox, TextBox numJobsBox, TextBox logLevelBox,
            List<Component> modeComponents, List<Component> solverComponents) {
        Map<String, String> state = new HashMap<>();
        state.put("stationFile", stationFileBox != null ? stationFileBox.getText() : "");
        state.put("taupFile", taupFileBox != null ? taupFileBox.getText() : "");
        state.put("hypBottom", hypBottomBox != null ? hypBottomBox.getText() : "");
        state.put("threshold", thresholdBox != null ? thresholdBox.getText() : "");
        state.put("numJobs", numJobsBox != null ? numJobsBox.getText() : "");
        state.put("logLevel", logLevelBox != null ? logLevelBox.getText() : "");
        if (modeComponents != null) {
            for (int i = 0; i < modeComponents.size(); i++) {
                Component c = modeComponents.get(i);
                if (c instanceof TextBox) state.put("mode" + i, ((TextBox) c).getText());
            }
        }
        if (solverComponents != null) {
            for (int i = 0; i < solverComponents.size(); i++) {
                Component c = solverComponents.get(i);
                if (c instanceof TextBox) state.put("solver" + i, ((TextBox) c).getText());
            }
        }
        modeFormStateCache.put(mode, state);
    }
    
    private void applyFormStateForMode(String mode, TextBox stationFileBox, TextBox taupFileBox, TextBox hypBottomBox,
            TextBox thresholdBox, TextBox numJobsBox, TextBox logLevelBox,
            List<Component> modeComponents, List<Component> solverComponents) {
        Map<String, String> state = modeFormStateCache.get(mode);
        if (state == null || state.isEmpty()) return;
        if (stationFileBox != null && state.containsKey("stationFile")) stationFileBox.setText(state.get("stationFile"));
        if (taupFileBox != null && state.containsKey("taupFile")) taupFileBox.setText(state.get("taupFile"));
        if (hypBottomBox != null && state.containsKey("hypBottom")) hypBottomBox.setText(state.get("hypBottom"));
        if (thresholdBox != null && state.containsKey("threshold")) thresholdBox.setText(state.get("threshold"));
        if (numJobsBox != null && state.containsKey("numJobs")) numJobsBox.setText(state.get("numJobs"));
        if (logLevelBox != null && state.containsKey("logLevel")) logLevelBox.setText(state.get("logLevel"));
        if (modeComponents != null) {
            for (int i = 0; i < modeComponents.size(); i++) {
                String v = state.get("mode" + i);
                if (v != null) {
                    Component c = modeComponents.get(i);
                    if (c instanceof TextBox) ((TextBox) c).setText(v);
                }
            }
        }
        if (solverComponents != null) {
            for (int i = 0; i < solverComponents.size(); i++) {
                String v = state.get("solver" + i);
                if (v != null) {
                    Component c = solverComponents.get(i);
                    if (c instanceof TextBox) ((TextBox) c).setText(v);
                }
            }
        }
    }
    
    private static boolean isBuiltinTaupModel(String name) {
        if (name == null || name.isEmpty()) return false;
        String n = name.trim().toLowerCase();
        return "prem".equals(n) || "iasp91".equals(n) || "ak135".equals(n) || "ak135f".equals(n);
    }
    
    private static String getFileExtension(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('.');
        if (i >= 0 && i < path.length() - 1) return path.substring(i + 1).trim();
        return "";
    }
    
    private void appendToLog(String message) {
        if (logArea == null) {
            // Log area not available, print to console
            System.out.println(message);
            return;
        }
        
        gui.getGUIThread().invokeLater(() -> {
            if (logArea != null) {
                String currentText = logArea.getText();
                if (currentText.isEmpty()) {
                    logArea.setText(message);
                } else {
                    logArea.setText(currentText + "\n" + message);
                }
                logArea.setCaretPosition(logArea.getText().length());
            } else {
                System.out.println(message);
            }
        });
    }
}
