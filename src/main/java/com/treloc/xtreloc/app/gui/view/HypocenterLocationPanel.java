package com.treloc.xtreloc.app.gui.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.ModeConfigResolver;
import com.treloc.xtreloc.io.VelocityModelCatalog;
import com.treloc.xtreloc.util.ExecutionFailureReporter;
import com.treloc.xtreloc.util.JsonMapperHolder;
import com.treloc.xtreloc.solver.HypoGridSearch;
import com.treloc.xtreloc.solver.HypoStationPairDiff;
import com.treloc.xtreloc.solver.SyntheticTest;
import com.treloc.xtreloc.solver.ConvergenceCallback;
import com.treloc.xtreloc.util.BatchExecutorFactory;
import com.treloc.xtreloc.util.ModeNameMapper;
import com.treloc.xtreloc.util.CatalogFileNameGenerator;
import com.treloc.xtreloc.util.SolverLogger;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.BundledImageLoader;
import com.treloc.xtreloc.app.gui.util.SolverBatchSummary;
import com.treloc.xtreloc.solver.SolverRunMetrics;
import com.treloc.xtreloc.solver.SolverRunMetricsContext;
import com.treloc.xtreloc.app.gui.util.UiFonts;
import com.treloc.xtreloc.app.gui.service.CatalogLoader;

import java.util.logging.Level;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.DefaultComboBoxModel;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Main panel for hypocenter location in the Solver tab.
 * <p>
 * Provides UI to configure and run location algorithms: grid search (GRD),
 * Levenberg-Marquardt (LMO), MCMC, differential evolution (DE), triple difference (TRD),
 * spatial clustering (CLS), and synthetic test (SYN). Manages input/output paths,
 * mode-specific parameters, execution log, and a full-bleed residual / k-distance chart in the
 * main window's right column when the Solver tab is active (SYN hides the chart). Per-run metrics are
 * appended to the Execution log as a TSV table, not per-iteration residual lines.
 *
 * @see HypoGridSearch
 * @see HypoStationPairDiff
 * @see HypoMCMC
 * @see HypoDifferentialEvolution
 * @see HypoTripleDiff
 * @see SpatialClustering
 * @see SyntheticTest
 */
public class HypocenterLocationPanel extends JPanel {
    private static final Logger logger = Logger.getLogger(HypocenterLocationPanel.class.getName());

    private static final Color CONVERGENCE_DOCK_BG = new Color(20, 20, 30);
    
    private final MapView mapView;
    /** When set, Solver execution result is added to this panel's catalog list (Viewer). */
    private CatalogTablePanel solverResultCatalogPanel;
    private AppConfig config;
    
    private JTextField targetDirField;
    private JButton selectDirButton;
    private JComboBox<String> modeCombo;
    private JComboBox<String> taupModelCombo;
    private JLabel taupModelLabel;
    private JLabel targetDirLabel;
    private JTextField totalGridsField;
    private JTextField numFocusField;
    private JTextField numJobsField;
    private JTextField thresholdField;
    private JTextField hypBottomField;
    private JTextField stationFileField;
    private JButton selectStationButton;
    private JTextField outputDirField;
    private JButton selectOutputDirButton;
    private JTextField outputFileField;
    private JButton selectOutputFileButton;
    private JTextField configExportField;
    private JButton selectConfigExportButton;
    private JLabel outputDirLabel;
    private JLabel outputFileLabel;
    private JLabel configExportLabel;
    private JButton executeButton;
    private JButton cancelButton;
    private JTextField importJsonFileField;
    private JButton selectImportJsonButton;
    private JButton importJsonButton;
    private JTextPane logArea;
    private JPanel logPanel;
    private JScrollPane logScrollPane;
    private JLabel logCommentLabel;
    private JPanel convergenceLogPanel;
    private JLabel convergenceLogCommentLabel;
    private JPanel solverConvergenceHostPanel;
    private ResidualPlotPanel residualPlotPanel;
    private ConvergenceCallback convergenceCallback;
    private JFileChooser fileChooser;
    private File selectedTargetDir;
    private File selectedStationFile;
    private File selectedOutputDir;
    private File selectedTaupFile;
    private SwingWorker<Void, String> currentWorker;
    
    private JTextField catalogFileField;
    private JButton selectCatalogButton;
    private JLabel catalogFileLabel;
    private JTextField randomSeedField;
    private JTextField phsErrField;
    private JTextField locErrField;
    private JTextField minSelectRateField;
    private JTextField maxSelectRateField;
    
    private JTextField nSamplesField;
    private JTextField burnInField;
    private JTextField stepSizeField;
    private JTextField stepSizeDepthField;
    private JTextField temperatureField;
    
    private JTextField minPtsField;
    private JTextField epsField;
    private JTextField epsPercentileField;
    private JTextField rmsThresholdField;
    private JTextField locErrThresholdField;
    
    private JTextField trdIterNumField;
    private JTextField trdDistKmField;
    private JTextField trdDampFactField;
    private JTextField trdMaxTripleDiffCountField;
    
    private JTextField lsqrAtolField;
    private JTextField lsqrBtolField;
    private JTextField lsqrConlimField;
    private JTextField lsqrIterLimField;
    private JCheckBox lsqrShowLogCheckBox;
    private JCheckBox lsqrCalcVarCheckBox;
    private JCheckBox showConvergenceLogCheckBox;
    
    private JTextField lmInitialStepBoundField;
    private JTextField lmCostRelativeToleranceField;
    private JTextField lmParRelativeToleranceField;
    private JTextField lmOrthoToleranceField;
    private JTextField lmMaxEvaluationsField;
    private JTextField lmMaxIterationsField;
    
    private JTextField dePopulationSizeField;
    private JTextField deMaxGenerationsField;
    private JTextField deScalingFactorField;
    private JTextField deCrossoverRateField;
    
    private JPanel parameterPanel;
    private JTable parameterTable;
    private javax.swing.table.DefaultTableModel parameterTableModel;
    private JButton parameterHelpButton;
    
    private JPanel leftPanel;
    private JPanel inputDataPanel;
    
    private final Map<String, Map<String, String>> modeParameterCache = new HashMap<>();
    private String lastSelectedMode = "GRD";
    private final java.util.List<Runnable> solverModeChangeListeners = new java.util.ArrayList<>();
    
    public HypocenterLocationPanel(MapView mapView) {
        this.mapView = mapView;
        this.fileChooser = new JFileChooser();
        initComponents();
        
        com.treloc.xtreloc.app.gui.util.SharedFileManager sharedManager = 
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance();
        
        sharedManager.addStationFileListener(file -> {
            if (file != null) {
                selectedStationFile = file;
                stationFileField.setText(file.getAbsolutePath());
                updateExecuteButtonState();
                appendLog("Station file updated: " + file.getAbsolutePath());
            }
        });
        
        sharedManager.addTaupFileListener(file -> {
            if (file != null && taupModelCombo != null) {
                taupModelCombo.setSelectedItem(file);
            }
        });
    }

    /**
     * Sets the catalog panel (Viewer) to which Solver execution results are added.
     * When set, after a successful run the result is added to the catalog list and reflected on the map.
     */
    public void setSolverResultCatalogPanel(CatalogTablePanel catalogPanel) {
        this.solverResultCatalogPanel = catalogPanel;
    }

    /**
     * Current solver mode abbreviation (GRD, LMO, …, SYN), for UI such as Viewer layout.
     */
    public String getSolverModeAbbreviation() {
        return getSelectedModeAbbreviation();
    }

    /**
     * Notified after the mode combo selection changes (same EDT).
     */
    public void addSolverModeChangeListener(Runnable listener) {
        if (listener != null) {
            solverModeChangeListeners.add(listener);
        }
    }

    private void fireSolverModeChangeListeners() {
        for (Runnable r : new java.util.ArrayList<>(solverModeChangeListeners)) {
            try {
                r.run();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Solver mode change listener failed", e);
            }
        }
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("Hypocenter Location"));
        setBackground(AppPanelStyle.getPanelBg());
        
        leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBackground(AppPanelStyle.getPanelBg());
        
        final int PANEL_WIDTH = 450;
        final int maxWidth = Short.MAX_VALUE;
        
        JPanel modePanel = createModePanel();
        modePanel.setPreferredSize(new Dimension(PANEL_WIDTH, 70));
        modePanel.setMaximumSize(new Dimension(maxWidth, 70));
        leftPanel.add(modePanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        inputDataPanel = createInputDataPanel();
        inputDataPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 300));
        inputDataPanel.setMaximumSize(new Dimension(maxWidth, 300));
        leftPanel.add(inputDataPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        JPanel paramPanel = createParameterPanel();
        paramPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 280));
        paramPanel.setMaximumSize(new Dimension(maxWidth, 280));
        leftPanel.add(paramPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        JPanel outputPanel = createOutputPanel();
        outputPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 180));
        outputPanel.setMaximumSize(new Dimension(maxWidth, 180));
        leftPanel.add(outputPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        JPanel buttonPanel = createButtonPanel();
        leftPanel.add(buttonPanel);
        
        AppPanelStyle.applyThemeTo(leftPanel);
        
        logPanel = createLogPanel();
        
        residualPlotPanel = new ResidualPlotPanel();
        residualPlotPanel.setMinimumSize(new Dimension(200, 120));
        setupConvergenceCallback();
        
        convergenceLogPanel = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(convergenceLogPanel);
        convergenceLogPanel.setOpaque(true);
        CardLayout convergenceCardLayout = new CardLayout();
        JPanel convergenceContentPanel = new JPanel(convergenceCardLayout);
        convergenceContentPanel.setMinimumSize(new Dimension(200, 150));
        convergenceContentPanel.setBackground(CONVERGENCE_DOCK_BG);
        convergenceContentPanel.setOpaque(true);
        
        JPanel plotOnlyPanel = new JPanel(new BorderLayout());
        plotOnlyPanel.setOpaque(true);
        plotOnlyPanel.setBackground(CONVERGENCE_DOCK_BG);
        plotOnlyPanel.add(residualPlotPanel, BorderLayout.CENTER);
        convergenceContentPanel.add(plotOnlyPanel, "PLOT_ONLY");
        
        convergenceLogCommentLabel = new JLabel(
            "<html><div style='text-align: left; padding: 20px; color: #c9d1e0;'>" +
            "<div style='font-size: 18px; font-weight: bold;'>Chart panel is hidden</div>" +
            "<div style='margin-top: 8px;'>Enable &quot;Show convergence chart&quot; to show the residual chart here. " +
            "Per-run totals appear as a tab-separated summary at the end of the Execution log.</div>" +
            "</div></html>"
        );
        convergenceLogCommentLabel.setHorizontalAlignment(SwingConstants.LEFT);
        convergenceLogCommentLabel.setVerticalAlignment(SwingConstants.CENTER);
        convergenceLogCommentLabel.setFont(UiFonts.uiPlain(14f));
        convergenceLogCommentLabel.setMinimumSize(new Dimension(200, 100));
        convergenceContentPanel.add(convergenceLogCommentLabel, "MESSAGE");
        
        convergenceLogPanel.add(convergenceContentPanel, BorderLayout.CENTER);
        convergenceLogPanel.setMinimumSize(new Dimension(200, 150));
        convergenceLogPanel.setBackground(CONVERGENCE_DOCK_BG);
        
        logPanel.setVisible(true);
        logPanel.setOpaque(true);
        updateLogPanelVisibility(true);
        
        convergenceLogPanel.setVisible(true);
        convergenceLogPanel.setOpaque(true);
        residualPlotPanel.setVisible(true);
        residualPlotPanel.setOpaque(true);
        
        solverConvergenceHostPanel = new JPanel(new BorderLayout());
        solverConvergenceHostPanel.setOpaque(true);
        solverConvergenceHostPanel.setBackground(CONVERGENCE_DOCK_BG);
        solverConvergenceHostPanel.add(convergenceLogPanel, BorderLayout.CENTER);
        
        loadLogHistory();
        
        updateParameterFields();
        updateExecuteButtonState();
        
        if (modeCombo != null && residualPlotPanel != null) {
            String initialMode = getSelectedModeAbbreviation();
            if (initialMode != null) {
                residualPlotPanel.setMode(initialMode);
                updateConvergenceLogForMode(initialMode);
            }
        }
    }
    
    public JComponent getLeftPanel() {
        JScrollPane scrollPane = new JScrollPane(leftPanel);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }
    
    public JPanel getLogPanel() {
        return logPanel;
    }
    
    public JPanel getConvergenceDockPanel() {
        return solverConvergenceHostPanel;
    }
    
    private void updateLayoutForMode(String mode) {
        if (inputDataPanel != null) {
            updateInputDataPanelForMode(mode);
        }
        updateConvergenceLogForMode(mode);
    }

    private void showConvergenceCard(String cardName) {
        if (convergenceLogPanel == null) {
            return;
        }
        for (Component comp : convergenceLogPanel.getComponents()) {
            if (comp instanceof JPanel panel) {
                LayoutManager layout = panel.getLayout();
                if (layout instanceof CardLayout cardLayout) {
                    cardLayout.show(panel, cardName);
                    return;
                }
            }
        }
    }

    /**
     * Per-iteration residual/sample lines are not copied to the Execution log (they explode volume for GRD/MCMC/DE).
     * Charts still update live when the convergence panel is visible; runs end with a {@link SolverBatchSummary} TSV block.
     */
    private boolean shouldStreamConvergenceToExecutionLog() {
        return false;
    }

    private void updateConvergenceLogForMode(String mode) {
        if (convergenceLogPanel == null) {
            return;
        }

        boolean isSynMode = "SYN".equals(mode);

        if (isSynMode) {
            convergenceLogPanel.setMinimumSize(new Dimension(0, 0));
            convergenceLogPanel.setVisible(false);
            if (solverConvergenceHostPanel != null) {
                solverConvergenceHostPanel.setVisible(false);
            }
        } else {
            if (solverConvergenceHostPanel != null) {
                solverConvergenceHostPanel.setVisible(true);
            }
            convergenceLogPanel.setVisible(true);
            convergenceLogPanel.setMinimumSize(new Dimension(200, 150));
            if (residualPlotPanel != null) {
                residualPlotPanel.setMode(mode);
            }
            showConvergenceCard("PLOT_ONLY");
        }

        convergenceLogPanel.revalidate();
        convergenceLogPanel.repaint();
        if (solverConvergenceHostPanel != null) {
            solverConvergenceHostPanel.revalidate();
            solverConvergenceHostPanel.repaint();
        }
    }
    
    private void updateInputDataPanelForMode(String mode) {
        boolean isSynOrClsMode = "SYN".equals(mode) || "CLS".equals(mode);
        boolean isTrdMode = "TRD".equals(mode);
        boolean isSynOrClsOrTrdMode = isSynOrClsMode || isTrdMode;
        boolean isOtherMode = !isSynOrClsMode;
        
        if (targetDirField != null) {
            targetDirField.setEnabled(isOtherMode || isTrdMode);
            if (!(isOtherMode || isTrdMode)) {
                targetDirField.setBackground(AppPanelStyle.getDisabledContentBg());
                targetDirField.setForeground(AppPanelStyle.getDisabledContentFg());
            } else {
                AppPanelStyle.styleTextField(targetDirField);
            }
        }
        if (selectDirButton != null) {
            selectDirButton.setEnabled(isOtherMode || isTrdMode);
        }
        if (targetDirLabel != null) {
            targetDirLabel.setEnabled(isOtherMode || isTrdMode);
            if (!(isOtherMode || isTrdMode)) {
                targetDirLabel.setForeground(AppPanelStyle.getDisabledContentFg());
            } else {
                targetDirLabel.setForeground(AppPanelStyle.getContentTextColor());
            }
        }
        
        if (catalogFileField != null) {
            catalogFileField.setEnabled(isSynOrClsOrTrdMode);
            if (!isSynOrClsOrTrdMode) {
                catalogFileField.setBackground(AppPanelStyle.getDisabledContentBg());
                catalogFileField.setForeground(AppPanelStyle.getDisabledContentFg());
            } else {
                AppPanelStyle.styleTextField(catalogFileField);
            }
        }
        if (selectCatalogButton != null) {
            selectCatalogButton.setEnabled(isSynOrClsOrTrdMode);
        }
        if (catalogFileLabel != null) {
            catalogFileLabel.setEnabled(isSynOrClsOrTrdMode);
            if (!isSynOrClsOrTrdMode) {
                catalogFileLabel.setForeground(AppPanelStyle.getDisabledContentFg());
            } else {
                catalogFileLabel.setForeground(AppPanelStyle.getContentTextColor());
            }
        }
        
        boolean isClsMode = "CLS".equals(mode);
        if (taupModelCombo != null) {
            taupModelCombo.setEnabled(!isClsMode);
            if (isClsMode) {
                taupModelCombo.setBackground(AppPanelStyle.getDisabledContentBg());
                taupModelCombo.setForeground(AppPanelStyle.getDisabledContentFg());
            } else {
                AppPanelStyle.styleComboBox(taupModelCombo);
            }
        }
        if (taupModelLabel != null) {
            taupModelLabel.setEnabled(!isClsMode);
            if (isClsMode) {
                taupModelLabel.setForeground(AppPanelStyle.getDisabledContentFg());
            } else {
                taupModelLabel.setForeground(AppPanelStyle.getContentTextColor());
            }
        }
        
        updateOutputPanelForMode(mode);
    }
    
    private void updateOutputPanelForMode(String mode) {
        if (outputDirField != null) {
            outputDirField.setEnabled(true);
        }
        if (selectOutputDirButton != null) {
            selectOutputDirButton.setEnabled(true);
        }
        if (outputDirLabel != null) {
            outputDirLabel.setEnabled(true);
        }
        
        if (outputFileField != null) {
            outputFileField.setEnabled(true);
        }
        if (selectOutputFileButton != null) {
            selectOutputFileButton.setEnabled(true);
        }
        if (outputFileLabel != null) {
            outputFileLabel.setEnabled(true);
        }
        if (configExportField != null) {
            configExportField.setEnabled(true);
        }
        if (selectConfigExportButton != null) {
            selectConfigExportButton.setEnabled(true);
        }
        if (configExportLabel != null) {
            configExportLabel.setEnabled(true);
        }
    }
    
    private JPanel createInputDataPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(panel);
        panel.setBorder(AppPanelStyle.createSectionBorder("Input Data"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(AppPanelStyle.GAP, AppPanelStyle.GAP + 2, AppPanelStyle.GAP, AppPanelStyle.GAP + 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Import (Json):"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        importJsonFileField = new JTextField();
        importJsonFileField.setEditable(true);
        importJsonFileField.setHorizontalAlignment(JTextField.LEFT);
        AppPanelStyle.styleTextField(importJsonFileField);
        selectImportJsonButton = new JButton();
        try {
            Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
            if (fileIcon != null) {
                selectImportJsonButton.setIcon(fileIcon);
            } else {
                selectImportJsonButton.setText("📄");
            }
        } catch (Exception e) {
            selectImportJsonButton.setText("📄");
        }
        selectImportJsonButton.setToolTipText("Select config file (JSON)");
        AppPanelStyle.stylePrimaryButton(selectImportJsonButton);
        selectImportJsonButton.addActionListener(e -> selectImportJsonFile());
        importJsonButton = new JButton();
        ImageIcon applyIcon = BundledImageLoader.loadImageIcon("Check.png");
        if (applyIcon != null && applyIcon.getIconWidth() > 0) {
            Image scaled = applyIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            importJsonButton.setIcon(new ImageIcon(scaled));
            importJsonButton.setText(null);
        } else {
            importJsonButton.setText("Load");
            importJsonButton.setFont(UiFonts.uiBold(12f));
        }
        importJsonButton.setToolTipText("Apply configuration from JSON");
        importJsonButton.setPreferredSize(new Dimension(28, 26));
        AppPanelStyle.stylePrimaryButton(importJsonButton);
        importJsonButton.addActionListener(e -> importConfigFromJson());
        JPanel importJsonPanel = new JPanel(new BorderLayout());
        importJsonPanel.add(selectImportJsonButton, BorderLayout.WEST);
        importJsonPanel.add(importJsonFileField, BorderLayout.CENTER);
        JPanel importJsonEast = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 0));
        importJsonEast.add(importJsonButton);
        importJsonPanel.add(importJsonEast, BorderLayout.EAST);
        panel.add(importJsonPanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 1;
        targetDirLabel = new JLabel("Directory:");
        panel.add(targetDirLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        targetDirField = new JTextField();
        targetDirField.setEditable(true);
        targetDirField.setHorizontalAlignment(JTextField.LEFT);
        AppPanelStyle.styleTextField(targetDirField);
        targetDirField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { syncSelectedTargetDirFromField(); updateExecuteButtonState(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { syncSelectedTargetDirFromField(); updateExecuteButtonState(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { syncSelectedTargetDirFromField(); updateExecuteButtonState(); }
        });
        selectDirButton = new JButton();
        try {
            Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
            if (folderIcon != null) {
                selectDirButton.setIcon(folderIcon);
            } else {
                selectDirButton.setText("📁");
            }
        } catch (Exception e) {
            selectDirButton.setText("📁");
        }
        selectDirButton.setToolTipText("Select target directory");
        AppPanelStyle.stylePrimaryButton(selectDirButton);
        selectDirButton.addActionListener(e -> selectTargetDirectory());
        JPanel dirPanel = new JPanel(new BorderLayout());
        dirPanel.add(selectDirButton, BorderLayout.WEST);
        dirPanel.add(targetDirField, BorderLayout.CENTER);
        panel.add(dirPanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 2;
        catalogFileLabel = new JLabel("Catalog File:");
        panel.add(catalogFileLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        catalogFileField = new JTextField();
        catalogFileField.setEditable(true);
        catalogFileField.setHorizontalAlignment(JTextField.LEFT);
        AppPanelStyle.styleTextField(catalogFileField);
        catalogFileField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateExecuteButtonState(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateExecuteButtonState(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateExecuteButtonState(); }
        });
        selectCatalogButton = new JButton();
        try {
            Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
            if (fileIcon != null) {
                selectCatalogButton.setIcon(fileIcon);
            } else {
                selectCatalogButton.setText("📄");
            }
        } catch (Exception e) {
            selectCatalogButton.setText("📄");
        }
        selectCatalogButton.setToolTipText("Select catalog file");
        AppPanelStyle.stylePrimaryButton(selectCatalogButton);
        selectCatalogButton.addActionListener(e -> selectCatalogFile());
        JPanel catalogButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        catalogButtonRow.add(selectCatalogButton);
        JPanel catalogPanel = new JPanel(new BorderLayout());
        catalogPanel.add(catalogButtonRow, BorderLayout.WEST);
        catalogPanel.add(catalogFileField, BorderLayout.CENTER);
        panel.add(catalogPanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Station File:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        stationFileField = new JTextField();
        stationFileField.setEditable(true);
        stationFileField.setHorizontalAlignment(JTextField.LEFT);
        AppPanelStyle.styleTextField(stationFileField);
        stationFileField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateStationFileFromField(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateStationFileFromField(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateStationFileFromField(); }
        });
        selectStationButton = new JButton();
        try {
            Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
            if (fileIcon != null) {
                selectStationButton.setIcon(fileIcon);
            } else {
                selectStationButton.setText("📄");
            }
        } catch (Exception e) {
            selectStationButton.setText("📄");
        }
        selectStationButton.setToolTipText("Select station file");
        AppPanelStyle.stylePrimaryButton(selectStationButton);
        selectStationButton.addActionListener(e -> selectStationFile());
        JPanel stationPanel = new JPanel(new BorderLayout());
        stationPanel.add(selectStationButton, BorderLayout.WEST);
        stationPanel.add(stationFileField, BorderLayout.CENTER);
        panel.add(stationPanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 4;
        taupModelLabel = new JLabel("Velocity Model:");
        taupModelLabel.setFont(AppPanelStyle.getLabelFont());
        taupModelLabel.setForeground(AppPanelStyle.getContentTextColor());
        panel.add(taupModelLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        String[] models = VelocityModelCatalog.comboModels();
        String[] comboItems = java.util.Arrays.copyOf(models, models.length + 1);
        comboItems[models.length] = "Select from file...";
        taupModelCombo = new JComboBox<>(comboItems);
        taupModelCombo.setSelectedItem(VelocityModelCatalog.DEFAULT_MODEL);
        AppPanelStyle.styleComboBox(taupModelCombo);
        taupModelCombo.addActionListener(e -> {
            String selected = (String) taupModelCombo.getSelectedItem();
            if (selected != null) {
                if ("Select from file...".equals(selected)) {
                    selectTaupFile();
                    if (selectedTaupFile != null) {
                    } else {
                        taupModelCombo.setSelectedItem(VelocityModelCatalog.DEFAULT_MODEL);
                    }
                } else {
                    com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setTaupFile(selected);
                }
            }
        });
        panel.add(taupModelCombo, gbc);
        gbc.weightx = 0.0;
        
        return panel;
    }
    
    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(panel);
        panel.setBorder(AppPanelStyle.createSectionBorder("Output Data"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(AppPanelStyle.GAP, AppPanelStyle.GAP + 2, AppPanelStyle.GAP, AppPanelStyle.GAP + 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0;
        outputDirLabel = new JLabel("Output Directory:");
        outputDirLabel.setFont(AppPanelStyle.getLabelFont());
        panel.add(outputDirLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        outputDirField = new JTextField();
        outputDirField.setEditable(true);
        outputDirField.setHorizontalAlignment(JTextField.LEFT);
        AppPanelStyle.styleTextField(outputDirField);
        outputDirField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { syncSelectedOutputDirFromField(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { syncSelectedOutputDirFromField(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { syncSelectedOutputDirFromField(); }
        });
        selectOutputDirButton = new JButton();
        try {
            Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
            if (folderIcon != null) {
                selectOutputDirButton.setIcon(folderIcon);
            } else {
                selectOutputDirButton.setText("📁");
            }
        } catch (Exception e) {
            selectOutputDirButton.setText("📁");
        }
        selectOutputDirButton.setToolTipText("Select output directory");
        selectOutputDirButton.addActionListener(e -> selectOutputDirectory());
        AppPanelStyle.stylePrimaryButton(selectOutputDirButton);
        JPanel outputDirPanel = new JPanel(new BorderLayout());
        outputDirPanel.add(selectOutputDirButton, BorderLayout.WEST);
        outputDirPanel.add(outputDirField, BorderLayout.CENTER);
        panel.add(outputDirPanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 1;
        outputFileLabel = new JLabel("Output File:");
        outputFileLabel.setFont(AppPanelStyle.getLabelFont());
        panel.add(outputFileLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        outputFileField = new JTextField();
        outputFileField.setEditable(true);
        outputFileField.setHorizontalAlignment(JTextField.LEFT);
        AppPanelStyle.styleTextField(outputFileField);
        selectOutputFileButton = new JButton();
        try {
            Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
            if (fileIcon != null) {
                selectOutputFileButton.setIcon(fileIcon);
            } else {
                selectOutputFileButton.setText("📄");
            }
        } catch (Exception e) {
            selectOutputFileButton.setText("📄");
        }
        selectOutputFileButton.setToolTipText("Select output file");
        selectOutputFileButton.addActionListener(e -> selectOutputFile());
        AppPanelStyle.stylePrimaryButton(selectOutputFileButton);
        JPanel outputFilePanel = new JPanel(new BorderLayout());
        outputFilePanel.add(selectOutputFileButton, BorderLayout.WEST);
        outputFilePanel.add(outputFileField, BorderLayout.CENTER);
        panel.add(outputFilePanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 2;
        configExportLabel = new JLabel("Export config.json:");
        configExportLabel.setFont(AppPanelStyle.getLabelFont());
        panel.add(configExportLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        configExportField = new JTextField();
        configExportField.setEditable(true);
        configExportField.setHorizontalAlignment(JTextField.LEFT);
        AppPanelStyle.styleTextField(configExportField);
        selectConfigExportButton = new JButton();
        try {
            Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
            if (fileIcon != null) {
                selectConfigExportButton.setIcon(fileIcon);
            } else {
                selectConfigExportButton.setText("📄");
            }
        } catch (Exception e) {
            selectConfigExportButton.setText("📄");
        }
        selectConfigExportButton.setToolTipText("Select config export path (JSON)");
        AppPanelStyle.stylePrimaryButton(selectConfigExportButton);
        selectConfigExportButton.addActionListener(e -> selectConfigExportFile());
        JPanel configExportPanel = new JPanel(new BorderLayout());
        configExportPanel.add(selectConfigExportButton, BorderLayout.WEST);
        configExportPanel.add(configExportField, BorderLayout.CENTER);
        panel.add(configExportPanel, gbc);
        gbc.weightx = 0.0;
        
        return panel;
    }
    
    private void selectConfigExportFile() {
        fileChooser.setDialogTitle("Select Config Export File (JSON)");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files (*.json)", "json"));
        fileChooser.setSelectedFile(new File("config.json"));
        String currentPath = configExportField.getText().trim();
        if (!currentPath.isEmpty()) {
            String absolutePath = resolveOutputPathUnderOutputDir(currentPath);
            File currentFile = new File(absolutePath);
            if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                fileChooser.setCurrentDirectory(currentFile.getParentFile());
                fileChooser.setSelectedFile(currentFile);
            }
        }
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fileChooser.getSelectedFile();
            if (f != null) {
                String path = f.getAbsolutePath();
                if (!path.endsWith(".json")) path = path + ".json";
                configExportField.setText(path);
                appendLog("Config export path: " + path);
            }
        }
    }
    
    private void selectOutputFile() {
        fileChooser.setDialogTitle("Select Output File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "All files (*.*)", "*"));
        String outputDirPath = outputDirField != null ? outputDirField.getText().trim() : "";
        if (!outputDirPath.isEmpty()) {
            String resolvedDir = resolvePathToAbsolute(outputDirPath);
            File dir = new File(resolvedDir);
            if (dir.exists() && dir.isDirectory()) {
                fileChooser.setCurrentDirectory(dir);
                fileChooser.setSelectedFile(getSuggestedOutputCatalogFile(dir));
            }
        }
        int result = fileChooser.showSaveDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            outputFileField.setText(selectedFile.getAbsolutePath());
            appendLog("Output file selected: " + selectedFile.getAbsolutePath());
        }
    }
    
    private void selectOutputDirectory() {
        File selectedDir = com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper.selectDirectory(
            this, "Select Output Directory");
        
        if (selectedDir != null) {
            selectedOutputDir = selectedDir;
            outputDirField.setText(selectedOutputDir.getAbsolutePath());
            
            File catalogFile = getSuggestedOutputCatalogFile(selectedOutputDir);
            outputFileField.setText(catalogFile.getAbsolutePath());
            
            File configExportFile = new File(selectedOutputDir, "config.json");
            configExportField.setText(configExportFile.getAbsolutePath());
            
            updateExecuteButtonState();
            appendLog("Output directory selected: " + selectedOutputDir.getAbsolutePath());
            appendLog("Output file auto-configured: " + catalogFile.getAbsolutePath());
            appendLog("Config export auto-configured: " + configExportFile.getAbsolutePath());
        }
    }
    
    private JPanel createModePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(panel);
        panel.setBorder(AppPanelStyle.createSectionBorder("Mode"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(AppPanelStyle.GAP, AppPanelStyle.GAP + 2, AppPanelStyle.GAP, AppPanelStyle.GAP + 2);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel modeLabel = new JLabel("Mode:");
        modeLabel.setFont(AppPanelStyle.getLabelFont());
        modeLabel.setForeground(AppPanelStyle.getContentTextColor());
        panel.add(modeLabel, gbc);
        gbc.gridx = 1;
        modeCombo = new JComboBox<>(ModeNameMapper.getAllDisplayNames());
        modeCombo.setSelectedItem(ModeNameMapper.getDisplayName("GRD"));
        AppPanelStyle.styleComboBox(modeCombo);
        modeCombo.addActionListener(e -> {
            String displayName = (String) modeCombo.getSelectedItem();
            String mode = ModeNameMapper.getAbbreviation(displayName);
            saveCurrentModeStateToCache(lastSelectedMode);
            updateParameterFields();
            updateLayoutForMode(mode);
            restoreModeStateFromCache(mode, lastSelectedMode);
            lastSelectedMode = mode;
            if (residualPlotPanel != null) {
                residualPlotPanel.setMode(mode);
            }
            fireSolverModeChangeListeners();
        });
        panel.add(modeCombo, gbc);
        
        return panel;
    }
    
    private JPanel createParameterPanel() {
        parameterPanel = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(parameterPanel);
        parameterPanel.setBorder(AppPanelStyle.createSectionBorder("Parameters"));
        
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        try {
            java.net.URL helpImageUrl = HypocenterLocationPanel.class.getResource("/images/Help.png");
            if (helpImageUrl != null) {
                ImageIcon helpIcon = new ImageIcon(helpImageUrl);
                Image scaledImage = helpIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                parameterHelpButton = new JButton(new ImageIcon(scaledImage));
                parameterHelpButton.setPreferredSize(new Dimension(24, 24));
                parameterHelpButton.setBorderPainted(false);
                parameterHelpButton.setFocusPainted(false);
                parameterHelpButton.setOpaque(false);
                parameterHelpButton.setContentAreaFilled(false);
                parameterHelpButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                parameterHelpButton.addActionListener(e -> showParameterHelpDialog());
                titlePanel.add(parameterHelpButton, BorderLayout.EAST);
            }
        } catch (Exception e) {
            logger.warning("Failed to load help icon: " + e.getMessage());
        }
        
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(titlePanel, BorderLayout.CENTER);
        topPanel.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        parameterPanel.add(topPanel, BorderLayout.NORTH);
        
        String[] columnNames = {"Parameter", "Value", "Unit"};
        parameterTableModel = new javax.swing.table.DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }
        };
        
        parameterTable = new JTable(parameterTableModel);
        parameterTable.setRowHeight(25);
        parameterTable.getTableHeader().setReorderingAllowed(false);
        parameterTable.getTableHeader().setResizingAllowed(true);
        
        parameterTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        parameterTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        parameterTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        
        parameterTable.getModel().addTableModelListener(e -> {
            if (e.getColumn() == 1) {
                int row = e.getFirstRow();
                if (row >= 0 && row < parameterTableModel.getRowCount()) {
                    String paramName = (String) parameterTableModel.getValueAt(row, 0);
                    if (paramName != null && paramName.contains("showConvergenceLog")) {
                        Object value = parameterTableModel.getValueAt(row, 1);
                        if (value != null && showConvergenceLogCheckBox != null) {
                            String valueStr = value.toString().trim().toLowerCase();
                            showConvergenceLogCheckBox.setSelected("true".equals(valueStr) || "1".equals(valueStr));
                        }
                    }
                }
                updateExecuteButtonState();
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(parameterTable);
        scrollPane.setPreferredSize(new Dimension(450, 300));
        AppPanelStyle.styleTable(parameterTable);
        AppPanelStyle.styleScrollPane(scrollPane);
        parameterPanel.add(scrollPane, BorderLayout.CENTER);
        
        totalGridsField = new JTextField("300", 10);
        numFocusField = new JTextField("3", 10);
        numJobsField = new JTextField("4", 10);
        thresholdField = new JTextField("0.0", 10);
        hypBottomField = new JTextField("100.0", 10);
        
        randomSeedField = new JTextField("100", 10);
        phsErrField = new JTextField("0.1", 10);
        locErrField = new JTextField("0.03", 10);
        minSelectRateField = new JTextField("0.2", 10);
        maxSelectRateField = new JTextField("0.4", 10);
        
        nSamplesField = new JTextField("10000", 10);
        burnInField = new JTextField("2000", 10);
        stepSizeField = new JTextField("0.1", 10);
        stepSizeDepthField = new JTextField("1.0", 10);
        temperatureField = new JTextField("1.0", 10);
        
        dePopulationSizeField = new JTextField("50", 10);
        deMaxGenerationsField = new JTextField("100", 10);
        deScalingFactorField = new JTextField("0.8", 10);
        deCrossoverRateField = new JTextField("0.9", 10);

        minPtsField = new JTextField("4", 10);
        epsField = new JTextField("-1.0", 10);
        epsPercentileField = new JTextField("", 10);
        rmsThresholdField = new JTextField("", 10);
        locErrThresholdField = new JTextField("", 10);
        
        trdIterNumField = new JTextField("10,10", 10);
        trdDistKmField = new JTextField("50,20", 10);
        trdDampFactField = new JTextField("0,1", 10);
        trdMaxTripleDiffCountField = new JTextField("", 10);
        trdMaxTripleDiffCountField.setToolTipText("Per cluster; empty = no limit. TRD uses triple-diff data with smallest residual first.");

        lsqrAtolField = new JTextField("1e-6", 10);
        lsqrBtolField = new JTextField("1e-6", 10);
        lsqrConlimField = new JTextField("1e8", 10);
        lsqrIterLimField = new JTextField("1000", 10);
        lsqrShowLogCheckBox = new JCheckBox("Show LSQR Convergence Log", true);
        lsqrCalcVarCheckBox = new JCheckBox("Calculate Variance (Error Estimation)", true);
        
        showConvergenceLogCheckBox = new JCheckBox("Show convergence chart", true);
        showConvergenceLogCheckBox.addActionListener(e -> {
            boolean selected = showConvergenceLogCheckBox.isSelected();
            if (convergenceLogPanel != null) {
                updateConvergenceLogPanelVisibility(selected);
            }
            updateParameterFields();
        });
        
        lmInitialStepBoundField = new JTextField("100", 10);
        lmCostRelativeToleranceField = new JTextField("1e-6", 10);
        lmParRelativeToleranceField = new JTextField("1e-6", 10);
        lmOrthoToleranceField = new JTextField("1e-6", 10);
        lmMaxEvaluationsField = new JTextField("1000", 10);
        lmMaxIterationsField = new JTextField("1000", 10);
        
        return parameterPanel;
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        AppPanelStyle.setPanelBackground(panel);
        
        executeButton = new JButton("▶ Execute");
        executeButton.setEnabled(false);
        executeButton.setFont(UiFonts.uiBold(12f));
        executeButton.setPreferredSize(new Dimension(120, 35));
        AppPanelStyle.styleSuccessButton(executeButton);
        executeButton.addActionListener(e -> executeLocation());
        panel.add(executeButton);
        
        cancelButton = new JButton("⏹ Cancel");
        cancelButton.setEnabled(false);
        cancelButton.setFont(UiFonts.uiBold(14f));
        cancelButton.setPreferredSize(new Dimension(120, 35));
        cancelButton.setBackground(new Color(200, 50, 50));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        cancelButton.setOpaque(true);
        cancelButton.addActionListener(e -> cancelExecution());
        panel.add(cancelButton);
        
        return panel;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(panel);
        panel.setOpaque(true);
        panel.setBorder(AppPanelStyle.createSectionBorder("Execution Log"));
        
        panel.setMinimumSize(new Dimension(300, 100));
        
        CardLayout cardLayout = new CardLayout();
        JPanel contentPanel = new JPanel(cardLayout);
        contentPanel.setOpaque(true);
        AppPanelStyle.setPanelBackground(contentPanel);
        contentPanel.setMinimumSize(new Dimension(300, 100));
        
        logArea = new JTextPane();
        logArea.setEditable(false);
        logArea.setFocusable(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        int menuShortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap logInput = logArea.getInputMap(JComponent.WHEN_FOCUSED);
        logInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuShortcut), DefaultEditorKit.copyAction);
        logInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuShortcut), DefaultEditorKit.selectAllAction);
        logArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                logArea.requestFocusInWindow();
            }
        });

        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setMinimumSize(new Dimension(300, 100));
        contentPanel.add(logScrollPane, "LOG");
        
        logCommentLabel = new JLabel(
            "<html><div style='text-align: center; padding: 20px; color: #666;'>"
            + "Execution log is hidden. Enable 'Show Execution Log' to display execution logs."
            + "</div></html>"
        );
        logCommentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logCommentLabel.setVerticalAlignment(SwingConstants.CENTER);
        logCommentLabel.setFont(UiFonts.uiPlain(12f));
        logCommentLabel.setMinimumSize(new Dimension(300, 100));
        logCommentLabel.setPreferredSize(new Dimension(500, 200));
        contentPanel.add(logCommentLabel, "COMMENT");
        
        cardLayout.show(contentPanel, "LOG");
        
        panel.add(contentPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void updateLogPanelVisibility(boolean visible) {
        if (logPanel == null) {
            return;
        }
        
        Component[] components = logPanel.getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                LayoutManager layout = ((JPanel) comp).getLayout();
                if (layout instanceof CardLayout) {
                    CardLayout cardLayout = (CardLayout) layout;
                    if (visible) {
                        cardLayout.show((JPanel) comp, "LOG");
                    } else {
                        cardLayout.show((JPanel) comp, "COMMENT");
                    }
                    break;
                }
            }
        }
        
        logPanel.revalidate();
        logPanel.repaint();
    }
    
    private void updateConvergenceLogPanelVisibility(boolean visible) {
        if (convergenceLogPanel == null) {
            return;
        }
        if (visible) {
            showConvergenceCard("PLOT_ONLY");
            convergenceLogPanel.setMinimumSize(new Dimension(200, 150));
        } else {
            showConvergenceCard("MESSAGE");
            convergenceLogPanel.setMinimumSize(new Dimension(0, 0));
        }
        convergenceLogPanel.setVisible(visible);
        if (solverConvergenceHostPanel != null) {
            solverConvergenceHostPanel.setVisible(visible);
            solverConvergenceHostPanel.revalidate();
            solverConvergenceHostPanel.repaint();
        }
        convergenceLogPanel.revalidate();
        convergenceLogPanel.repaint();
    }
    
    private void setupConvergenceCallback() {
        convergenceCallback = new ConvergenceCallback() {
            @Override
            public void onResidualUpdate(int iteration, double residual) {
                String eventName = getCurrentEventName();
                
                if (residualPlotPanel != null && residualPlotPanel.isVisible()) {
                    if (eventName != null) {
                        residualPlotPanel.addResidualPoint(eventName, iteration, residual);
                    } else {
                        residualPlotPanel.addResidualPoint(iteration, residual);
                    }
                }
                
                if (convergenceLogPanel != null && convergenceLogPanel.isVisible()
                    && shouldStreamConvergenceToExecutionLog()) {
                    String logMessage = String.format("Iteration %d: Residual = %.6f s", iteration, residual);
                    if (eventName != null) {
                        logMessage = String.format("[%s] %s", eventName, logMessage);
                    }
                    appendConvergenceLog(logMessage);
                }
            }
            
            @Override
            public void onLikelihoodUpdate(int sample, double logLikelihood) {
                String eventName = getCurrentEventName();
                
                if (residualPlotPanel != null && residualPlotPanel.isVisible()) {
                    if (eventName != null) {
                        residualPlotPanel.addLikelihoodPoint(eventName, sample, logLikelihood);
                    } else {
                        residualPlotPanel.addLikelihoodPoint(sample, logLikelihood);
                    }
                }
                
                if (convergenceLogPanel != null && convergenceLogPanel.isVisible()
                    && shouldStreamConvergenceToExecutionLog()) {
                    String logMessage = String.format("Sample %d: Log-Likelihood = %.4f", sample, logLikelihood);
                    if (eventName != null) {
                        logMessage = String.format("[%s] %s", eventName, logMessage);
                    }
                    appendConvergenceLog(logMessage);
                }
            }
            
            @Override
            public void onClusterResidualUpdate(int clusterId, int iteration, double residual) {
                String eventName = getCurrentEventName();
                String clusterEventName = "Cluster " + clusterId;
                
                if (residualPlotPanel != null && residualPlotPanel.isVisible()) {
                    residualPlotPanel.addResidualPoint(clusterEventName, iteration, residual);
                }
                
                if (convergenceLogPanel != null && convergenceLogPanel.isVisible()
                    && shouldStreamConvergenceToExecutionLog()) {
                    String logMessage = String.format(
                        "Cluster %d, LSQR step %d (cumulative over TRD stages): Residual = %.6f s",
                        clusterId, iteration + 1, residual);
                    if (eventName != null) {
                        logMessage = String.format("[%s] %s", eventName, logMessage);
                    }
                    appendConvergenceLog(logMessage);
                }
            }
            
            @Override
            public void onIterationUpdate(int iteration, int evaluations, double residual, 
                                         double[] parameterChanges) {
                String eventName = getCurrentEventName();
                
                if (residualPlotPanel != null && residualPlotPanel.isVisible()) {
                    if (eventName != null) {
                        residualPlotPanel.addResidualPoint(eventName, iteration, residual);
                    } else {
                        residualPlotPanel.addResidualPoint(iteration, residual);
                    }
                }
                
                if (convergenceLogPanel != null && convergenceLogPanel.isVisible()
                    && shouldStreamConvergenceToExecutionLog()) {
                    String logMessage;
                    if (parameterChanges != null && parameterChanges.length >= 3) {
                        logMessage = String.format(
                            "Iteration %d (Eval: %d): Residual = %.6f s, Δ(lon,lat,dep) = (%.6f°, %.6f°, %.3f km)",
                            iteration, evaluations, residual,
                            parameterChanges[0], parameterChanges[1], parameterChanges[2]);
                    } else {
                        logMessage = String.format("Iteration %d (Eval: %d): Residual = %.6f s",
                            iteration, evaluations, residual);
                    }
                    if (eventName != null) {
                        logMessage = String.format("[%s] %s", eventName, logMessage);
                    }
                    appendConvergenceLog(logMessage);
                }
            }
        };
    }
    
    /**
     * Gets the current event name being processed (for parallel processing).
     * 
     * @return the current event name, or null if not set
     */
    private String getCurrentEventName() {
        return currentEventName;
    }
    
    private String currentEventName;
    
    /**
     * Gets the selected mode abbreviation from the mode combo box.
     * Converts display name to abbreviation if needed.
     * 
     * @return mode abbreviation (e.g., "LMO"), or "GRD" as default
     */
    private String getSelectedModeAbbreviation() {
        if (modeCombo == null) {
            return "GRD";
        }
        Object selected = modeCombo.getSelectedItem();
        if (selected == null) {
            return "GRD";
        }
        String displayName = selected.toString();
        String abbrev = ModeNameMapper.getAbbreviation(displayName);
        return abbrev != null ? abbrev : "GRD";
    }
    
    /**
     * Returns the suggested output catalog file for the current mode (e.g. catalog_lmo.csv, catalog_lmo_cls.csv).
     * Uses CatalogFileNameGenerator: mode suffix is appended; for CLS the input catalog base name is used.
     *
     * @param outputDir output directory (if null, uses selectedOutputDir or a default)
     * @return suggested File for the output catalog
     */
    private File getSuggestedOutputCatalogFile(File outputDir) {
        File dir = outputDir != null ? outputDir : getOutputDirForResolve();
        if (dir == null) {
            String mode = getSelectedModeAbbreviation();
            String suffix = (mode != null && !mode.isEmpty()) ? "_" + mode.toLowerCase() : "";
            return new File("catalog" + suffix + ".csv");
        }
        String mode = getSelectedModeAbbreviation();
        String inputCatalogPath = null;
        if ("CLS".equals(mode) && catalogFileField != null) {
            String path = catalogFileField.getText();
            if (path != null && !path.trim().isEmpty()) {
                inputCatalogPath = path.trim();
            }
        }
        return CatalogFileNameGenerator.generateCatalogFileName(inputCatalogPath, mode, dir);
    }
    
    private void selectTargetDirectory() {
        File selectedDir = com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper.selectDirectory(
            this, "Select Target Directory");
        
        if (selectedDir != null) {
            selectedTargetDir = selectedDir;
            targetDirField.setText(selectedTargetDir.getAbsolutePath());
            updateExecuteButtonState();
            appendLog("Target directory selected: " + selectedTargetDir.getAbsolutePath());
        }
    }
    
    private void selectStationFile() {
        fileChooser.setDialogTitle("Select Station File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Station files (*.tbl)", "tbl"));
        
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
        
        String currentPath = stationFileField.getText().trim();
        if (!currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                if (currentFile.isFile()) {
                    fileChooser.setSelectedFile(currentFile);
                } else {
                    fileChooser.setCurrentDirectory(currentFile);
                }
            }
        }
        
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedStationFile = fileChooser.getSelectedFile();
            stationFileField.setText(selectedStationFile.getAbsolutePath());
            updateExecuteButtonState();
            appendLog("Station file selected: " + selectedStationFile.getAbsolutePath());
            
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setStationFile(selectedStationFile);
        }
    }
    
    private void updateStationFileFromField() {
        String filePath = stationFileField.getText().trim();
        if (!filePath.isEmpty()) {
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                selectedStationFile = file;
                updateExecuteButtonState();
            }
        }
    }
    
    private void selectTaupFile() {
        fileChooser.setDialogTitle("Select Velocity Model File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Velocity model files (*.nd, *.tvel)", "nd", "tvel"));
        fileChooser.setAcceptAllFileFilterUsed(true);
        
        String homeDir = System.getProperty("user.home");
        if (homeDir != null && !homeDir.isEmpty()) {
            fileChooser.setCurrentDirectory(new File(homeDir));
        }
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedTaupFile = fileChooser.getSelectedFile();
            String filePath = selectedTaupFile.getAbsolutePath();
            
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) taupModelCombo.getModel();
            
            boolean alreadyExists = false;
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).equals(filePath)) {
                    alreadyExists = true;
                    break;
                }
            }
            
            if (!alreadyExists) {
                model.addElement(filePath);
            }
            
            taupModelCombo.setSelectedItem(filePath);
            
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setTaupFile(filePath);
            appendLog("TauP model file selected: " + filePath);
        }
    }
    
    private void selectCatalogFile() {
        fileChooser.setDialogTitle("Select Catalog File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Catalog files (*.csv)", "csv"));
        
        String currentPath = catalogFileField.getText().trim();
        if (!currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                if (currentFile.isFile()) {
                    fileChooser.setSelectedFile(currentFile);
                    fileChooser.setCurrentDirectory(currentFile.getParentFile());
                } else {
                    fileChooser.setCurrentDirectory(currentFile);
                }
            } else {
                com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
            }
        } else {
            com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
        }
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            catalogFileField.setText(selectedFile.getAbsolutePath());
            updateExecuteButtonState();
            appendLog("Catalog file selected: " + selectedFile.getAbsolutePath());
        }
    }
    
    private File getCatalogFileFromField() {
        if (catalogFileField == null) return null;
        String filePath = catalogFileField.getText().trim();
        if (filePath.isEmpty()) return null;
        filePath = resolvePathToAbsolute(filePath);
        File file = new File(filePath);
        if (file.exists() && file.isFile()) return file;
        return null;
    }
    
    /**
     * Resolves a path string to an absolute path. If the path is already absolute, returns it as-is.
     * If relative, resolves against the base directory: the parent of the imported config file
     * (when set and existing), otherwise the JVM's user.dir. This allows config-relative paths
     * to work for output file and config export path completion.
     */
    private String resolvePathToAbsolute(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) return pathStr;
        pathStr = pathStr.trim();
        File f = new File(pathStr);
        if (f.isAbsolute()) return pathStr;
        File base = null;
        if (importJsonFileField != null) {
            String importPath = importJsonFileField.getText();
            if (importPath != null && !importPath.trim().isEmpty()) {
                File importFile = new File(importPath.trim());
                if (importFile.exists() && importFile.isFile() && importFile.getParentFile() != null) {
                    base = importFile.getParentFile();
                }
            }
        }
        if (base == null) {
            base = new File(System.getProperty("user.dir", "."));
        }
        return new File(base, pathStr).getAbsolutePath();
    }
    
    /**
     * Resolves Output File or Config Export path. Absolute paths are returned as-is.
     * Relative paths are resolved under the current Output Directory using only the last
     * path component (filename), so e.g. "sub/catalog.csv" becomes outputDir/catalog.csv.
     */
    private String resolveOutputPathUnderOutputDir(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) return pathStr;
        pathStr = pathStr.trim();
        File f = new File(pathStr);
        if (f.isAbsolute()) return pathStr;
        File baseDir = getOutputDirForResolve();
        if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory()) {
            return resolvePathToAbsolute(pathStr);
        }
        String fileName = f.getName();
        return new File(baseDir, fileName).getAbsolutePath();
    }

    /**
     * When the output file is a bare name, config-relative path, or a single-segment absolute path
     * (e.g. {@code /catalog.csv} on Unix), rewrites it under the current output directory for display
     * and for consistent resolution.
     */
    private String normalizeOutputFilePathUsingOutputDir(File outDir, String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty() || outDir == null) {
            return pathStr;
        }
        String trimmed = pathStr.trim();
        File f = new File(trimmed);
        String name = f.getName();
        if (name.isEmpty()) {
            return pathStr;
        }
        String outAbs = outDir.getAbsolutePath();
        if (!f.isAbsolute()) {
            return new File(outDir, name).getAbsolutePath();
        }
        String curAbs = f.getAbsolutePath();
        if (curAbs.equals(outAbs) || curAbs.startsWith(outAbs + File.separator)) {
            return curAbs;
        }
        File parent = f.getParentFile();
        if (parent != null && isFilesystemRoot(parent)) {
            return new File(outDir, name).getAbsolutePath();
        }
        return pathStr;
    }

    private static boolean isFilesystemRoot(File dir) {
        if (dir == null) {
            return false;
        }
        for (File root : File.listRoots()) {
            try {
                if (dir.getCanonicalFile().equals(root.getCanonicalFile())) {
                    return true;
                }
            } catch (IOException e) {
                if (dir.getAbsolutePath().equals(root.getAbsolutePath())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * After importing JSON: sync output directory from fields and fill or fix the Output File path
     * using {@link #normalizeOutputFilePathUsingOutputDir(File, String)} / suggested catalog name.
     */
    private void completeOutputFileFieldAfterConfigImport() {
        syncSelectedOutputDirFromField();
        File out = getResolvedOutputDirectoryPathFromUi();
        if (out == null || outputFileField == null) {
            return;
        }
        String p = outputFileField.getText().trim();
        if (p.isEmpty()) {
            File suggested = getSuggestedOutputCatalogFile(out);
            outputFileField.setText(suggested.getAbsolutePath());
            appendLog("Output file auto-filled: " + suggested.getAbsolutePath());
            return;
        }
        String normalized = normalizeOutputFilePathUsingOutputDir(out, p);
        if (!normalized.equals(p)) {
            outputFileField.setText(normalized);
            appendLog("Output file path completed: " + normalized);
        }
    }

    /**
     * Resolved output directory path from the Output Directory field for UI completion, even when the
     * directory does not exist yet (execute step may create it). Prefer {@link #getOutputDirForResolve()}
     * when the path exists on disk.
     */
    private File getResolvedOutputDirectoryPathFromUi() {
        File existing = getOutputDirForResolve();
        if (existing != null) {
            return existing;
        }
        if (outputDirField == null) {
            return null;
        }
        String v = outputDirField.getText();
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        return new File(resolvePathToAbsolute(v.trim()));
    }
    
    /** Returns the current output directory for resolving relative output/config paths; may be null. */
    private File getOutputDirForResolve() {
        if (selectedOutputDir != null && selectedOutputDir.exists() && selectedOutputDir.isDirectory()) {
            return selectedOutputDir;
        }
        if (outputDirField != null) {
            String v = outputDirField.getText();
            if (v != null && !v.trim().isEmpty()) {
                String resolved = resolvePathToAbsolute(v.trim());
                File dir = new File(resolved);
                if (dir.exists() && dir.isDirectory()) return dir;
            }
        }
        return null;
    }
    
    /** Updates selectedOutputDir from the current Output Directory field text when it denotes a valid directory. */
    private void syncSelectedOutputDirFromField() {
        if (outputDirField == null) return;
        String v = outputDirField.getText();
        if (v == null || v.trim().isEmpty()) {
            selectedOutputDir = null;
            return;
        }
        String resolved = resolvePathToAbsolute(v.trim());
        File dir = new File(resolved);
        if (dir.exists() && dir.isDirectory()) {
            selectedOutputDir = dir;
        }
    }
    
    /** Updates selectedTargetDir from the current Target Directory (Directory) field text when it denotes a valid directory. */
    private void syncSelectedTargetDirFromField() {
        if (targetDirField == null) return;
        String v = targetDirField.getText();
        if (v == null || v.trim().isEmpty()) {
            selectedTargetDir = null;
            return;
        }
        String resolved = resolvePathToAbsolute(v.trim());
        File dir = new File(resolved);
        if (dir.exists() && dir.isDirectory()) {
            selectedTargetDir = dir;
        } else {
            selectedTargetDir = null;
        }
    }
    
    private File getConfigExportFileFromField() {
        String path = configExportField != null ? configExportField.getText().trim() : "";
        if (path.isEmpty()) return null;
        return new File(resolveOutputPathUnderOutputDir(path));
    }
    
    /** Returns the output catalog file from Output File field (resolved; relative = outputDir + filename only), or null if empty. */
    private File getOutputFileFromField() {
        if (outputFileField == null) return null;
        String path = outputFileField.getText().trim();
        if (path.isEmpty()) return null;
        return new File(resolveOutputPathUnderOutputDir(path));
    }
    
    private void exportConfigToPath(File file) {
        if (file == null) return;
        try {
            AppConfig cfg = buildConfigFromUI();
            String path = file.getAbsolutePath();
            if (!path.endsWith(".json")) path = path + ".json";
            file = new File(path);
            java.nio.file.Path parent = file.toPath().getParent();
            if (parent != null && !java.nio.file.Files.exists(parent)) {
                java.nio.file.Files.createDirectories(parent);
            }
            ObjectMapper mapper = JsonMapperHolder.getMapper();
            mapper.writeValue(file, cfg);
            appendLog("Configuration exported to: " + file.getAbsolutePath());
        } catch (Exception e) {
            logger.warning("Failed to export config to path: " + e.getMessage());
            appendLog("Warning: Failed to export configuration: " + e.getMessage());
        }
    }
    
    private void updateParameterFields() {
        if (parameterTableModel == null) {
            return;
        }
        
        parameterTableModel.setRowCount(0);
        
        String selectedMode = getSelectedModeAbbreviation();
        
        if (!"SYN".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Parallelization (numJobs)", numJobsField.getText(), "cluster triple-diff parallel jobs (CLS); file batch jobs (others)"});
            parameterTableModel.addRow(new Object[]{"Weight Threshold (threshold)", thresholdField.getText(), "weight (empty or 0.0 for no filtering)"});
            if (!"CLS".equals(selectedMode)) {
                parameterTableModel.addRow(new Object[]{"Maximum Depth (hypBottom)", hypBottomField.getText(), "km"});
            }
        }
        
        if ("GRD".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Total Grids (totalGrids)", totalGridsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Focus Count (numFocus)", numFocusField.getText(), ""});
        } else if ("LMO".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"LM Initial Step Bound (initialStepBoundFactor)", lmInitialStepBoundField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Cost Relative Tolerance (costRelativeTolerance)", lmCostRelativeToleranceField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Parameter Relative Tolerance (parRelativeTolerance)", lmParRelativeToleranceField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Orthogonal Tolerance (orthoTolerance)", lmOrthoToleranceField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Max Evaluations (maxEvaluations)", lmMaxEvaluationsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Max Iterations (maxIterations)", lmMaxIterationsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Show convergence (showConvergenceLog)", showConvergenceLogCheckBox.isSelected() ? "true" : "false", "Show residual chart on the right; run totals only in TSV summary below"});
        } else if ("TRD".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Iteration Count (iterNum)", trdIterNumField.getText(), "Comma-separated (e.g., 10,10)"});
            parameterTableModel.addRow(new Object[]{"Distance Threshold (distKm)", trdDistKmField.getText(), "km, comma-separated (e.g., 50,20)"});
            parameterTableModel.addRow(new Object[]{"Damping Factor (dampFact)", trdDampFactField.getText(), "Comma-separated (e.g., 0,1)"});
            parameterTableModel.addRow(new Object[]{"Max Triple-Diff Count (maxTripleDiffCount)", trdMaxTripleDiffCountField.getText(), "Per cluster; empty = no limit; smallest residual first"});
            parameterTableModel.addRow(new Object[]{"LSQR ATOL (atol)", lsqrAtolField.getText(), "Stopping tolerance (default: 1e-6)"});
            parameterTableModel.addRow(new Object[]{"LSQR BTOL (btol)", lsqrBtolField.getText(), "Stopping tolerance (default: 1e-6)"});
            parameterTableModel.addRow(new Object[]{"LSQR CONLIM (conlim)", lsqrConlimField.getText(), "Condition number limit (default: 1e8)"});
            parameterTableModel.addRow(new Object[]{"LSQR Iteration Limit (iter_lim)", lsqrIterLimField.getText(), "Maximum iterations (default: 1000)"});
            parameterTableModel.addRow(new Object[]{"LSQR Show Log (showLSQR)", lsqrShowLogCheckBox.isSelected() ? "true" : "false", "Display LSQR iteration log"});
            parameterTableModel.addRow(new Object[]{"LSQR Calculate Variance (calcVar)", lsqrCalcVarCheckBox.isSelected() ? "true" : "false", "Estimate error covariance diagonal elements"});
            parameterTableModel.addRow(new Object[]{"Show convergence (showConvergenceLog)", showConvergenceLogCheckBox.isSelected() ? "true" : "false", "Show residual chart on the right; run totals only in TSV summary below"});
        } else if ("MCMC".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Sample Count (nSamples)", nSamplesField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Burn-in Period (burnIn)", burnInField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Step Size (stepSize)", stepSizeField.getText(), "degree"});
            parameterTableModel.addRow(new Object[]{"Depth Step Size (stepSizeDepth)", stepSizeDepthField.getText(), "km"});
            parameterTableModel.addRow(new Object[]{"Temperature Parameter (temperature)", temperatureField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Show convergence (showConvergenceLog)", showConvergenceLogCheckBox.isSelected() ? "true" : "false", "Show residual chart on the right; run totals only in TSV summary below"});
        } else if ("DE".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Population Size (populationSize)", dePopulationSizeField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Max Generations (maxGenerations)", deMaxGenerationsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Scaling Factor (scalingFactor)", deScalingFactorField.getText(), "[0-2]"});
            parameterTableModel.addRow(new Object[]{"Crossover Rate (crossoverRate)", deCrossoverRateField.getText(), "[0-1]"});
            parameterTableModel.addRow(new Object[]{"Show convergence (showConvergenceLog)", showConvergenceLogCheckBox.isSelected() ? "true" : "false", "Show residual chart on the right; run totals only in TSV summary below"});
        } else if ("CLS".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Minimum Points (minPts)", minPtsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Epsilon (eps)", epsField.getText(), "km (negative value for auto-estimation)"});
            parameterTableModel.addRow(new Object[]{"Data Inclusion Rate (epsPercentile)", epsPercentileField.getText(), "[0-1] (elbow method if eps<0 and empty)"});
            parameterTableModel.addRow(new Object[]{"RMS Threshold (rmsThreshold)", rmsThresholdField.getText(), "s (empty for no filtering)"});
            parameterTableModel.addRow(new Object[]{"Location Error Threshold (locErrThreshold)", locErrThresholdField.getText(), "km (xerr and yerr must both be <= this, empty for no filtering)"});
            parameterTableModel.addRow(new Object[]{"Do Clustering (doClustering)", "true", "true: run DBSCAN; false: use existing cluster IDs in catalog"});
            parameterTableModel.addRow(new Object[]{"Calc. Triple Diff (calcTripleDiff)", "true", "true: compute triple differences; false: clustering only"});
        } else if ("SYN".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Random Seed (randomSeed)", randomSeedField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Phase Error (phsErr)", phsErrField.getText(), "s"});
            parameterTableModel.addRow(new Object[]{"Location Error (locErr)", locErrField.getText(), "degree"});
            parameterTableModel.addRow(new Object[]{"Minimum Selection Rate (minSelectRate)", minSelectRateField.getText(), "[0-1]"});
            parameterTableModel.addRow(new Object[]{"Maximum Selection Rate (maxSelectRate)", maxSelectRateField.getText(), "[0-1]"});
        }
        
        updateLayoutForMode(selectedMode);
        updateExecuteButtonState();
    }
    
    private String getParameterValue(String paramName) {
        if (parameterTableModel == null) {
            return "";
        }
        for (int i = 0; i < parameterTableModel.getRowCount(); i++) {
            String name = (String) parameterTableModel.getValueAt(i, 0);
            if (name != null && name.contains(paramName)) {
                Object value = parameterTableModel.getValueAt(i, 1);
                return value != null ? value.toString() : "";
            }
        }
        return "";
    }
    
    private void setParameterInTable(String paramName, String value) {
        if (parameterTableModel == null || value == null) return;
        for (int i = 0; i < parameterTableModel.getRowCount(); i++) {
            String name = (String) parameterTableModel.getValueAt(i, 0);
            if (name != null && name.contains("(" + paramName + ")")) {
                parameterTableModel.setValueAt(value.trim(), i, 1);
                return;
            }
        }
    }
    
    /**
     * Saves the current parameter table, shared fields, and input/output paths into the per-mode cache
     * so that switching back to this mode restores the same values.
     *
     * @param mode mode abbreviation (GRD, LMO, MCMC, DE, TRD, CLS, SYN)
     */
    private void saveCurrentModeStateToCache(String mode) {
        if (mode == null) {
            return;
        }
        Map<String, String> cache = modeParameterCache.computeIfAbsent(mode, k -> new HashMap<>());
        cache.clear();
        if (parameterTableModel != null) {
            for (int i = 0; i < parameterTableModel.getRowCount(); i++) {
                String name = (String) parameterTableModel.getValueAt(i, 0);
                if (name == null) continue;
                int start = name.indexOf('(');
                int end = name.indexOf(')', start);
                String key = (start >= 0 && end > start) ? name.substring(start + 1, end).trim() : null;
                if (key != null && !key.isEmpty()) {
                    Object value = parameterTableModel.getValueAt(i, 1);
                    cache.put(key, value != null ? value.toString() : "");
                }
            }
        }
        if (targetDirField != null) cache.put("targetDir", targetDirField.getText() != null ? targetDirField.getText() : "");
        if (outputDirField != null) cache.put("outputDir", outputDirField.getText() != null ? outputDirField.getText() : "");
        if (stationFileField != null) cache.put("stationFile", stationFileField.getText() != null ? stationFileField.getText() : "");
        if (catalogFileField != null) cache.put("catalogFile", catalogFileField.getText() != null ? catalogFileField.getText() : "");
        if (taupModelCombo != null) {
            Object selected = taupModelCombo.getSelectedItem();
            cache.put("taupFile", selected != null ? selected.toString() : "");
        }
        if (importJsonFileField != null) {
            String importPath = importJsonFileField.getText() != null ? importJsonFileField.getText() : "";
            cache.put("configFile", importPath);
            cache.put("importJsonFile", importPath);
        }
        if (configExportField != null) cache.put("configExport", configExportField.getText() != null ? configExportField.getText() : "");
        if (outputFileField != null) {
            cache.put("outputFile", outputFileField.getText() != null ? outputFileField.getText() : "");
        }
    }
    
    /**
     * Restores parameter values and input/output paths for the given mode from the cache
     * into the UI fields and refreshes the parameter table.
     * If the target mode has no cached values, copies from the source mode (the mode we switched from)
     * into the target mode's cache so that I/O and parameters are inherited, then restores.
     * If no source is provided or source has no cache, falls back to GRD.
     *
     * @param mode   mode abbreviation to restore (GRD, LMO, MCMC, DE, TRD, CLS, SYN)
     * @param sourceModeForCopy when target mode has no cache, copy from this mode's cache first (e.g. previous mode)
     */
    private void restoreModeStateFromCache(String mode, String sourceModeForCopy) {
        Map<String, String> cache = modeParameterCache.get(mode);
        if (cache == null || cache.isEmpty()) {
            if (sourceModeForCopy != null && !sourceModeForCopy.equals(mode)) {
                Map<String, String> sourceCache = modeParameterCache.get(sourceModeForCopy);
                if (sourceCache != null && !sourceCache.isEmpty()) {
                    Map<String, String> targetCache = modeParameterCache.computeIfAbsent(mode, k -> new HashMap<>());
                    targetCache.clear();
                    targetCache.putAll(sourceCache);
                    // First visit to this mode: do not inherit the previous mode's output path (names are mode-specific).
                    targetCache.remove("outputFile");
                    cache = targetCache;
                }
            }
            if ((cache == null || cache.isEmpty()) && !"GRD".equals(mode)) {
                cache = modeParameterCache.get("GRD");
            }
        }
        if (cache == null || cache.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> e : cache.entrySet()) {
            String k = e.getKey();
            if ("targetDir".equals(k) || "outputDir".equals(k) || "stationFile".equals(k) || "catalogFile".equals(k)
                    || "configFile".equals(k) || "configExport".equals(k) || "importJsonFile".equals(k)
                    || "taupFile".equals(k) || "outputFile".equals(k)) {
                continue;
            }
            setParameterValue(k, e.getValue());
        }
        String v;
        if ((v = cache.get("targetDir")) != null && targetDirField != null) {
            targetDirField.setText(v);
            if (!v.isEmpty()) {
                String resolved = resolvePathToAbsolute(v);
                File f = new File(resolved);
                if (f.exists()) selectedTargetDir = f.isDirectory() ? f : f.getParentFile();
            }
        }
        if ((v = cache.get("outputDir")) != null && outputDirField != null) {
            outputDirField.setText(v);
            if (!v.isEmpty()) {
                String resolvedOut = resolvePathToAbsolute(v);
                File f = new File(resolvedOut);
                if (f.exists() && f.isDirectory()) selectedOutputDir = f;
                if (configExportField != null && (configExportField.getText() == null || configExportField.getText().trim().isEmpty())) {
                    configExportField.setText(new File(resolvedOut, "config.json").getAbsolutePath());
                }
            }
        }
        if ((v = cache.get("stationFile")) != null && stationFileField != null) {
            stationFileField.setText(v);
            if (!v.isEmpty()) {
                File f = new File(v);
                if (f.exists() && f.isFile()) selectedStationFile = f;
            }
        }
        if ((v = cache.get("catalogFile")) != null && catalogFileField != null) {
            catalogFileField.setText(v);
        }
        syncSelectedOutputDirFromField();
        File outDirForOutput = getResolvedOutputDirectoryPathFromUi();
        String cachedOutputFile = cache.get("outputFile");
        if (outputFileField != null) {
            if (cachedOutputFile != null && !cachedOutputFile.trim().isEmpty()) {
                if (outDirForOutput != null) {
                    outputFileField.setText(normalizeOutputFilePathUsingOutputDir(outDirForOutput, cachedOutputFile.trim()));
                } else {
                    outputFileField.setText(cachedOutputFile.trim());
                }
            } else if (outDirForOutput != null) {
                outputFileField.setText(getSuggestedOutputCatalogFile(outDirForOutput).getAbsolutePath());
            }
        }
        if ((v = cache.get("configFile")) != null && importJsonFileField != null) {
            importJsonFileField.setText(v);
        } else if ((v = cache.get("importJsonFile")) != null && importJsonFileField != null) {
            importJsonFileField.setText(v);
        }
        if ((v = cache.get("configExport")) != null && configExportField != null) {
            configExportField.setText(v);
        }
        if ((v = cache.get("taupFile")) != null && taupModelCombo != null && !v.trim().isEmpty()) {
            taupModelCombo.setSelectedItem(v);
        }
        updateParameterFields();
    }
    
    /**
     * Sets a single parameter value by key, updating the corresponding UI field.
     * Used when restoring cached mode state.
     *
     * @param key parameter key (e.g. "numJobs", "threshold")
     * @param value string value to set
     */
    private void setParameterValue(String key, String value) {
        if (key == null || value == null) return;
        switch (key) {
            case "numJobs": if (numJobsField != null) numJobsField.setText(value); break;
            case "threshold": if (thresholdField != null) thresholdField.setText(value); break;
            case "hypBottom": if (hypBottomField != null) hypBottomField.setText(value); break;
            case "totalGrids": if (totalGridsField != null) totalGridsField.setText(value); break;
            case "numFocus": if (numFocusField != null) numFocusField.setText(value); break;
            case "initialStepBoundFactor": if (lmInitialStepBoundField != null) lmInitialStepBoundField.setText(value); break;
            case "costRelativeTolerance": if (lmCostRelativeToleranceField != null) lmCostRelativeToleranceField.setText(value); break;
            case "parRelativeTolerance": if (lmParRelativeToleranceField != null) lmParRelativeToleranceField.setText(value); break;
            case "orthoTolerance": if (lmOrthoToleranceField != null) lmOrthoToleranceField.setText(value); break;
            case "maxEvaluations": if (lmMaxEvaluationsField != null) lmMaxEvaluationsField.setText(value); break;
            case "maxIterations": if (lmMaxIterationsField != null) lmMaxIterationsField.setText(value); break;
            case "showConvergenceLog": if (showConvergenceLogCheckBox != null) showConvergenceLogCheckBox.setSelected("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim())); break;
            case "iterNum": if (trdIterNumField != null) trdIterNumField.setText(value); break;
            case "distKm": if (trdDistKmField != null) trdDistKmField.setText(value); break;
            case "dampFact": if (trdDampFactField != null) trdDampFactField.setText(value); break;
            case "maxTripleDiffCount": if (trdMaxTripleDiffCountField != null) trdMaxTripleDiffCountField.setText(value); break;
            case "atol": if (lsqrAtolField != null) lsqrAtolField.setText(value); break;
            case "btol": if (lsqrBtolField != null) lsqrBtolField.setText(value); break;
            case "conlim": if (lsqrConlimField != null) lsqrConlimField.setText(value); break;
            case "iter_lim": if (lsqrIterLimField != null) lsqrIterLimField.setText(value); break;
            case "showLSQR": if (lsqrShowLogCheckBox != null) lsqrShowLogCheckBox.setSelected("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim())); break;
            case "calcVar": if (lsqrCalcVarCheckBox != null) lsqrCalcVarCheckBox.setSelected("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim())); break;
            case "nSamples": if (nSamplesField != null) nSamplesField.setText(value); break;
            case "burnIn": if (burnInField != null) burnInField.setText(value); break;
            case "stepSize": if (stepSizeField != null) stepSizeField.setText(value); break;
            case "stepSizeDepth": if (stepSizeDepthField != null) stepSizeDepthField.setText(value); break;
            case "temperature": if (temperatureField != null) temperatureField.setText(value); break;
            case "populationSize": if (dePopulationSizeField != null) dePopulationSizeField.setText(value); break;
            case "maxGenerations": if (deMaxGenerationsField != null) deMaxGenerationsField.setText(value); break;
            case "scalingFactor": if (deScalingFactorField != null) deScalingFactorField.setText(value); break;
            case "crossoverRate": if (deCrossoverRateField != null) deCrossoverRateField.setText(value); break;
            case "minPts": if (minPtsField != null) minPtsField.setText(value); break;
            case "eps": if (epsField != null) epsField.setText(value); break;
            case "epsPercentile": if (epsPercentileField != null) epsPercentileField.setText(value); break;
            case "rmsThreshold": if (rmsThresholdField != null) rmsThresholdField.setText(value); break;
            case "locErrThreshold": if (locErrThresholdField != null) locErrThresholdField.setText(value); break;
            case "doClustering": setParameterInTable("doClustering", value); break;
            case "calcTripleDiff": setParameterInTable("calcTripleDiff", value); break;
            case "randomSeed": if (randomSeedField != null) randomSeedField.setText(value); break;
            case "phsErr": if (phsErrField != null) phsErrField.setText(value); break;
            case "locErr": if (locErrField != null) locErrField.setText(value); break;
            case "minSelectRate": if (minSelectRateField != null) minSelectRateField.setText(value); break;
            case "maxSelectRate": if (maxSelectRateField != null) maxSelectRateField.setText(value); break;
            default:
                break;
        }
    }
    
    /**
     * Validates required files and parameters for the given mode.
     * @param selectedMode mode abbreviation (GRD, LMO, MCMC, DE, TRD, CLS, SYN)
     * @return list of error messages; empty if all checks pass
     */
    private java.util.List<String> validateBeforeExecute(String selectedMode) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        syncSelectedTargetDirFromField();
        syncSelectedOutputDirFromField();
        if (selectedMode == null || selectedMode.isEmpty()) {
            errors.add("No mode selected.");
            return errors;
        }
        if ("SYN".equals(selectedMode)) {
            File catalogFile = getCatalogFileFromField();
            if (catalogFile == null || !catalogFile.exists()) {
                errors.add("Catalog file is required for SYN mode. Please select an existing catalog file.");
            }
            if (selectedOutputDir == null) {
                errors.add("Output directory is required. Please select an output directory.");
            } else {
                if (!selectedOutputDir.exists()) {
                    errors.add("Output directory does not exist: " + selectedOutputDir.getAbsolutePath());
                } else if (!selectedOutputDir.isDirectory()) {
                    errors.add("Output path is not a directory: " + selectedOutputDir.getAbsolutePath());
                }
            }
            if (selectedStationFile == null || !selectedStationFile.exists()) {
                errors.add("Station file is required. Please select an existing station file.");
            }
            return errors;
        }
        if ("CLS".equals(selectedMode)) {
            File catalogFile = getCatalogFileFromField();
            if (catalogFile == null || !catalogFile.exists()) {
                errors.add("Catalog file is required for CLS mode. Please select an existing catalog file.");
            }
            if (selectedOutputDir == null) {
                errors.add("Output directory is required. Please select an output directory.");
            } else {
                if (!selectedOutputDir.exists()) {
                    errors.add("Output directory does not exist: " + selectedOutputDir.getAbsolutePath());
                } else if (!selectedOutputDir.isDirectory()) {
                    errors.add("Output path is not a directory: " + selectedOutputDir.getAbsolutePath());
                }
            }
            if (selectedStationFile == null || !selectedStationFile.exists()) {
                errors.add("Station file is required. Please select an existing station file.");
            }
            String minPtsStr = getParameterValue("minPts");
            String epsStr = getParameterValue("eps");
            if (minPtsStr == null || minPtsStr.trim().isEmpty()) {
                errors.add("CLS: minPts is required.");
            }
            if (epsStr == null || epsStr.trim().isEmpty()) {
                errors.add("CLS: eps is required.");
            }
            return errors;
        }
        if ("TRD".equals(selectedMode)) {
            File catalogFile = getCatalogFileFromField();
            boolean hasCatalog = catalogFile != null && catalogFile.exists();
            boolean hasDatFiles = selectedTargetDir != null && !findDatFiles(selectedTargetDir).isEmpty();
            if (!hasCatalog && !hasDatFiles) {
                errors.add("TRD mode requires either an existing catalog file or a target directory containing .dat files.");
            }
            if (selectedTargetDir == null) {
                errors.add("Target directory (dat files) is required for TRD mode. Please select a target directory.");
            } else {
                if (!selectedTargetDir.exists()) {
                    errors.add("Target directory does not exist: " + selectedTargetDir.getAbsolutePath());
                } else if (!selectedTargetDir.isDirectory()) {
                    errors.add("Target path is not a directory: " + selectedTargetDir.getAbsolutePath());
                }
            }
            if (selectedOutputDir == null) {
                errors.add("Output directory is required. Please select an output directory.");
            } else {
                if (!selectedOutputDir.exists()) {
                    errors.add("Output directory does not exist: " + selectedOutputDir.getAbsolutePath());
                } else if (!selectedOutputDir.isDirectory()) {
                    errors.add("Output path is not a directory: " + selectedOutputDir.getAbsolutePath());
                }
            }
            if (selectedStationFile == null || !selectedStationFile.exists()) {
                errors.add("Station file is required. Please select an existing station file.");
            }
            String iterNumStr = getParameterValue("iterNum");
            String distKmStr = getParameterValue("distKm");
            String dampFactStr = getParameterValue("dampFact");
            if (iterNumStr.isEmpty() && trdIterNumField != null) iterNumStr = trdIterNumField.getText();
            if (distKmStr.isEmpty() && trdDistKmField != null) distKmStr = trdDistKmField.getText();
            if (dampFactStr.isEmpty() && trdDampFactField != null) dampFactStr = trdDampFactField.getText();
            if (iterNumStr == null || iterNumStr.trim().isEmpty()) {
                errors.add("TRD: iterNum is required.");
            } else if (distKmStr == null || distKmStr.trim().isEmpty()) {
                errors.add("TRD: distKm is required.");
            } else if (dampFactStr == null || dampFactStr.trim().isEmpty()) {
                errors.add("TRD: dampFact is required.");
            } else {
                String[] a = iterNumStr.split(",");
                String[] b = distKmStr.split(",");
                String[] c = dampFactStr.split(",");
                if (a.length != b.length || a.length != c.length) {
                    errors.add("TRD: iterNum, distKm, and dampFact must have the same number of comma-separated values.");
                }
            }
            return errors;
        }
        if (selectedTargetDir == null) {
            errors.add("Target directory is required. Please select a target directory containing .dat files.");
        } else {
            if (!selectedTargetDir.exists()) {
                errors.add("Target directory does not exist: " + selectedTargetDir.getAbsolutePath());
            } else if (!selectedTargetDir.isDirectory()) {
                errors.add("Target path is not a directory: " + selectedTargetDir.getAbsolutePath());
            }
        }
        if (selectedOutputDir == null) {
            errors.add("Output directory is required. Please select an output directory.");
        } else {
            if (!selectedOutputDir.exists()) {
                errors.add("Output directory does not exist: " + selectedOutputDir.getAbsolutePath());
            } else if (!selectedOutputDir.isDirectory()) {
                errors.add("Output path is not a directory: " + selectedOutputDir.getAbsolutePath());
            }
        }
        if (selectedStationFile == null || !selectedStationFile.exists()) {
            errors.add("Station file is required. Please select an existing station file.");
        }
        String thresholdStr = getParameterValue("threshold");
        String hypBottomStr = getParameterValue("hypBottom");
        if (thresholdStr == null || thresholdStr.trim().isEmpty()) {
            errors.add("Weight threshold (threshold) is required.");
        }
        if (hypBottomStr == null || hypBottomStr.trim().isEmpty()) {
            errors.add("Maximum depth (hypBottom) is required.");
        }
        if ("GRD".equals(selectedMode)) {
            String totalGridsStr = getParameterValue("totalGrids");
            String numFocusStr = getParameterValue("numFocus");
            if ((totalGridsStr == null || totalGridsStr.trim().isEmpty()) && (numFocusStr == null || numFocusStr.trim().isEmpty())) {
                errors.add("GRD: totalGrids or numFocus is required.");
            }
        } else if ("MCMC".equals(selectedMode)) {
            String nSamplesStr = getParameterValue("nSamples");
            String burnInStr = getParameterValue("burnIn");
            String stepSizeStr = getParameterValue("stepSize");
            String stepSizeDepthStr = getParameterValue("stepSizeDepth");
            String temperatureStr = getParameterValue("temperature");
            if (nSamplesStr == null || nSamplesStr.trim().isEmpty()) errors.add("MCMC: nSamples is required.");
            if (burnInStr == null || burnInStr.trim().isEmpty()) errors.add("MCMC: burnIn is required.");
            if (stepSizeStr == null || stepSizeStr.trim().isEmpty()) errors.add("MCMC: stepSize is required.");
            if (stepSizeDepthStr == null || stepSizeDepthStr.trim().isEmpty()) errors.add("MCMC: stepSizeDepth is required.");
            if (temperatureStr == null || temperatureStr.trim().isEmpty()) errors.add("MCMC: temperature is required.");
        } else if ("DE".equals(selectedMode)) {
            String populationSizeStr = dePopulationSizeField != null ? dePopulationSizeField.getText().trim() : "";
            String maxGenerationsStr = deMaxGenerationsField != null ? deMaxGenerationsField.getText().trim() : "";
            String scalingFactorStr = deScalingFactorField != null ? deScalingFactorField.getText().trim() : "";
            String crossoverRateStr = deCrossoverRateField != null ? deCrossoverRateField.getText().trim() : "";
            if (populationSizeStr.isEmpty()) errors.add("DE: populationSize is required.");
            if (maxGenerationsStr.isEmpty()) errors.add("DE: maxGenerations is required.");
            if (scalingFactorStr.isEmpty()) errors.add("DE: scalingFactor is required.");
            if (crossoverRateStr.isEmpty()) errors.add("DE: crossoverRate is required.");
        }
        return errors;
    }
    
    /**
     * Resets UI after a run finishes (success, failure, or cancel). Ensures Execute is enabled and Cancel disabled
     * so the user can run again. Call from worker's done() in a finally block.
     */
    private void resetExecutionStateAfterRun() {
        SolverLogger.setSuppressInfoInCallback(false);
        SolverLogger.setMode(true, false, false);
        SolverLogger.setCallback(null);
        if (executeButton != null) {
            executeButton.setEnabled(true);
        }
        if (cancelButton != null) {
            cancelButton.setEnabled(false);
        }
        updateExecuteButtonState();
    }
    
    private void updateExecuteButtonState() {
        if (executeButton == null) {
            return;
        }
        
        if (currentWorker != null && !currentWorker.isDone()) {
            executeButton.setEnabled(false);
            if (cancelButton != null) {
                cancelButton.setEnabled(true);
            }
            return;
        }
        
        String selectedMode = getSelectedModeAbbreviation();
        boolean canExecute = selectedMode != null && !selectedMode.isEmpty();
        
        executeButton.setEnabled(canExecute);
        if (cancelButton != null) {
            cancelButton.setEnabled(false);
        }
        
        if (canExecute) {
            executeButton.setToolTipText("Run location. Missing files or invalid settings will be reported in Execution log.");
        } else {
            executeButton.setToolTipText("Select a mode to enable Execute.");
        }
    }
    
    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String line = "[" + java.time.LocalTime.now().toString() + "] " + message + "\n";
            Color color = colorForLogMessage(message);
            appendLogLine(line, color);
        });
    }

    /**
     * Appends a TSV block on the EDT (no timestamp prefix on data rows so paste into spreadsheets stays aligned).
     */
    private void appendLogTsvSection(java.util.List<String> tsvLines) {
        if (tsvLines == null || tsvLines.isEmpty()) {
            return;
        }
        if (logArea == null) {
            return;
        }
        String ts = "[" + java.time.LocalTime.now() + "] ";
        appendLogLine(ts + "--- Solver run summary (tab-separated; copy lines below into a spreadsheet) ---\n",
            LOG_SECTION_HEADER_COLOR);
        for (String line : tsvLines) {
            appendLogLine(line + "\n", new Color(200, 230, 200));
        }
    }

    /**
     * Appends one line to the Execution log with a Java logging level (used by {@link com.treloc.xtreloc.app.gui.util.GuiExecutionLog}).
     */
    public void appendExecutionLog(Level level, String message) {
        Runnable r = () -> {
            if (logArea == null) {
                return;
            }
            String body = level.getName() + ": " + message;
            String line = "[" + java.time.LocalTime.now() + "] " + body + "\n";
            appendLogLine(line, colorForLevel(level));
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private static Color colorForLevel(Level level) {
        int v = level.intValue();
        if (v >= Level.SEVERE.intValue()) {
            return new Color(255, 100, 100);
        }
        if (v >= Level.WARNING.intValue()) {
            return new Color(255, 220, 100);
        }
        if (v >= Level.INFO.intValue()) {
            return new Color(180, 255, 180);
        }
        if (v >= Level.CONFIG.intValue()) {
            return new Color(160, 220, 255);
        }
        return new Color(140, 200, 200);
    }
    
    /** Slightly darker cyan for "Application Started" and "Current Session" section headers (not affected by previous error color). */
    private static final Color LOG_SECTION_HEADER_COLOR = new Color(80, 190, 220);

    /**
     * Chooses text color for Execution log based on message content (Warning, Severe, Error).
     * Also recognizes Java log format in history (e.g. "2026-03-10 22:44:07 WARNING ...").
     * Only log-level ERROR/SEVERE are red; exception continuation lines (e.g. "java.io.IOException...")
     * are not colored red so they stay part of the same warning block.
     */
    private static Color colorForLogMessage(String message) {
        if (message == null) return new Color(180, 255, 180);
        String m = message.trim();
        int rb = m.indexOf("] ");
        if (rb >= 0 && rb + 2 < m.length()) {
            int c2 = m.indexOf(':', rb + 2);
            if (c2 > rb + 2) {
                try {
                    return colorForLevel(Level.parse(m.substring(rb + 2, c2).trim()));
                } catch (IllegalArgumentException ignored) {
                    // fall through
                }
            }
        }
        if (m.startsWith("ERROR:") || m.startsWith("Error:")) {
            return new Color(255, 100, 100);
        }
        if (m.startsWith("Warning:") || m.startsWith("WARNING:")) {
            return new Color(255, 220, 100);
        }
        if (m.startsWith("Severe") || m.startsWith("SEVERE") || m.contains(" SEVERE ")) {
            return new Color(255, 165, 80);
        }
        if (m.contains(" WARNING ") || m.contains("\tWARNING ")) {
            return new Color(255, 220, 100);
        }
        if (m.contains(" SEVERE ") || m.contains("\tSEVERE ")) {
            return new Color(255, 165, 80);
        }
        if (m.contains(" ERROR ") || m.contains("\tERROR ")) {
            return new Color(255, 100, 100);
        }
        return new Color(180, 255, 180);
    }
    
    /**
     * Appends a line to the Execution log with the given text color.
     */
    private void appendLogLine(String line, Color color) {
        if (logArea == null) return;
        try {
            StyledDocument doc = logArea.getStyledDocument();
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, color);
            doc.insertString(doc.getLength(), line, attr);
            logArea.setCaretPosition(doc.getLength());
            if (logScrollPane != null) {
                JScrollBar bar = logScrollPane.getVerticalScrollBar();
                if (bar != null) bar.setValue(bar.getMaximum());
                logArea.repaint();
            }
        } catch (javax.swing.text.BadLocationException e) {
            logger.warning("Execution log append failed: " + e.getMessage());
        }
    }

    /**
     * Sets the entire log content (e.g. previous session history) with per-line color.
     * A line is treated as a new log record if it starts with a timestamp (yyyy-MM-dd or [HH:mm:ss]);
     * otherwise it is a continuation of the previous message and gets the same color.
     */
    private void setLogContentWithColors(String fullText) {
        if (logArea == null) return;
        try {
            StyledDocument doc = logArea.getStyledDocument();
            doc.remove(0, doc.getLength());
            if (fullText == null || fullText.isEmpty()) return;
            String[] lines = fullText.split("\n", -1);
            Color lastColor = new Color(180, 255, 180);
            for (String line : lines) {
                String lineWithNewline = line + "\n";
                Color color;
                if (isLogSectionHeader(line)) {
                    color = LOG_SECTION_HEADER_COLOR;
                    lastColor = color;
                } else if (isNewLogRecord(line)) {
                    color = colorForLogMessage(line);
                    lastColor = color;
                } else {
                    color = lastColor;
                }
                SimpleAttributeSet attr = new SimpleAttributeSet();
                StyleConstants.setForeground(attr, color);
                doc.insertString(doc.getLength(), lineWithNewline, attr);
            }
            logArea.setCaretPosition(doc.getLength());
            if (logScrollPane != null) {
                JScrollBar bar = logScrollPane.getVerticalScrollBar();
                if (bar != null) bar.setValue(bar.getMaximum());
            }
        } catch (Exception e) {
            logger.warning("Failed to set log content with colors: " + e.getMessage());
        }
    }

    /** True if the line is a log section header (Application Started, Current Session, or separator); use fixed header color. */
    private static boolean isLogSectionHeader(String line) {
        if (line == null) return false;
        String t = line.trim();
        if (t.isEmpty()) return false;
        if (t.startsWith("Application Started") || t.equals("Current Session")) return true;
        if (t.length() > 0 && (t.charAt(0) == '═' || t.charAt(0) == '─')) return true;
        return false;
    }

    /** True if the line looks like the start of a new log record (timestamp at start), not a continuation. */
    private static boolean isNewLogRecord(String line) {
        if (line == null || line.isEmpty()) return false;
        String t = line.trim();
        if (t.length() >= 10 && t.charAt(0) >= '0' && t.charAt(0) <= '9' && t.charAt(4) == '-' && t.charAt(7) == '-') {
            return true;
        }
        if (t.startsWith("[")) return true;
        return false;
    }
    
    private void appendConvergenceLog(String message) {
        if (!shouldStreamConvergenceToExecutionLog()) {
            return;
        }
        appendLog("Convergence: " + message);
    }
    
    /**
     * Loads log history from settings and displays it in the Execution Log panel.
     * Uses the configured history line count; on failure uses default and still tries to load from log file.
     * On Ubuntu/Linux, settings or timing may cause failures — we log the cause and still attempt to read the log file.
     * If the first read returns no history (e.g. log file not yet created at startup), retries once after a short delay.
     */
    private void loadLogHistory() {
        int historyLines = com.treloc.xtreloc.app.gui.util.AppSettingsCache.snapshot().getHistoryLines();
        final int linesToLoad = historyLines;
        String history = loadLogHistoryImpl(linesToLoad);
        final boolean hadNoHistory = history != null && history.contains("(No previous log history)");
        final String toShow = history != null ? history : (
            "═══════════════════════════════════════════════════════════════\n"
            + "Application Started - Previous Log History (unable to load)\n"
            + "═══════════════════════════════════════════════════════════════\n"
            + "(No previous log history)\n"
            + "───────────────────────────────────────────────────────────────\n"
            + "Current Session\n"
            + "───────────────────────────────────────────────────────────────\n"
        );
        SwingUtilities.invokeLater(() -> {
            setLogContentWithColors(toShow);
        });
        if (hadNoHistory) {
            javax.swing.Timer retryTimer = new javax.swing.Timer(500, e -> {
                String retryHistory = loadLogHistoryImpl(linesToLoad);
                if (retryHistory != null && retryHistory.contains("lines)\n") && !retryHistory.contains("(0 lines)")) {
                    SwingUtilities.invokeLater(() -> {
                        setLogContentWithColors(retryHistory);
                    });
                }
            });
            retryTimer.setRepeats(false);
            retryTimer.start();
        }
    }
    
    private String loadLogHistoryImpl(int historyLines) {
        try {
            return com.treloc.xtreloc.app.gui.util.LogHistoryManager.getHistoryForGUI(historyLines);
        } catch (Exception e) {
            logger.warning("Could not read log history file (e.g. path or permission on Linux): " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Builds an {@link AppConfig} from the current UI state (shared fields plus current mode parameters).
     * Used by {@link #executeLocation()} and by config export on Execute.
     *
     * @return the constructed config, never null
     */
    private AppConfig buildConfigFromUI() {
        AppConfig cfg = new AppConfig();
        
        String selectedModel = (String) taupModelCombo.getSelectedItem();
        if (selectedModel != null && !"Select from file...".equals(selectedModel)) {
            cfg.taupFile = selectedModel;
        }
        cfg.raytraceMethod = com.treloc.xtreloc.app.gui.util.AppSettingsCache.snapshot().getRaytraceMethod();
        if (selectedStationFile != null) {
            cfg.stationFile = selectedStationFile.getAbsolutePath();
        }
        
        String numJobsStr = getParameterValue("numJobs");
        String thresholdStr = getParameterValue("threshold");
        String hypBottomStr = getParameterValue("hypBottom");
        if (!numJobsStr.isEmpty()) {
            cfg.numJobs = Integer.parseInt(numJobsStr);
        }
        if (!thresholdStr.isEmpty()) {
            cfg.threshold = Double.parseDouble(thresholdStr);
        }
        if (!hypBottomStr.isEmpty()) {
            cfg.hypBottom = Double.parseDouble(hypBottomStr);
        }
        
        String selectedMode = getSelectedModeAbbreviation();
        cfg.io = new java.util.HashMap<>();
        cfg.params = new java.util.HashMap<>();
        
        if ("GRD".equals(selectedMode)) {
            var grdIO = new AppConfig.ModeIOConfig();
            if (selectedTargetDir != null) grdIO.datDirectory = selectedTargetDir.toPath();
            if (selectedOutputDir != null) grdIO.outDirectory = selectedOutputDir.toPath();
            cfg.io.put("GRD", grdIO);
            var grdSolver = new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
            String totalGridsStr = getParameterValue("totalGrids");
            String numFocusStr = getParameterValue("numFocus");
            if (!totalGridsStr.isEmpty()) {
                grdSolver.put("totalGrids", Integer.parseInt(totalGridsStr));
            }
            if (!numFocusStr.isEmpty()) {
                grdSolver.put("numFocus", Integer.parseInt(numFocusStr));
            }
            cfg.params.put("GRD", grdSolver);
        } else if ("LMO".equals(selectedMode)) {
            var lmoIO = new AppConfig.ModeIOConfig();
            if (selectedTargetDir != null) lmoIO.datDirectory = selectedTargetDir.toPath();
            if (selectedOutputDir != null) lmoIO.outDirectory = selectedOutputDir.toPath();
            cfg.io.put("LMO", lmoIO);
            var lmoSolver = new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
                String showConvergenceLogStr = getParameterValue("showConvergenceLog");
                boolean showConvergenceLog = false;
                if (!showConvergenceLogStr.isEmpty()) {
                    showConvergenceLog = "true".equalsIgnoreCase(showConvergenceLogStr.trim()) || "1".equals(showConvergenceLogStr.trim());
                } else if (showConvergenceLogCheckBox != null) {
                    showConvergenceLog = showConvergenceLogCheckBox.isSelected();
                }
                lmoSolver.put("showConvergenceLog", showConvergenceLog);
                if (showConvergenceLogCheckBox != null) {
                    showConvergenceLogCheckBox.setSelected(showConvergenceLog);
                }
                
                String initialStepBoundStr = getParameterValue("initialStepBoundFactor");
                String costRelativeTolStr = getParameterValue("costRelativeTolerance");
                String parRelativeTolStr = getParameterValue("parRelativeTolerance");
                String orthoTolStr = getParameterValue("orthoTolerance");
                String maxEvalStr = getParameterValue("maxEvaluations");
                String maxIterStr = getParameterValue("maxIterations");
                
                if (initialStepBoundStr.isEmpty() && lmInitialStepBoundField != null) {
                    initialStepBoundStr = lmInitialStepBoundField.getText();
                }
                if (costRelativeTolStr.isEmpty() && lmCostRelativeToleranceField != null) {
                    costRelativeTolStr = lmCostRelativeToleranceField.getText();
                }
                if (parRelativeTolStr.isEmpty() && lmParRelativeToleranceField != null) {
                    parRelativeTolStr = lmParRelativeToleranceField.getText();
                }
                if (orthoTolStr.isEmpty() && lmOrthoToleranceField != null) {
                    orthoTolStr = lmOrthoToleranceField.getText();
                }
                if (maxEvalStr.isEmpty() && lmMaxEvaluationsField != null) {
                    maxEvalStr = lmMaxEvaluationsField.getText();
                }
                if (maxIterStr.isEmpty() && lmMaxIterationsField != null) {
                    maxIterStr = lmMaxIterationsField.getText();
                }
                
                if (!initialStepBoundStr.isEmpty()) {
                    lmoSolver.put("initialStepBoundFactor", Double.parseDouble(initialStepBoundStr));
                }
                if (!costRelativeTolStr.isEmpty()) {
                    lmoSolver.put("costRelativeTolerance", Double.parseDouble(costRelativeTolStr));
                }
                if (!parRelativeTolStr.isEmpty()) {
                    lmoSolver.put("parRelativeTolerance", Double.parseDouble(parRelativeTolStr));
                }
                if (!orthoTolStr.isEmpty()) {
                    lmoSolver.put("orthoTolerance", Double.parseDouble(orthoTolStr));
                }
                if (!maxEvalStr.isEmpty()) {
                    lmoSolver.put("maxEvaluations", Integer.parseInt(maxEvalStr));
                }
                if (!maxIterStr.isEmpty()) {
                    lmoSolver.put("maxIterations", Integer.parseInt(maxIterStr));
                }
            cfg.params.put("LMO", lmoSolver);
        } else if ("TRD".equals(selectedMode)) {
            var trdIO = new AppConfig.ModeIOConfig();
            File catalogFile = getCatalogFileFromField();
            if (catalogFile != null && catalogFile.exists()) {
                trdIO.catalogFile = catalogFile.getAbsolutePath();
            }
            if (selectedTargetDir != null) trdIO.datDirectory = selectedTargetDir.toPath();
            if (selectedOutputDir != null) trdIO.outDirectory = selectedOutputDir.toPath();
            cfg.io.put("TRD", trdIO);
            var trdSolver = new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
                String iterNumStr = getParameterValue("iterNum");
                String distKmStr = getParameterValue("distKm");
                String dampFactStr = getParameterValue("dampFact");
                
                if (iterNumStr.isEmpty() && trdIterNumField != null) {
                    iterNumStr = trdIterNumField.getText();
                }
                if (distKmStr.isEmpty() && trdDistKmField != null) {
                    distKmStr = trdDistKmField.getText();
                }
                if (dampFactStr.isEmpty() && trdDampFactField != null) {
                    dampFactStr = trdDampFactField.getText();
                }
                
                if (!iterNumStr.isEmpty()) {
                    String[] iterNumArray = iterNumStr.split(",");
                    var iterNumNode = trdSolver.putArray("iterNum");
                    for (String val : iterNumArray) {
                        iterNumNode.add(Integer.parseInt(val.trim()));
                    }
                }
                if (!distKmStr.isEmpty()) {
                    String[] distKmArray = distKmStr.split(",");
                    var distKmNode = trdSolver.putArray("distKm");
                    for (String val : distKmArray) {
                        distKmNode.add(Integer.parseInt(val.trim()));
                    }
                }
                if (!dampFactStr.isEmpty()) {
                    String[] dampFactArray = dampFactStr.split(",");
                    var dampFactNode = trdSolver.putArray("dampFact");
                    for (String val : dampFactArray) {
                        dampFactNode.add(Integer.parseInt(val.trim()));
                    }
                }
                String maxTripleDiffCountStr = getParameterValue("maxTripleDiffCount");
                if (maxTripleDiffCountStr.isEmpty() && trdMaxTripleDiffCountField != null) {
                    maxTripleDiffCountStr = trdMaxTripleDiffCountField.getText();
                }
                if (!maxTripleDiffCountStr.trim().isEmpty()) {
                    try {
                        int maxCount = Integer.parseInt(maxTripleDiffCountStr.trim());
                        if (maxCount > 0) trdSolver.put("maxTripleDiffCount", maxCount);
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid maxTripleDiffCount: " + maxTripleDiffCountStr);
                    }
                }

                String showConvergenceLogStr = getParameterValue("showConvergenceLog");
                boolean showConvergenceLog = false;
                if (!showConvergenceLogStr.isEmpty()) {
                    showConvergenceLog = "true".equalsIgnoreCase(showConvergenceLogStr.trim()) || "1".equals(showConvergenceLogStr.trim());
                } else if (showConvergenceLogCheckBox != null) {
                    showConvergenceLog = showConvergenceLogCheckBox.isSelected();
                }
                trdSolver.put("showConvergenceLog", showConvergenceLog);
                if (showConvergenceLogCheckBox != null) {
                    showConvergenceLogCheckBox.setSelected(showConvergenceLog);
                }
                
                if (lsqrAtolField != null && !lsqrAtolField.getText().trim().isEmpty()) {
                    try {
                        double atol = Double.parseDouble(lsqrAtolField.getText().trim());
                        trdSolver.put("lsqrAtol", atol);
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid LSQR ATOL value: " + lsqrAtolField.getText());
                    }
                }
                if (lsqrBtolField != null && !lsqrBtolField.getText().trim().isEmpty()) {
                    try {
                        double btol = Double.parseDouble(lsqrBtolField.getText().trim());
                        trdSolver.put("lsqrBtol", btol);
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid LSQR BTOL value: " + lsqrBtolField.getText());
                    }
                }
                if (lsqrConlimField != null && !lsqrConlimField.getText().trim().isEmpty()) {
                    try {
                        double conlim = Double.parseDouble(lsqrConlimField.getText().trim());
                        trdSolver.put("lsqrConlim", conlim);
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid LSQR CONLIM value: " + lsqrConlimField.getText());
                    }
                }
                if (lsqrIterLimField != null && !lsqrIterLimField.getText().trim().isEmpty()) {
                    try {
                        int iterLim = Integer.parseInt(lsqrIterLimField.getText().trim());
                        trdSolver.put("lsqrIterLim", iterLim);
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid LSQR Iteration Limit value: " + lsqrIterLimField.getText());
                    }
                }
                if (lsqrShowLogCheckBox != null) {
                    trdSolver.put("lsqrShowLog", lsqrShowLogCheckBox.isSelected());
                }
                if (lsqrCalcVarCheckBox != null) {
                    trdSolver.put("lsqrCalcVar", lsqrCalcVarCheckBox.isSelected());
                }
            cfg.params.put("TRD", trdSolver);
        } else if ("MCMC".equals(selectedMode)) {
            var mcmcIO = new AppConfig.ModeIOConfig();
            if (selectedTargetDir != null) mcmcIO.datDirectory = selectedTargetDir.toPath();
            if (selectedOutputDir != null) mcmcIO.outDirectory = selectedOutputDir.toPath();
            cfg.io.put("MCMC", mcmcIO);
            var mcmcSolver = new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
                String nSamplesStr = getParameterValue("nSamples");
                String burnInStr = getParameterValue("burnIn");
                String stepSizeStr = getParameterValue("stepSize");
                String stepSizeDepthStr = getParameterValue("stepSizeDepth");
                String temperatureStr = getParameterValue("temperature");
                if (!nSamplesStr.isEmpty()) {
                    mcmcSolver.put("nSamples", Integer.parseInt(nSamplesStr));
                }
                if (!burnInStr.isEmpty()) {
                    mcmcSolver.put("burnIn", Integer.parseInt(burnInStr));
                }
                if (!stepSizeStr.isEmpty()) {
                    mcmcSolver.put("stepSize", Double.parseDouble(stepSizeStr));
                }
                if (!stepSizeDepthStr.isEmpty()) {
                    mcmcSolver.put("stepSizeDepth", Double.parseDouble(stepSizeDepthStr));
                }
                if (!temperatureStr.isEmpty()) {
                    mcmcSolver.put("temperature", Double.parseDouble(temperatureStr));
                }
                String showConvergenceLogStr = getParameterValue("showConvergenceLog");
                boolean showConvergenceLog = false;
                if (!showConvergenceLogStr.isEmpty()) {
                    showConvergenceLog = "true".equalsIgnoreCase(showConvergenceLogStr.trim()) || "1".equals(showConvergenceLogStr.trim());
                } else if (showConvergenceLogCheckBox != null) {
                    showConvergenceLog = showConvergenceLogCheckBox.isSelected();
                }
                mcmcSolver.put("showConvergenceLog", showConvergenceLog);
                if (showConvergenceLogCheckBox != null) {
                    showConvergenceLogCheckBox.setSelected(showConvergenceLog);
                }
            cfg.params.put("MCMC", mcmcSolver);
        } else if ("DE".equals(selectedMode)) {
            var deIO = new AppConfig.ModeIOConfig();
            if (selectedTargetDir != null) deIO.datDirectory = selectedTargetDir.toPath();
            if (selectedOutputDir != null) deIO.outDirectory = selectedOutputDir.toPath();
            cfg.io.put("DE", deIO);
            var deSolver = new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
                String populationSizeStr = dePopulationSizeField != null ? dePopulationSizeField.getText().trim() : "";
                String maxGenerationsStr = deMaxGenerationsField != null ? deMaxGenerationsField.getText().trim() : "";
                String scalingFactorStr = deScalingFactorField != null ? deScalingFactorField.getText().trim() : "";
                String crossoverRateStr = deCrossoverRateField != null ? deCrossoverRateField.getText().trim() : "";
                if (!populationSizeStr.isEmpty()) {
                    deSolver.put("populationSize", Integer.parseInt(populationSizeStr));
                }
                if (!maxGenerationsStr.isEmpty()) {
                    deSolver.put("maxGenerations", Integer.parseInt(maxGenerationsStr));
                }
                if (!scalingFactorStr.isEmpty()) {
                    deSolver.put("scalingFactor", Double.parseDouble(scalingFactorStr));
                }
                if (!crossoverRateStr.isEmpty()) {
                    deSolver.put("crossoverRate", Double.parseDouble(crossoverRateStr));
                }
                String showConvergenceLogStr = getParameterValue("showConvergenceLog");
                boolean showConvergenceLog = false;
                if (!showConvergenceLogStr.isEmpty()) {
                    showConvergenceLog = "true".equalsIgnoreCase(showConvergenceLogStr.trim()) || "1".equals(showConvergenceLogStr.trim());
                } else if (showConvergenceLogCheckBox != null) {
                    showConvergenceLog = showConvergenceLogCheckBox.isSelected();
                }
                deSolver.put("showConvergenceLog", showConvergenceLog);
                if (showConvergenceLogCheckBox != null) {
                    showConvergenceLogCheckBox.setSelected(showConvergenceLog);
                }
            cfg.params.put("DE", deSolver);
        } else if ("CLS".equals(selectedMode)) {
            var clsIO = new AppConfig.ModeIOConfig();
            File catalogFile = getCatalogFileFromField();
            if (catalogFile != null && catalogFile.exists()) {
                clsIO.catalogFile = catalogFile.getAbsolutePath();
            }
            if (selectedOutputDir != null) {
                clsIO.outDirectory = selectedOutputDir.toPath();
            }
            if (selectedTargetDir != null) {
                clsIO.datDirectory = selectedTargetDir.toPath();
            }
            cfg.io.put("CLS", clsIO);
            String minPtsStr = getParameterValue("minPts");
            String epsStr = getParameterValue("eps");
            String epsPercentileStr = getParameterValue("epsPercentile");
            String rmsThresholdStr = getParameterValue("rmsThreshold");
            String locErrThresholdStr = getParameterValue("locErrThreshold");
            String doClusteringStr = getParameterValue("doClustering");
            String calcTripleDiffStr = getParameterValue("calcTripleDiff");
            var clsSolver = new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
            if (!minPtsStr.isEmpty()) {
                clsSolver.put("minPts", Integer.parseInt(minPtsStr));
            }
            if (!epsStr.isEmpty()) {
                clsSolver.put("eps", Double.parseDouble(epsStr));
            }
            if (!epsPercentileStr.isEmpty()) {
                try {
                    double p = Double.parseDouble(epsPercentileStr);
                    if (p > 0 && p <= 1) clsSolver.put("epsPercentile", p);
                } catch (NumberFormatException e) {
                    logger.log(Level.FINE, "CLS epsPercentile ignored (not a number in (0,1]): " + epsPercentileStr, e);
                }
            }
            if (!rmsThresholdStr.isEmpty()) {
                try {
                    clsSolver.put("rmsThreshold", Double.parseDouble(rmsThresholdStr));
                } catch (NumberFormatException e) {
                    logger.log(Level.FINE, "CLS rmsThreshold ignored (invalid number): " + rmsThresholdStr, e);
                }
            }
            if (!locErrThresholdStr.isEmpty()) {
                try {
                    clsSolver.put("locErrThreshold", Double.parseDouble(locErrThresholdStr));
                } catch (NumberFormatException e) {
                    logger.log(Level.FINE, "CLS locErrThreshold ignored (invalid number): " + locErrThresholdStr, e);
                }
            }
            clsSolver.put("useBinaryFormat", true);
            if (!doClusteringStr.isEmpty()) {
                clsSolver.put("doClustering", "true".equalsIgnoreCase(doClusteringStr.trim()) || "1".equals(doClusteringStr.trim()));
            }
            if (!calcTripleDiffStr.isEmpty()) {
                clsSolver.put("calcTripleDiff", "true".equalsIgnoreCase(calcTripleDiffStr.trim()) || "1".equals(calcTripleDiffStr.trim()));
            }
            cfg.params.put("CLS", clsSolver);
        }
        
        return cfg;
    }
    
    private void executeLocation() {
        config = buildConfigFromUI();
        String selectedMode = getSelectedModeAbbreviation();
        
        java.util.List<String> validationErrors = validateBeforeExecute(selectedMode);
        if (!validationErrors.isEmpty()) {
            appendLog("Execution aborted — validation failed:");
            for (String msg : validationErrors) {
                appendLog("ERROR: " + msg);
            }
            logger.warning("Execute aborted: validation failed for mode " + selectedMode);
            return;
        }
        
        if (residualPlotPanel != null) {
            residualPlotPanel.setMode(selectedMode);
            residualPlotPanel.clearData();
        }
        
        if ("SYN".equals(selectedMode)) {
            executeSyntheticTest();
            return;
        }
        
        if ("CLS".equals(selectedMode)) {
            executeClustering();
            return;
        }
        
        if (selectedOutputDir == null) {
            appendLog("ERROR: Please select an output directory.");
            logger.warning("Execute aborted: no output directory selected.");
            return;
        }
        
        if (!selectedOutputDir.exists()) {
            String errorMsg = String.format(
                "Output directory does not exist: %s\n" +
                "  Please create the directory before running the task.",
                selectedOutputDir.getAbsolutePath());
            appendLog("ERROR: " + errorMsg);
            logger.warning(errorMsg);
            return;
        }
        if (!selectedOutputDir.isDirectory()) {
            String errorMsg = String.format(
                "Output path is not a directory: %s\n" +
                "  Please select a valid directory.",
                selectedOutputDir.getAbsolutePath());
            appendLog("ERROR: " + errorMsg);
            logger.warning(errorMsg);
            return;
        }
        
        File outputDir = selectedOutputDir;
        
        final String currentMode = getSelectedModeAbbreviation();
        if (selectedTargetDir != null) {
            if (!selectedTargetDir.exists()) {
                String errorMsg = String.format(
                    "Target directory does not exist: %s\n" +
                    "  Please select a valid target directory.",
                    selectedTargetDir.getAbsolutePath());
                appendLog("ERROR: " + errorMsg);
                logger.warning(errorMsg);
                return;
            }
            if (!selectedTargetDir.isDirectory()) {
                String errorMsg = String.format(
                    "Target path is not a directory: %s\n" +
                    "  Please select a valid directory.",
                    selectedTargetDir.getAbsolutePath());
                appendLog("ERROR: " + errorMsg);
                logger.warning(errorMsg);
                return;
            }
        }
        
        if (selectedTargetDir != null && selectedOutputDir != null) {
            try {
                String inputPath = selectedTargetDir.getCanonicalPath();
                String outputPath = selectedOutputDir.getCanonicalPath();
                if (inputPath.equals(outputPath)) {
                    int overwriteResult = JOptionPane.showConfirmDialog(this,
                        "Input directory and output directory are the same.\n" +
                        "Existing .dat files will be overwritten.\n" +
                        "Do you want to continue?",
                        "Overwrite Warning",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                    if (overwriteResult != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to compare directory paths: " + e.getMessage());
            }
        }
        
        if ("TRD".equals(currentMode)) {
            File catalogFile = getCatalogFileFromField();
            if (catalogFile == null || !catalogFile.exists()) {
                appendLog("Error: Catalog file is required for TRD mode.");
                logger.warning("TRD: catalog file required.");
                return;
            }
            if (selectedTargetDir == null) {
                appendLog("Error: Target directory (dat files) is required for TRD mode.");
                logger.warning("TRD: target directory required.");
                return;
            }
            appendLog("Catalog file: " + catalogFile.getAbsolutePath());
            appendLog("Target directory: " + selectedTargetDir.getAbsolutePath());
        } else {
            List<File> datFiles = findDatFiles(selectedTargetDir);
            if (datFiles.isEmpty()) {
                appendLog("Error: No .dat files found in the selected directory.");
                logger.info("Execute aborted: no .dat files in target directory.");
                return;
            }
            appendLog("Number of .dat files found: " + datFiles.size());
        }
        
        /* Snapshot Swing-backed state on EDT — do not read JTextField / combo from worker threads. */
        final File snapshotOutputCatalogFile = getOutputFileFromField();
        final File snapshotInputCatalogFile = getCatalogFileFromField();
        
        currentWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                SolverLogger.setMode(true, false, true);
                SolverLogger.setCallback((msg, level) -> {
                    if (level.intValue() >= Level.INFO.intValue()) {
                        appendLog(level.getName() + ": " + msg);
                    }
                });
                publish("Execution started...");
                try {
                if ("TRD".equals(currentMode)) {
                    try {
                        var trdMode = config.getModes().get("TRD");
                        if (trdMode != null) {
                            if (trdMode.catalogFile == null || trdMode.catalogFile.isEmpty()) {
                                throw new IllegalArgumentException("Catalog file is required for TRD mode. Please select a catalog file.");
                            }
                            
                            File catalogFileObj = new File(trdMode.catalogFile);
                            if (!catalogFileObj.exists()) {
                                throw new IllegalArgumentException("Specified catalog file does not exist: " + trdMode.catalogFile);
                            }
                            
                            publish("TRD mode: Processing catalog file...");
                            publish("Catalog file: " + trdMode.catalogFile);
                            
                            if (trdMode.datDirectory != null) {
                                publish("Target directory (binary file search location): " + trdMode.datDirectory);
                            } else {
                                throw new IllegalArgumentException("Target directory (datDirectory) is required for TRD mode. Please select a target directory.");
                            }
                        }
                        
                        if (isCancelled()) {
                            publish("Cancelled");
                            return null;
                        }
                        
                        com.treloc.xtreloc.solver.HypoTripleDiff solver = 
                            new com.treloc.xtreloc.solver.HypoTripleDiff(config);
                        solver.setLogConsumer(message -> publish("LSQR: " + message));
                        try {
                            solver.start("", "");
                        } catch (RuntimeException e) {
                            if (isCancelled() || e.getMessage() != null && e.getMessage().contains("interrupted")) {
                                publish("Cancelled");
                                return null;
                            }
                            throw e;
                        }
                        
                        if (isCancelled()) {
                            publish("Cancelled");
                            return null;
                        }
                        
                        publish("TRD mode: Processing completed");
                        try {
                            String trdOutPath = solver.getLastOutputCatalogAbsolutePath();
                            if (trdOutPath != null) {
                                File trdCatalogFile = new File(trdOutPath);
                                if (trdCatalogFile.exists()) {
                                    java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> trdHypos =
                                        CatalogLoader.load(trdCatalogFile);
                                    if (solverResultCatalogPanel != null) {
                                        SwingUtilities.invokeLater(() -> solverResultCatalogPanel.addCatalogFromHypocenters(
                                            new java.util.ArrayList<>(trdHypos), trdCatalogFile.getName(), trdCatalogFile));
                                    } else if (mapView != null && !trdHypos.isEmpty()) {
                                        SwingUtilities.invokeLater(() -> {
                                            try {
                                                mapView.showHypocenters(trdHypos);
                                            } catch (Exception ex) {
                                                ExecutionFailureReporter.log(logger, "Map display after TRD failed.", ex);
                                            }
                                        });
                                    }
                                    publish("Viewer: loaded TRD output (" + trdHypos.size() + " events) — " + trdCatalogFile.getName());
                                }
                            }
                        } catch (IOException ioEx) {
                            publish("Warning: TRD finished but could not load output catalog into Viewer: " + ioEx.getMessage());
                            logger.log(Level.WARNING, "TRD Viewer auto-load", ioEx);
                        }
                    } catch (Exception e) {
                        if (isCancelled()) {
                            publish("Cancelled");
                            return null;
                        }
                        StringBuilder errorMsg = new StringBuilder("TRD mode execution error:\n");
                        errorMsg.append("  Error type: ").append(e.getClass().getName()).append("\n");
                        errorMsg.append("  Error message: ").append(e.getMessage()).append("\n");
                        if (e.getCause() != null) {
                            errorMsg.append("  Caused by: ").append(e.getCause().getClass().getName())
                                   .append(": ").append(e.getCause().getMessage()).append("\n");
                        }
                        String errorStr = errorMsg.toString();
                        publish(errorStr);
                        logger.severe(errorStr);
                        logger.log(Level.SEVERE, "TRD mode execution error", e);
                        throw e;
                    }
                    return null;
                }
                
                int numJobs = (config != null && config.numJobs > 0) ? config.numJobs : 
                    Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
                publish("Parallel processing: " + numJobs + " threads");
                
                if (residualPlotPanel != null) {
                    SwingUtilities.invokeLater(() -> {
                        residualPlotPanel.setMaxParallelJobs(numJobs);
                    });
                }
                
                AtomicInteger processedCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> allHypocenters = 
                    new java.util.concurrent.CopyOnWriteArrayList<>();

                List<File> datFiles = findDatFiles(selectedTargetDir);
                ConcurrentHashMap<String, SolverBatchSummary.Row> batchSummary = new ConcurrentHashMap<>();
                boolean multiEventBatch = datFiles.size() > 1;
                if (multiEventBatch) {
                    SolverLogger.setSuppressInfoInCallback(true);
                    publish("Batch: " + datFiles.size()
                        + " events — per-event INFO lines are omitted from this log; tab-separated summary follows.");
                }

                ExecutorService executor = (numJobs > 1)
                    ? BatchExecutorFactory.newFixedThreadPoolBounded(
                        numJobs, BatchExecutorFactory.suggestedQueueCapacity(datFiles.size()))
                    : null;
                
                try {
                    if (executor != null) {
                        List<Future<Void>> futures = new java.util.ArrayList<>();
                        
                        for (File datFile : datFiles) {
                            if (isCancelled()) {
                                publish("Cancelled");
                                break;
                            }
                            
                            File finalDatFile = datFile;
                            Future<Void> future = executor.submit(() -> {
                                if (isCancelled() || Thread.currentThread().isInterrupted()) {
                                    return null;
                                }
                                String inputPath = finalDatFile.getAbsolutePath();
                                String outputPath = new File(outputDir, finalDatFile.getName()).getAbsolutePath();
                                
                                try {
                                    if (isCancelled() || Thread.currentThread().isInterrupted()) {
                                        return null;
                                    }
                                    int current = processedCount.get() + errorCount.get() + 1;
                                    if (!multiEventBatch) {
                                        publish("Processing: " + finalDatFile.getName() + " (" + current + "/" + datFiles.size() + ")");
                                    }
                                    
                                    String mode = currentMode;
                                    String eventName = finalDatFile.getName();
                                    currentEventName = eventName;
                                    
                                    if (residualPlotPanel != null) {
                                        SwingUtilities.invokeLater(() -> {
                                            residualPlotPanel.startProcessingEvent(eventName);
                                        });
                                    }
                                    
                                    if ("GRD".equals(mode)) {
                                        HypoGridSearch solver = new HypoGridSearch(config);
                                        if (convergenceCallback != null) {
                                            solver.setConvergenceCallback(convergenceCallback);
                                        }
                                        solver.start(inputPath, outputPath);
                                    } else if ("LMO".equals(mode)) {
                                        HypoStationPairDiff solver = new HypoStationPairDiff(config);
                                        if (convergenceCallback != null) {
                                            solver.setConvergenceCallback(convergenceCallback);
                                        }
                                        solver.start(inputPath, outputPath);
                                    } else if ("MCMC".equals(mode)) {
                                        com.treloc.xtreloc.solver.HypoMCMC solver = new com.treloc.xtreloc.solver.HypoMCMC(config);
                                        if (convergenceCallback != null) {
                                            solver.setConvergenceCallback(convergenceCallback);
                                        }
                                        solver.start(inputPath, outputPath);
                                    } else if ("DE".equals(mode)) {
                                        com.treloc.xtreloc.solver.HypoDifferentialEvolution solver = 
                                            new com.treloc.xtreloc.solver.HypoDifferentialEvolution(config);
                                        if (convergenceCallback != null) {
                                            solver.setConvergenceCallback(convergenceCallback);
                                        }
                                        solver.start(inputPath, outputPath);
                                    } else if ("TRD".equals(mode)) {
                                        com.treloc.xtreloc.solver.HypoTripleDiff solver = 
                                            new com.treloc.xtreloc.solver.HypoTripleDiff(config);
                                        if (convergenceCallback != null) {
                                            solver.setConvergenceCallback(convergenceCallback);
                                        }
                                        solver.start(inputPath, outputPath);
                                    } else {
                                        throw new IllegalArgumentException("Unknown mode: " + mode);
                                    }
                                    SolverRunMetrics runMetrics = SolverRunMetricsContext.getAndClear();
                                    
                                    if (residualPlotPanel != null) {
                                        SwingUtilities.invokeLater(() -> {
                                            residualPlotPanel.stopProcessingEvent(eventName);
                                        });
                                    }
                                    processedCount.incrementAndGet();
                                    
                                    try {
                                        java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = 
                                            loadHypocentersFromDatFile(new File(outputPath));
                                        allHypocenters.addAll(hypocenters);
                                        batchSummary.put(finalDatFile.getName(),
                                            SolverBatchSummary.Row.fromMetrics(currentMode, finalDatFile.getName(), "OK",
                                                hypocenters.size(), runMetrics, ""));
                                        if (!multiEventBatch) {
                                            publish("Success: " + finalDatFile.getName() + " - " + hypocenters.size() + " hypocenter data");
                                        }
                                    } catch (Exception e) {
                                        batchSummary.put(finalDatFile.getName(),
                                            SolverBatchSummary.Row.fromMetrics(currentMode, finalDatFile.getName(), "WARN", 0,
                                                runMetrics, SolverBatchSummary.truncateNote(e.getMessage(), 240)));
                                        if (!multiEventBatch) {
                                            publish("Warning: Failed to load results from " + finalDatFile.getName() + ": " + e.getMessage());
                                        }
                                        logger.warning("Result loading error: " + finalDatFile.getName() + " - " + e.getMessage());
                                    }
                                    
                                } catch (Exception e) {
                                    if (isCancelled() || Thread.currentThread().isInterrupted() || 
                                        (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                                        return null;
                                    }
                                    errorCount.incrementAndGet();
                                    batchSummary.put(finalDatFile.getName(),
                                        new SolverBatchSummary.Row(currentMode, finalDatFile.getName(), "ERROR", 0,
                                            SolverBatchSummary.truncateNote(e.getMessage(), 240)));
                                    if (!multiEventBatch) {
                                        publish("Error: Skipping processing of " + finalDatFile.getName() + ": " + e.getMessage());
                                    }
                                    logger.warning("File processing error: " + finalDatFile.getName() + " - " + e.getMessage());
                                }
                                return null;
                            });
                            futures.add(future);
                        }
                        
                        if (isCancelled()) {
                            publish("Cancelling all tasks...");
                            for (Future<Void> future : futures) {
                                future.cancel(true);
                            }
                            executor.shutdownNow();
                            publish("All tasks cancelled");
                        } else {
                            for (Future<Void> future : futures) {
                                if (isCancelled()) {
                                    future.cancel(true);
                                    executor.shutdownNow();
                                    break;
                                }
                                try {
                                    future.get();
                                } catch (java.util.concurrent.CancellationException e) {
                                    if (isCancelled()) {
                                        executor.shutdownNow();
                                        break;
                                    }
                                } catch (Exception e) {
                                    if (!isCancelled()) {
                                        logger.log(Level.WARNING, "Parallel batch task execution error", e);
                                    }
                                }
                            }
                        }
                    } else {
                        for (File datFile : datFiles) {
                            if (isCancelled()) {
                                publish("Cancelled");
                                break;
                            }
                            
                            String inputPath = datFile.getAbsolutePath();
                            String outputPath = new File(outputDir, datFile.getName()).getAbsolutePath();
                            
                            try {
                                int current = processedCount.get() + errorCount.get() + 1;
                                if (!multiEventBatch) {
                                    publish("Processing: " + datFile.getName() + " (" + current + "/" + datFiles.size() + ")");
                                }
                                
                                String mode = currentMode;
                                String eventName = datFile.getName();
                                currentEventName = eventName;
                                
                                if (residualPlotPanel != null) {
                                    SwingUtilities.invokeLater(() -> {
                                        residualPlotPanel.setActiveEvent(eventName);
                                    });
                                }
                                
                                if ("GRD".equals(mode)) {
                                    HypoGridSearch solver = new HypoGridSearch(config);
                                    if (convergenceCallback != null) {
                                        solver.setConvergenceCallback(convergenceCallback);
                                    }
                                    solver.start(inputPath, outputPath);
                                } else if ("LMO".equals(mode)) {
                                    HypoStationPairDiff solver = new HypoStationPairDiff(config);
                                    if (convergenceCallback != null) {
                                        solver.setConvergenceCallback(convergenceCallback);
                                    }
                                    solver.start(inputPath, outputPath);
                                } else if ("MCMC".equals(mode)) {
                                    com.treloc.xtreloc.solver.HypoMCMC solver = new com.treloc.xtreloc.solver.HypoMCMC(config);
                                    if (convergenceCallback != null) {
                                        solver.setConvergenceCallback(convergenceCallback);
                                    }
                                    solver.start(inputPath, outputPath);
                                } else if ("DE".equals(mode)) {
                                    com.treloc.xtreloc.solver.HypoDifferentialEvolution solver = 
                                        new com.treloc.xtreloc.solver.HypoDifferentialEvolution(config);
                                    if (convergenceCallback != null) {
                                        solver.setConvergenceCallback(convergenceCallback);
                                    }
                                    solver.start(inputPath, outputPath);
                                } else if ("TRD".equals(mode)) {
                                    com.treloc.xtreloc.solver.HypoTripleDiff solver = 
                                        new com.treloc.xtreloc.solver.HypoTripleDiff(config);
                                    if (convergenceCallback != null) {
                                        solver.setConvergenceCallback(convergenceCallback);
                                    }
                                    solver.start(inputPath, outputPath);
                                } else {
                                    throw new IllegalArgumentException("Unknown mode: " + mode);
                                }
                                SolverRunMetrics runMetrics = SolverRunMetricsContext.getAndClear();
                                
                                if (residualPlotPanel != null) {
                                    SwingUtilities.invokeLater(() -> {
                                        residualPlotPanel.markEventCompleted(eventName);
                                    });
                                }
                                processedCount.incrementAndGet();
                                try {
                                    java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = 
                                        loadHypocentersFromDatFile(new File(outputPath));
                                    allHypocenters.addAll(hypocenters);
                                    batchSummary.put(datFile.getName(),
                                        SolverBatchSummary.Row.fromMetrics(currentMode, datFile.getName(), "OK",
                                            hypocenters.size(), runMetrics, ""));
                                    if (!multiEventBatch) {
                                        publish("Success: " + datFile.getName() + " - " + hypocenters.size() + " hypocenter data");
                                    }
                                } catch (Exception e) {
                                    batchSummary.put(datFile.getName(),
                                        SolverBatchSummary.Row.fromMetrics(currentMode, datFile.getName(), "WARN", 0,
                                            runMetrics, SolverBatchSummary.truncateNote(e.getMessage(), 240)));
                                    if (!multiEventBatch) {
                                        publish("Warning: Failed to load results from " + datFile.getName() + ": " + e.getMessage());
                                    }
                                    logger.warning("Result loading error: " + datFile.getName() + " - " + e.getMessage());
                                }
                                
                            } catch (Exception e) {
                                if (isCancelled() || Thread.currentThread().isInterrupted() || 
                                    (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                                    break;
                                }
                                errorCount.incrementAndGet();
                                batchSummary.put(datFile.getName(),
                                    new SolverBatchSummary.Row(currentMode, datFile.getName(), "ERROR", 0,
                                        SolverBatchSummary.truncateNote(e.getMessage(), 240)));
                                if (!multiEventBatch) {
                                    publish("Error: Skipping processing of " + datFile.getName() + ": " + e.getMessage());
                                }
                                logger.warning("File processing error: " + datFile.getName() + " - " + e.getMessage());
                            }
                        }
                    }
                    
                    if (isCancelled()) {
                        publish("Cancelled: " + processedCount.get() + " files processed successfully, " + errorCount.get() + " files with errors");
                    } else {
                        publish("Execution completed: " + processedCount.get() + " files processed successfully, " + errorCount.get() + " files with errors");
                    }
                    
                    if (!allHypocenters.isEmpty()) {
                        File catalogFile = snapshotOutputCatalogFile;
                        if (catalogFile == null) {
                            File inputCatalogFile = snapshotInputCatalogFile;
                            catalogFile = com.treloc.xtreloc.util.CatalogFileNameGenerator.generateCatalogFileName(
                                inputCatalogFile != null ? inputCatalogFile.getAbsolutePath() : null,
                                currentMode, outputDir);
                        }
                        final File exportedCatalogFile = catalogFile;
                        try {
                            com.treloc.xtreloc.app.gui.service.CsvExporter.exportHypocenters(allHypocenters, catalogFile);
                            publish("Exported in catalog format: " + catalogFile.getAbsolutePath() + " (" + allHypocenters.size() + " entries)");
                        } catch (Exception e) {
                            publish("Warning: Failed to export in catalog format: " + e.getMessage());
                            logger.warning("Catalog export error: " + e.getMessage());
                        }
                        
                        if (solverResultCatalogPanel != null) {
                            final java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hyposToAdd =
                                new java.util.ArrayList<>(allHypocenters);
                            final String displayName = exportedCatalogFile != null
                                ? exportedCatalogFile.getName()
                                : "Solver result (" + currentMode + ")";
                            SwingUtilities.invokeLater(() -> {
                                solverResultCatalogPanel.addCatalogFromHypocenters(
                                    hyposToAdd, displayName, exportedCatalogFile);
                            });
                        } else if (mapView != null) {
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    mapView.showHypocenters(allHypocenters);
                                } catch (Exception e) {
                                    ExecutionFailureReporter.log(logger, "Map display: showHypocenters failed.", e);
                                }
                            });
                        }
                    } else {
                        publish("Warning: No processed hypocenter data");
                    }
                    
                } catch (Exception e) {
                    StringBuilder errorMsg = new StringBuilder("Execution error in " + currentMode + " mode:\n");
                    errorMsg.append("  Error type: ").append(e.getClass().getName()).append("\n");
                    errorMsg.append("  Error message: ").append(e.getMessage()).append("\n");
                    if (e.getCause() != null) {
                        errorMsg.append("  Caused by: ").append(e.getCause().getClass().getName())
                               .append(": ").append(e.getCause().getMessage()).append("\n");
                    }
                    String errorStr = errorMsg.toString();
                    logger.severe(errorStr);
                    publish(errorStr);
                    logger.log(Level.SEVERE, "Solver execution error (" + currentMode + ")", e);
                } finally {
                    if (executor != null) {
                        if (isCancelled()) {
                            executor.shutdownNow();
                            try {
                                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                                    publish("Warning: Some tasks may still be running");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        } else {
                            executor.shutdown();
                            try {
                                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                                    executor.shutdownNow();
                                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                                        publish("Warning: Some tasks did not terminate");
                                    }
                                }
                            } catch (InterruptedException e) {
                                executor.shutdownNow();
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }

                    if (!datFiles.isEmpty()) {
                        java.util.List<String> tsvLines = SolverBatchSummary.formatTsvLines(currentMode, datFiles, batchSummary);
                        SwingUtilities.invokeLater(() -> appendLogTsvSection(tsvLines));
                    }

                return null;
                } finally {
                    SolverLogger.setSuppressInfoInCallback(false);
                }
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    appendLog(message);
                }
            }
            
            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    try {
                        get();
                        File configExport = getConfigExportFileFromField();
                        if (configExport != null && !isCancelled()) {
                            exportConfigToPath(configExport);
                        }
                    } catch (java.util.concurrent.CancellationException e) {
                        appendLog("Cancelled successfully");
                    } catch (Exception e) {
                        ExecutionFailureReporter.log(logger, "Solver execution: worker failed.", e);
                        appendLog(ExecutionFailureReporter.formatExecutionLogBlock(e));
                    } catch (Throwable t) {
                        ExecutionFailureReporter.log(logger, "Solver execution: unexpected throwable.", t);
                        appendLog(ExecutionFailureReporter.formatExecutionLogBlock(t));
                    } finally {
                        resetExecutionStateAfterRun();
                    }
                });
            }
        };
        
        executeButton.setEnabled(false);
        if (cancelButton != null) {
            cancelButton.setEnabled(true);
        }
        currentWorker.execute();
    }
    
    /**
     * Runs SYN mode: generates synthetic test data from catalog and writes .dat files to the output directory.
     * Uses parameters from the parameter table and SYN mode config.
     */
    private void executeSyntheticTest() {
        if (config == null) {
            config = new AppConfig();
        }
        
        File catalogFile = getCatalogFileFromField();
        if (catalogFile == null || !catalogFile.exists()) {
            appendLog("ERROR: Please select a catalog file.");
            logger.warning("SYN aborted: no catalog file.");
            return;
        }
        
        if (selectedOutputDir == null) {
            appendLog("ERROR: Please select an output directory.");
            logger.warning("SYN aborted: no output directory.");
            return;
        }
        
        if (!selectedOutputDir.exists()) {
            String errorMsg = String.format(
                "Output directory does not exist: %s\n" +
                "  Please create the directory before running SYN mode.",
                selectedOutputDir.getAbsolutePath());
            appendLog("ERROR: " + errorMsg);
            logger.warning(errorMsg);
            return;
        }
        if (!selectedOutputDir.isDirectory()) {
            String errorMsg = String.format(
                "Output path is not a directory: %s\n" +
                "  Please select a valid directory.",
                selectedOutputDir.getAbsolutePath());
            appendLog("ERROR: " + errorMsg);
            logger.warning(errorMsg);
            return;
        }
        
        if (selectedStationFile == null || !selectedStationFile.exists()) {
            appendLog("ERROR: Please select a station file.");
            logger.warning("SYN aborted: no station file.");
            return;
        }
        String selectedModel = (String) taupModelCombo.getSelectedItem();
        config.taupFile = selectedModel;
        config.stationFile = selectedStationFile.getAbsolutePath();
        
        if (config.io == null) {
            config.io = new java.util.HashMap<>();
        }
        if (config.params == null) {
            config.params = new java.util.HashMap<>();
        }
        AppConfig.ModeIOConfig synIO = config.io.get("SYN");
        if (synIO == null) {
            synIO = new AppConfig.ModeIOConfig();
            config.io.put("SYN", synIO);
        }
        synIO.catalogFile = catalogFile.getAbsolutePath();
        synIO.outDirectory = selectedOutputDir.toPath();
        com.fasterxml.jackson.databind.node.ObjectNode synParams = (com.fasterxml.jackson.databind.node.ObjectNode) config.params.get("SYN");
        if (synParams == null) {
            synParams = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            config.params.put("SYN", synParams);
        }
        String rs = getParameterValue("randomSeed");
        String pe = getParameterValue("phsErr");
        String le = getParameterValue("locErr");
        String msr = getParameterValue("minSelectRate");
        String mxr = getParameterValue("maxSelectRate");
        if (!rs.isEmpty()) synParams.put("randomSeed", Integer.parseInt(rs));
        if (!pe.isEmpty()) synParams.put("phsErr", Double.parseDouble(pe));
        if (!le.isEmpty()) synParams.put("locErr", Double.parseDouble(le));
        if (!msr.isEmpty()) synParams.put("minSelectRate", Double.parseDouble(msr));
        if (!mxr.isEmpty()) synParams.put("maxSelectRate", Double.parseDouble(mxr));
        
        final String synRandomSeedStr = getParameterValue("randomSeed");
        final String synPhsErrStr = getParameterValue("phsErr");
        final String synLocErrStr = getParameterValue("locErr");
        final String synMinSelectRateStr = getParameterValue("minSelectRate");
        final String synMaxSelectRateStr = getParameterValue("maxSelectRate");
        
        executeButton.setEnabled(false);
        cancelButton.setEnabled(true);
        appendLog("Starting synthetic test data generation...");
        
        currentWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                SolverLogger.setMode(true, false, true);
                SolverLogger.setCallback((msg, level) -> {
                    if (level.intValue() >= Level.INFO.intValue()) {
                        appendLog(level.getName() + ": " + msg);
                    }
                });
                try {
                    if (config.taupFile == null || config.taupFile.isEmpty()) {
                        throw new IllegalArgumentException("Velocity model file (taupFile) is not set");
                    }
                    if (config.stationFile == null || config.stationFile.isEmpty()) {
                        throw new IllegalArgumentException("Station file (stationFile) is not set");
                    }
                    
                    publish("Catalog file: " + catalogFile.getAbsolutePath());
                    publish("Output directory: " + selectedOutputDir.getAbsolutePath());
                    String randomSeedStr = synRandomSeedStr;
                    String phsErrStr = synPhsErrStr;
                    String locErrStr = synLocErrStr;
                    String minSelectRateStr = synMinSelectRateStr;
                    String maxSelectRateStr = synMaxSelectRateStr;
                    int randomSeed = !randomSeedStr.isEmpty() ? Integer.parseInt(randomSeedStr) : 100;
                    double phsErr = !phsErrStr.isEmpty() ? Double.parseDouble(phsErrStr) : 0.1;
                    double locErr = !locErrStr.isEmpty() ? Double.parseDouble(locErrStr) : 0.03;
                    double minSelectRate = !minSelectRateStr.isEmpty() ? Double.parseDouble(minSelectRateStr) : 0.2;
                    double maxSelectRate = !maxSelectRateStr.isEmpty() ? Double.parseDouble(maxSelectRateStr) : 0.4;
                    boolean addLocationPerturbation = true;
                    
                    publish("Parameters: seed=" + randomSeed + ", phsErr=" + phsErr + 
                           ", locErr=" + locErr + ", selectRate=" + minSelectRate + "-" + maxSelectRate);
                    
                    SyntheticTest syntheticTest = new SyntheticTest(config, randomSeed, phsErr, locErr, 
                                                                   minSelectRate, maxSelectRate, addLocationPerturbation);
                    syntheticTest.generateDataFromCatalog();
                    
                    publish("Synthetic test data generation completed");
                    
                    publish("Starting automatic catalog generation...");
                    List<File> datFiles = findDatFiles(selectedOutputDir);
                    if (!datFiles.isEmpty()) {
                        List<com.treloc.xtreloc.app.gui.model.Hypocenter> allHypocenters = 
                            new java.util.ArrayList<>();
                        int processedCount = 0;
                        int errorCount = 0;
                        
                        for (File datFile : datFiles) {
                            try {
                                List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = 
                                    loadHypocentersFromDatFile(datFile);
                                for (com.treloc.xtreloc.app.gui.model.Hypocenter h : hypocenters) {
                                    com.treloc.xtreloc.app.gui.model.Hypocenter newHypo;
                                    String type = (h.type == null || h.type.isEmpty()) ? "SYN" : h.type;
                                    
                                    String datFilePath = h.datFilePath;
                                    if (datFilePath == null || datFilePath.isEmpty()) {
                                        if (selectedOutputDir != null) {
                                            try {
                                                java.nio.file.Path catalogPath = selectedOutputDir.toPath();
                                                java.nio.file.Path datPath = datFile.toPath();
                                                java.nio.file.Path relativePath = catalogPath.relativize(datPath);
                                                datFilePath = relativePath.toString().replace(java.io.File.separator, "/");
                                            } catch (Exception e) {
                                                datFilePath = datFile.getName();
                                                logger.warning("Failed to calculate relative path: " + datFile.getName() + " - " + e.getMessage());
                                            }
                                        } else {
                                            datFilePath = datFile.getName();
                                        }
                                    }
                                    newHypo = new com.treloc.xtreloc.app.gui.model.Hypocenter(
                                        h.time, h.lat, h.lon, h.depth, h.xerr, h.yerr, h.zerr, h.rms, 
                                        h.clusterId, datFilePath, type);
                                    allHypocenters.add(newHypo);
                                }
                                processedCount++;
                            } catch (Exception e) {
                                errorCount++;
                                publish("Warning: Failed to load " + datFile.getName() + ": " + e.getMessage());
                            }
                        }
                        
                        if (!allHypocenters.isEmpty()) {
                            File catalogFile = com.treloc.xtreloc.util.CatalogFileNameGenerator.generateCatalogFileName(
                                null, "SYN", selectedOutputDir);
                            try {
                                com.treloc.xtreloc.app.gui.service.CsvExporter.exportHypocenters(allHypocenters, catalogFile);
                                publish("Catalog auto-generated: " + catalogFile.getAbsolutePath() + " (" + allHypocenters.size() + " entries)");
                                publish("Processing result: " + processedCount + " files succeeded, " + errorCount + " files with errors");
                            } catch (Exception e) {
                                publish("Warning: Failed to auto-generate catalog: " + e.getMessage());
                            }
                        } else {
                            publish("Warning: No hypocenter data loaded");
                        }
                    }
                } catch (Exception e) {
                    ExecutionFailureReporter.log(logger, "SYN: synthetic test worker failed in background.", e);
                    publish("Error: " + ExecutionFailureReporter.oneLine(e));
                    throw e;
                }
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    appendLog(message);
                }
            }
            
            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    try {
                        get();
                        File configExport = getConfigExportFileFromField();
                        if (configExport != null && !isCancelled()) {
                            exportConfigToPath(configExport);
                        }
                    } catch (java.util.concurrent.CancellationException e) {
                        appendLog("Cancelled successfully");
                    } catch (Exception e) {
                        ExecutionFailureReporter.log(logger, "SYN: synthetic test worker failed on completion.", e);
                        appendLog(ExecutionFailureReporter.formatExecutionLogBlock(e));
                    } catch (Throwable t) {
                        ExecutionFailureReporter.log(logger, "SYN: unexpected throwable on worker completion.", t);
                        appendLog(ExecutionFailureReporter.formatExecutionLogBlock(t));
                    } finally {
                        resetExecutionStateAfterRun();
                    }
                });
            }
        };
        
        currentWorker.execute();
    }
    
    /**
     * Runs CLS mode: spatial clustering (DBSCAN) on triple-difference result.
     * Reads from TRD output and writes cluster catalog; uses minPts, eps (or percentile), and thresholds from UI.
     */
    private void executeClustering() {
        if (config == null) {
            config = new AppConfig();
        }
        String numJobsStr = getParameterValue("numJobs");
        if (!numJobsStr.isEmpty()) {
            try {
                config.numJobs = Math.max(1, Integer.parseInt(numJobsStr.trim()));
            } catch (NumberFormatException e) {
                config.numJobs = 1;
            }
        }
        
        File catalogFile = getCatalogFileFromField();
        if (catalogFile == null || !catalogFile.exists()) {
            appendLog("ERROR: Please select a catalog file.");
            logger.warning("CLS aborted: no catalog file.");
            return;
        }
        
        if (selectedOutputDir == null) {
            appendLog("ERROR: Please select an output directory.");
            logger.warning("CLS aborted: no output directory.");
            return;
        }
        
        if (selectedStationFile == null || !selectedStationFile.exists()) {
            appendLog("ERROR: Please select a station file.");
            logger.warning("CLS aborted: no station file.");
            return;
        }
        String selectedModel = (String) taupModelCombo.getSelectedItem();
        config.taupFile = selectedModel;
        config.stationFile = selectedStationFile.getAbsolutePath();
        
        String thresholdStr = getParameterValue("threshold");
        String hypBottomStr = getParameterValue("hypBottom");
        if (!thresholdStr.isEmpty()) {
            config.threshold = Double.parseDouble(thresholdStr);
        }
        if (!hypBottomStr.isEmpty()) {
            config.hypBottom = Double.parseDouble(hypBottomStr);
        }
        
        if (config.io == null) {
            config.io = new java.util.HashMap<>();
        }
        if (config.params == null) {
            config.params = new java.util.HashMap<>();
        }
        AppConfig.ModeConfig clsConfig = config.getModes().get("CLS");
        if (clsConfig == null) {
            var clsIO = new AppConfig.ModeIOConfig();
            config.io.put("CLS", clsIO);
            config.params.put("CLS", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
            clsConfig = config.getModes().get("CLS");
        }
        var clsIO = config.io.get("CLS");
        if (clsIO != null) {
            clsIO.catalogFile = catalogFile.getAbsolutePath();
            clsIO.outDirectory = selectedOutputDir.toPath();
            if (selectedTargetDir != null) clsIO.datDirectory = selectedTargetDir.toPath();
        }
        var clsParams = (com.fasterxml.jackson.databind.node.ObjectNode) config.params.get("CLS");
        if (clsParams == null) {
            clsParams = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            config.params.put("CLS", clsParams);
        }
        String minPtsStr = getParameterValue("minPts");
        String epsStr = getParameterValue("eps");
        String epsPercentileStr = getParameterValue("epsPercentile");
        String rmsThresholdStr = getParameterValue("rmsThreshold");
        String locErrThresholdStr = getParameterValue("locErrThreshold");
        if (!minPtsStr.isEmpty()) {
            clsParams.put("minPts", Integer.parseInt(minPtsStr));
        }
        if (!epsStr.isEmpty()) {
            clsParams.put("eps", Double.parseDouble(epsStr));
        }
        if (!epsPercentileStr.isEmpty()) {
            try {
                double percentile = Double.parseDouble(epsPercentileStr);
                if (percentile > 0 && percentile <= 1) {
                    clsParams.put("epsPercentile", percentile);
                }
            } catch (NumberFormatException e) {
            }
        }
        if (!rmsThresholdStr.isEmpty()) {
            try {
                clsParams.put("rmsThreshold", Double.parseDouble(rmsThresholdStr));
            } catch (NumberFormatException e) {
            }
        }
        if (!locErrThresholdStr.isEmpty()) {
            try {
                clsParams.put("locErrThreshold", Double.parseDouble(locErrThresholdStr));
            } catch (NumberFormatException e) {
            }
        }
        String doClusteringStr = getParameterValue("doClustering");
        String calcTripleDiffStr = getParameterValue("calcTripleDiff");
        if (!doClusteringStr.isEmpty()) {
            clsParams.put("doClustering", "true".equalsIgnoreCase(doClusteringStr.trim()) || "1".equals(doClusteringStr.trim()));
        }
        if (!calcTripleDiffStr.isEmpty()) {
            clsParams.put("calcTripleDiff", "true".equalsIgnoreCase(calcTripleDiffStr.trim()) || "1".equals(calcTripleDiffStr.trim()));
        }
        clsParams.put("useBinaryFormat", true);
        clsConfig = config.getModes().get("CLS");
        if (clsConfig.doClustering == null) clsConfig.doClustering = true;
        if (clsConfig.calcTripleDiff == null) clsConfig.calcTripleDiff = true;
        if (!clsConfig.doClustering && !clsConfig.calcTripleDiff) {
            appendLog("ERROR: Both Do Clustering and Calc. Triple Diff are false. At least one must be true.");
            logger.warning("CLS aborted: doClustering and calcTripleDiff both false.");
            executeButton.setEnabled(true);
            if (cancelButton != null) cancelButton.setEnabled(false);
            return;
        }
        clsConfig.useBinaryFormat = true;
        
        final int finalMinPts = clsConfig.minPts != null ? clsConfig.minPts : 4;
        final double finalEps = clsConfig.eps != null ? clsConfig.eps : -1.0;
        
        executeButton.setEnabled(false);
        cancelButton.setEnabled(true);
        appendLog("Starting clustering process...");
        
        final java.util.concurrent.atomic.AtomicReference<File> clsOutputCatalogRef =
            new java.util.concurrent.atomic.AtomicReference<>();
        
        currentWorker = new SwingWorker<Void, String>() {
            private com.treloc.xtreloc.solver.SpatialClustering clustering;
            
            @Override
            protected Void doInBackground() throws Exception {
                SolverLogger.setMode(true, false, true);
                SolverLogger.setCallback((msg, level) -> {
                    if (level.intValue() >= Level.INFO.intValue()) {
                        appendLog(level.getName() + ": " + msg);
                    }
                });
                try {
                    publish("Catalog file: " + catalogFile.getAbsolutePath());
                    publish("Output directory: " + selectedOutputDir.getAbsolutePath());
                    publish("Parameters: minPts=" + finalMinPts + ", eps=" + finalEps);
                    
                    clustering = new com.treloc.xtreloc.solver.SpatialClustering(config);
                    clustering.start("", "");
                    
                    publish("Clustering process completed");
                    publish("Output directory: " + selectedOutputDir.getAbsolutePath());
                    File clsOutputFile = com.treloc.xtreloc.util.CatalogFileNameGenerator.generateCatalogFileName(
                        catalogFile.getAbsolutePath(), "CLS", selectedOutputDir);
                    clsOutputCatalogRef.set(clsOutputFile);
                    publish("Catalog file with cluster IDs: " + clsOutputFile.getAbsolutePath());
                    publish("Triple difference data: " + selectedOutputDir.getAbsolutePath() + "/triple_diff_*.bin");
                    
                    List<Double> kDistances = clustering.getKDistances();
                    if (kDistances != null && !kDistances.isEmpty()) {
                        double estimatedEps = clustering.getEstimatedEps();
                        publish("Estimated Epsilon: " + estimatedEps + " km");
                        SwingUtilities.invokeLater(() -> {
                            showKDistanceGraph(kDistances, estimatedEps);
                        });
                    }
                    
                } catch (Exception e) {
                    ExecutionFailureReporter.log(logger, "CLS: clustering process error.", e);
                    throw e;
                }
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    appendLog(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    File configExport = getConfigExportFileFromField();
                    if (configExport != null && !isCancelled()) {
                        exportConfigToPath(configExport);
                    }
                    if (!isCancelled()) {
                        File clsCat = clsOutputCatalogRef.get();
                        if (solverResultCatalogPanel != null && clsCat != null && clsCat.exists()) {
                            try {
                                java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypos =
                                    CatalogLoader.load(clsCat);
                                solverResultCatalogPanel.addCatalogFromHypocenters(
                                    hypos, clsCat.getName(), clsCat);
                            } catch (Exception ex) {
                                ExecutionFailureReporter.log(logger, "CLS: could not add output catalog to Viewer.", ex);
                            }
                        }
                    }
                } catch (java.util.concurrent.CancellationException e) {
                    appendLog("Cancelled successfully");
                } catch (Exception e) {
                    Throwable cause = e.getCause();
                    String causeMsg = cause != null ? cause.getMessage() : null;
                    boolean cancelled = currentWorker != null && (currentWorker.isCancelled()
                        || "Cancelled".equals(causeMsg)
                        || (causeMsg != null && causeMsg.toLowerCase().contains("interrupted")));
                    if (cancelled) {
                        appendLog("Cancelled successfully");
                    } else {
                        ExecutionFailureReporter.log(logger, "CLS: clustering worker failed on completion.", e);
                        appendLog(ExecutionFailureReporter.formatExecutionLogBlock(e));
                    }
                } catch (Throwable t) {
                    ExecutionFailureReporter.log(logger, "CLS: unexpected throwable on worker completion.", t);
                    appendLog(ExecutionFailureReporter.formatExecutionLogBlock(t));
                } finally {
                    resetExecutionStateAfterRun();
                }
            }
        };
        
        currentWorker.execute();
    }
    
    /**
     * Displays the k-distance graph in the Convergence Log panel (upper part of the Solver right-hand split).
     *
     * @param kDistances list of k-distance values
     * @param estimatedEps estimated epsilon for DBSCAN
     */
    private void showKDistanceGraph(List<Double> kDistances, double estimatedEps) {
        if (residualPlotPanel != null) {
            residualPlotPanel.showKDistanceGraph(kDistances, estimatedEps);
        }
    }
    
    /**
     * Requests cancellation of the current execution.
     * Shows a confirmation dialog; if confirmed, the worker is cancelled and
     * processed catalog data will still be exported.
     */
    private void cancelExecution() {
        if (currentWorker != null && !currentWorker.isDone()) {
            int result = JOptionPane.showConfirmDialog(this,
                "Do you want to cancel execution?\nAfter cancellation, catalog will be exported from processed data.",
                "Cancel Confirmation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                currentWorker.cancel(true);
                appendLog("Interrupt request sent...");
            }
        }
    }
    
    /**
     * Opens a file chooser to select a JSON config file and sets the path in the Import (Json) field.
     */
    private void selectImportJsonFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Config File (JSON)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files (*.json)", "json"));
        chooser.setSelectedFile(new File("config.json"));
        String currentPath = importJsonFileField != null ? importJsonFileField.getText().trim() : "";
        if (!currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                chooser.setCurrentDirectory(currentFile.getParentFile());
                chooser.setSelectedFile(currentFile);
            } else {
                com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(chooser);
            }
        } else {
            com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(chooser);
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file != null) {
                importJsonFileField.setText(file.getAbsolutePath());
                appendLog("Import config file selected: " + file.getAbsolutePath());
            }
        }
    }
    
    /**
     * Loads solver settings from the path in the Import (Json) field and applies them to the UI.
     * If the field is empty, opens a file chooser. Prompts for confirmation before overwriting
     * the current configuration.
     */
    private void importConfigFromJson() {
        try {
            String path = importJsonFileField != null ? importJsonFileField.getText().trim() : "";
            if (path.isEmpty()) {
                selectImportJsonFile();
                path = importJsonFileField != null ? importJsonFileField.getText().trim() : "";
            }
            if (path.isEmpty()) return;
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                appendLog("ERROR: Import (Json) file not found: " + path);
                logger.warning("Import config: file not found: " + path);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this,
                "Do you want to overwrite the existing configuration?",
                "Import (Json)",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            com.treloc.xtreloc.io.ConfigLoader loader = new com.treloc.xtreloc.io.ConfigLoader(file.getAbsolutePath());
            AppConfig loaded = loader.getConfig();
            setConfig(loaded);
            completeOutputFileFieldAfterConfigImport();
            if (importJsonFileField != null) {
                importJsonFileField.setText(file.getAbsolutePath());
            }
            appendLog("Configuration imported from: " + file.getAbsolutePath());
            logger.info("Import (Json): configuration applied from " + file.getAbsolutePath());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to import config", e);
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            appendLog("ERROR: Failed to import configuration: " + message);
        }
    }
    
    /**
     * Loads hypocenter data from a single .dat file.
     * <p>
     * Expected format: two lines per event — line 1: latitude longitude depth [type];
     * line 2: xerr (km) yerr (km) zerr (km) rms residual.
     *
     * @param datFile the .dat file to read
     * @return list of hypocenters from the file, or empty list on error
     */
    private java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> loadHypocentersFromDatFile(File datFile) {
        java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = new java.util.ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(datFile))) {
            String line1 = br.readLine();
            if (line1 != null) {
                String[] parts1 = line1.trim().split("\\s+");
                if (parts1.length >= 3) {
                    double lat = Double.parseDouble(parts1[0]);
                    double lon = Double.parseDouble(parts1[1]);
                    double depth = Double.parseDouble(parts1[2]);
                    String time = datFile.getName().replace(".dat", "");
                    String type = parts1.length > 3 ? parts1[3] : null;
                    
                    double xerr = 0.0;
                    double yerr = 0.0;
                    double zerr = 0.0;
                    double rms = 0.0;
                    
                    String line2 = br.readLine();
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
                    
                    String datFilePath = null;
                    if (selectedOutputDir != null) {
                        try {
                            java.nio.file.Path catalogPath = selectedOutputDir.toPath();
                            java.nio.file.Path datPath = datFile.toPath();
                            java.nio.file.Path relativePath = catalogPath.relativize(datPath);
                            datFilePath = relativePath.toString().replace(java.io.File.separator, "/");
                        } catch (Exception e) {
                            datFilePath = datFile.getAbsolutePath();
                        }
                    } else {
                        datFilePath = datFile.getName();
                    }
                    
                    hypocenters.add(new com.treloc.xtreloc.app.gui.model.Hypocenter(time, lat, lon, depth, xerr, yerr, zerr, rms, null, datFilePath, type));
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to read dat file: " + e.getMessage());
        }
        return hypocenters;
    }
    
    private List<File> findDatFiles(File directory) {
        List<File> datFiles = new java.util.ArrayList<>();
        if (!directory.isDirectory()) {
            return datFiles;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    datFiles.addAll(findDatFiles(file));
                } else if (file.getName().toLowerCase().endsWith(".dat")) {
                    datFiles.add(file);
                }
            }
        }
        return datFiles;
    }
    
    /**
     * Applies a loaded config to all GUI input fields (paths, parameters, solver settings).
     * Called when importing config.json or when the app starts with an existing config file.
     */
    public void setConfig(AppConfig config) {
        this.config = config;
        if (config != null) {
            if (numJobsField != null && config.numJobs > 0) {
                numJobsField.setText(String.valueOf(config.numJobs));
            }
            if (stationFileField != null && config.stationFile != null && !config.stationFile.isEmpty()) {
                stationFileField.setText(config.stationFile);
                File f = new File(config.stationFile);
                if (f.exists() && f.isFile()) {
                    selectedStationFile = f;
                }
            }
            if (config.getModes() != null) {
                for (Map.Entry<String, AppConfig.ModeConfig> entry : config.getModes().entrySet()) {
                    String mode = entry.getKey();
                    AppConfig.ModeConfig mc = ModeConfigResolver.getModeConfigWithFallback(config, mode);
                    if (mc == null) continue;
                    Map<String, String> cache = modeParameterCache.computeIfAbsent(mode, k -> new HashMap<>());
                    if (config.stationFile != null && !config.stationFile.isEmpty()) {
                        cache.put("stationFile", config.stationFile);
                    }
                    if (config.taupFile != null && !config.taupFile.isEmpty()) {
                        cache.put("taupFile", config.taupFile);
                    }
                    if (mc.datDirectory != null) {
                        cache.put("targetDir", mc.datDirectory.toString());
                    }
                    if (mc.outDirectory != null) {
                        cache.put("outputDir", mc.outDirectory.toString());
                        cache.put("configExport", mc.outDirectory.resolve("config.json").toString());
                    }
                    if (mc.catalogFile != null && !mc.catalogFile.isEmpty()) {
                        cache.put("catalogFile", mc.catalogFile);
                    }
                    if ("SYN".equals(mode)) {
                        if (mc.randomSeed != null) cache.put("randomSeed", String.valueOf(mc.randomSeed));
                        if (mc.phsErr != null) cache.put("phsErr", String.valueOf(mc.phsErr));
                        if (mc.locErr != null) cache.put("locErr", String.valueOf(mc.locErr));
                        if (mc.minSelectRate != null) cache.put("minSelectRate", String.valueOf(mc.minSelectRate));
                        if (mc.maxSelectRate != null) cache.put("maxSelectRate", String.valueOf(mc.maxSelectRate));
                    }
                    if ("CLS".equals(mode)) {
                        if (mc.minPts != null) cache.put("minPts", String.valueOf(mc.minPts));
                        if (mc.eps != null) cache.put("eps", String.valueOf(mc.eps));
                        if (mc.epsPercentile != null) cache.put("epsPercentile", String.valueOf(mc.epsPercentile));
                        if (mc.rmsThreshold != null) cache.put("rmsThreshold", String.valueOf(mc.rmsThreshold));
                        if (mc.locErrThreshold != null) cache.put("locErrThreshold", String.valueOf(mc.locErrThreshold));
                        if (mc.doClustering != null) cache.put("doClustering", mc.doClustering ? "true" : "false");
                        if (mc.calcTripleDiff != null) cache.put("calcTripleDiff", mc.calcTripleDiff ? "true" : "false");
                    }
                }
                if (config.getParams() != null && config.getParams().containsKey("CLS")) {
                    var clsSolver = config.getParams().get("CLS");
                    Map<String, String> clsCache = modeParameterCache.get("CLS");
                    if (clsCache != null) {
                        if (clsSolver.has("minPts")) clsCache.put("minPts", String.valueOf(clsSolver.get("minPts").asInt()));
                        if (clsSolver.has("eps")) clsCache.put("eps", String.valueOf(clsSolver.get("eps").asDouble()));
                    }
                }
                if (modeCombo != null) {
                    String currentMode = getSelectedModeAbbreviation();
                    if (currentMode != null) {
                        restoreModeStateFromCache(currentMode, null);
                    }
                }
            }
            thresholdField.setText(String.valueOf(config.threshold));
            hypBottomField.setText(String.valueOf(config.hypBottom));
            
            if (config.taupFile != null && !config.taupFile.isEmpty()) {
                if (taupModelCombo != null) {
                    taupModelCombo.setSelectedItem(config.taupFile);
                }
                com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setTaupFile(config.taupFile);
            }
            
            if (config.getParams() != null) {
                if (config.getParams().containsKey("GRD")) {
                    var grdSolver = config.getParams().get("GRD");
                    if (grdSolver.has("totalGrids") && totalGridsField != null) {
                        totalGridsField.setText(String.valueOf(grdSolver.get("totalGrids").asInt()));
                    }
                    if (grdSolver.has("numFocus") && numFocusField != null) {
                        numFocusField.setText(String.valueOf(grdSolver.get("numFocus").asInt()));
                    }
                    if (grdSolver.has("numGrid") && !grdSolver.has("totalGrids") && totalGridsField != null) {
                        totalGridsField.setText(String.valueOf(grdSolver.get("numGrid").asInt()));
                        if (numFocusField != null) {
                            numFocusField.setText("1");
                        }
                    }
                }
                if (config.getParams().containsKey("LMO")) {
                    var lmoSolver = config.getParams().get("LMO");
                    if (lmoSolver.has("initialStepBoundFactor") && lmInitialStepBoundField != null) {
                        lmInitialStepBoundField.setText(String.valueOf(lmoSolver.get("initialStepBoundFactor").asDouble()));
                    }
                    if (lmoSolver.has("costRelativeTolerance") && lmCostRelativeToleranceField != null) {
                        lmCostRelativeToleranceField.setText(String.valueOf(lmoSolver.get("costRelativeTolerance").asDouble()));
                    }
                    if (lmoSolver.has("parRelativeTolerance") && lmParRelativeToleranceField != null) {
                        lmParRelativeToleranceField.setText(String.valueOf(lmoSolver.get("parRelativeTolerance").asDouble()));
                    }
                    if (lmoSolver.has("orthoTolerance") && lmOrthoToleranceField != null) {
                        lmOrthoToleranceField.setText(String.valueOf(lmoSolver.get("orthoTolerance").asDouble()));
                    }
                    if (lmoSolver.has("maxEvaluations") && lmMaxEvaluationsField != null) {
                        lmMaxEvaluationsField.setText(String.valueOf(lmoSolver.get("maxEvaluations").asInt()));
                    }
                    if (lmoSolver.has("maxIterations") && lmMaxIterationsField != null) {
                        lmMaxIterationsField.setText(String.valueOf(lmoSolver.get("maxIterations").asInt()));
                    }
                    if (lmoSolver.has("showConvergenceLog") && showConvergenceLogCheckBox != null) {
                        showConvergenceLogCheckBox.setSelected(lmoSolver.get("showConvergenceLog").asBoolean());
                    }
                }
                if (config.getParams().containsKey("MCMC")) {
                    var mcmcSolver = config.getParams().get("MCMC");
                    if (mcmcSolver.has("showConvergenceLog") && showConvergenceLogCheckBox != null) {
                        showConvergenceLogCheckBox.setSelected(mcmcSolver.get("showConvergenceLog").asBoolean());
                    }
                    if (mcmcSolver.has("nSamples") && nSamplesField != null) {
                        nSamplesField.setText(String.valueOf(mcmcSolver.get("nSamples").asInt()));
                    }
                    if (mcmcSolver.has("burnIn") && burnInField != null) {
                        burnInField.setText(String.valueOf(mcmcSolver.get("burnIn").asInt()));
                    }
                    if (mcmcSolver.has("stepSize") && stepSizeField != null) {
                        stepSizeField.setText(String.valueOf(mcmcSolver.get("stepSize").asDouble()));
                    }
                    if (mcmcSolver.has("stepSizeDepth") && stepSizeDepthField != null) {
                        stepSizeDepthField.setText(String.valueOf(mcmcSolver.get("stepSizeDepth").asDouble()));
                    }
                    if (mcmcSolver.has("temperature") && temperatureField != null) {
                        temperatureField.setText(String.valueOf(mcmcSolver.get("temperature").asDouble()));
                    }
                    if (mcmcSolver.has("showConvergenceLog") && showConvergenceLogCheckBox != null) {
                        showConvergenceLogCheckBox.setSelected(mcmcSolver.get("showConvergenceLog").asBoolean());
                    }
                }
                if (config.getParams().containsKey("DE")) {
                    var deSolver = config.getParams().get("DE");
                    if (deSolver.has("populationSize") && dePopulationSizeField != null) {
                        dePopulationSizeField.setText(String.valueOf(deSolver.get("populationSize").asInt()));
                    }
                    if (deSolver.has("maxGenerations") && deMaxGenerationsField != null) {
                        deMaxGenerationsField.setText(String.valueOf(deSolver.get("maxGenerations").asInt()));
                    }
                    if (deSolver.has("scalingFactor") && deScalingFactorField != null) {
                        deScalingFactorField.setText(String.valueOf(deSolver.get("scalingFactor").asDouble()));
                    }
                    if (deSolver.has("crossoverRate") && deCrossoverRateField != null) {
                        deCrossoverRateField.setText(String.valueOf(deSolver.get("crossoverRate").asDouble()));
                    }
                }
                if (config.getParams().containsKey("SYN")) {
                    var synParams = config.getParams().get("SYN");
                    if (synParams.has("randomSeed") && randomSeedField != null) {
                        randomSeedField.setText(String.valueOf(synParams.get("randomSeed").asInt()));
                    }
                    if (synParams.has("phsErr") && phsErrField != null) {
                        phsErrField.setText(String.valueOf(synParams.get("phsErr").asDouble()));
                    }
                    if (synParams.has("locErr") && locErrField != null) {
                        locErrField.setText(String.valueOf(synParams.get("locErr").asDouble()));
                    }
                    if (synParams.has("minSelectRate") && minSelectRateField != null) {
                        minSelectRateField.setText(String.valueOf(synParams.get("minSelectRate").asDouble()));
                    }
                    if (synParams.has("maxSelectRate") && maxSelectRateField != null) {
                        maxSelectRateField.setText(String.valueOf(synParams.get("maxSelectRate").asDouble()));
                    }
                }
                if (config.getParams().containsKey("TRD")) {
                    var trdSolver = config.getParams().get("TRD");
                    if (trdSolver.has("iterNum") && trdIterNumField != null) {
                        var iterNumArray = trdSolver.get("iterNum");
                        if (iterNumArray.isArray()) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < iterNumArray.size(); i++) {
                                if (i > 0) sb.append(",");
                                sb.append(iterNumArray.get(i).asInt());
                            }
                            trdIterNumField.setText(sb.toString());
                        }
                    }
                    if (trdSolver.has("distKm") && trdDistKmField != null) {
                        var distKmArray = trdSolver.get("distKm");
                        if (distKmArray.isArray()) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < distKmArray.size(); i++) {
                                if (i > 0) sb.append(",");
                                sb.append(distKmArray.get(i).asInt());
                            }
                            trdDistKmField.setText(sb.toString());
                        }
                    }
                    if (trdSolver.has("dampFact") && trdDampFactField != null) {
                        var dampFactArray = trdSolver.get("dampFact");
                        if (dampFactArray.isArray()) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < dampFactArray.size(); i++) {
                                if (i > 0) sb.append(",");
                                sb.append(dampFactArray.get(i).asInt());
                            }
                            trdDampFactField.setText(sb.toString());
                        }
                    }
                    if (trdSolver.has("maxTripleDiffCount") && trdMaxTripleDiffCountField != null) {
                        trdMaxTripleDiffCountField.setText(String.valueOf(trdSolver.get("maxTripleDiffCount").asInt()));
                    }
                    if (trdSolver.has("lsqrAtol") && lsqrAtolField != null) {
                        lsqrAtolField.setText(String.valueOf(trdSolver.get("lsqrAtol").asDouble()));
                    }
                    if (trdSolver.has("lsqrBtol") && lsqrBtolField != null) {
                        lsqrBtolField.setText(String.valueOf(trdSolver.get("lsqrBtol").asDouble()));
                    }
                    if (trdSolver.has("lsqrConlim") && lsqrConlimField != null) {
                        lsqrConlimField.setText(String.valueOf(trdSolver.get("lsqrConlim").asDouble()));
                    }
                    if (trdSolver.has("lsqrIterLim") && lsqrIterLimField != null) {
                        lsqrIterLimField.setText(String.valueOf(trdSolver.get("lsqrIterLim").asInt()));
                    }
                    if (trdSolver.has("lsqrShowLog") && lsqrShowLogCheckBox != null) {
                        lsqrShowLogCheckBox.setSelected(trdSolver.get("lsqrShowLog").asBoolean());
                    } else if (lsqrShowLogCheckBox != null) {
                        String logLevel = config.logLevel != null ? config.logLevel : "INFO";
                        boolean defaultShow = java.util.logging.Level.INFO.intValue() <= 
                                            java.util.logging.Level.parse(logLevel.toUpperCase()).intValue();
                        lsqrShowLogCheckBox.setSelected(defaultShow);
                    }
                    if (trdSolver.has("lsqrCalcVar") && lsqrCalcVarCheckBox != null) {
                        lsqrCalcVarCheckBox.setSelected(trdSolver.get("lsqrCalcVar").asBoolean());
                    }
                    if (trdSolver.has("showConvergenceLog") && showConvergenceLogCheckBox != null) {
                        showConvergenceLogCheckBox.setSelected(trdSolver.get("showConvergenceLog").asBoolean());
                    }
                }
            }
            if (config.getModes() != null && modeCombo != null) {
                String currentMode = getSelectedModeAbbreviation();
                if (currentMode != null) {
                    restoreModeStateFromCache(currentMode, null);
                }
            }
            updateParameterFields();
        }
    }
    
    private void showParameterHelpDialog() {
        String mode = getSelectedModeAbbreviation();
        String title = "Parameter Help - " + mode + " Mode";
        String helpText = getParameterHelpText(mode);
        
        JDialog helpDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), title, true);
        helpDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        helpDialog.setSize(600, 500);
        helpDialog.setLocationRelativeTo(this);
        
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextArea textArea = new JTextArea();
        textArea.setText(helpText);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(UiFonts.uiPlain(12f));
        textArea.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> helpDialog.dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(closeButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        helpDialog.setContentPane(contentPanel);
        helpDialog.setVisible(true);
    }
    
    /**
     * Get parameter help text for the specified mode
     */
    private String getParameterHelpText(String mode) {
        switch (mode.toUpperCase()) {
            case "GRD":
                return "Grid Search Location (GRD) Parameters\n" +
                    "=========================================\n\n" +
                    "Common Parameters:\n" +
                    "- numJobs: Number of parallel jobs (threads) for processing.\n" +
                    "  Default: 4. Increase for faster processing on multi-core systems.\n\n" +
                    "- threshold: Weight threshold for filtering data.\n" +
                    "  Default: 0.0 (no filtering). Use to exclude low-weight data.\n\n" +
                    "- hypBottom: Maximum depth for hypocenter location (km).\n" +
                    "  Default: 100.0 km.\n\n" +
                    "GRD-Specific Parameters:\n" +
                    "- totalGrids: Total number of grid points to search.\n" +
                    "  Default: 300. More grids = finer resolution but slower.\n\n" +
                    "- numFocus: Number of focus iterations.\n" +
                    "  Default: 3. Higher values = more iterations, better convergence.\n\n" +
                    "Algorithm Description:\n" +
                    "Grid search exhaustively evaluates all grid points and iteratively\n" +
                    "refines the search area around the best solution.";
                    
            case "LMO":
                return "Levenberg-Marquardt Optimization (LMO) Parameters\n" +
                    "=====================================================\n\n" +
                    "Common Parameters:\n" +
                    "- numJobs: Number of parallel jobs (threads) for processing.\n" +
                    "  Default: 4.\n\n" +
                    "- threshold: Weight threshold for filtering data.\n" +
                    "  Default: 0.0 (no filtering).\n\n" +
                    "- hypBottom: Maximum depth for hypocenter location (km).\n" +
                    "  Default: 100.0 km.\n\n" +
                    "LMO-Specific Parameters:\n" +
                    "- initialStepBoundFactor: Initial step bound multiplier.\n" +
                    "  Default: 100. Controls initial step size in optimization.\n\n" +
                    "- costRelativeTolerance: Relative tolerance for cost function.\n" +
                    "  Default: 1e-6. Smaller values = higher precision, slower.\n\n" +
                    "- parRelativeTolerance: Relative tolerance for parameters.\n" +
                    "  Default: 1e-6.\n\n" +
                    "Algorithm Description:\n" +
                    "Levenberg-Marquardt is a hybrid method combining gradient descent\n" +
                    "and Gauss-Newton approaches for efficient non-linear optimization.";
                    
            case "MCMC":
                return "Markov Chain Monte Carlo (MCMC) Parameters\n" +
                    "============================================\n\n" +
                    "Common Parameters:\n" +
                    "- numJobs: Number of parallel jobs (threads) for processing.\n" +
                    "  Default: 4.\n\n" +
                    "- threshold: Weight threshold for filtering data.\n" +
                    "  Default: 0.0 (no filtering).\n\n" +
                    "- hypBottom: Maximum depth for hypocenter location (km).\n" +
                    "  Default: 100.0 km.\n\n" +
                    "MCMC-Specific Parameters:\n" +
                    "- nSamples: Number of MCMC samples to generate.\n" +
                    "  Default: 10000. More samples = better statistics, slower.\n\n" +
                    "- burnIn: Number of burn-in samples to discard.\n" +
                    "  Default: 2000. Typically ~20% of nSamples.\n\n" +
                    "- stepSize: Step size for lateral parameter perturbation.\n" +
                    "  Default: 0.1. Larger = faster exploration, lower acceptance.\n\n" +
                    "- stepSizeDepth: Step size for depth parameter perturbation.\n" +
                    "  Default: 1.0 km.\n\n" +
                    "- temperature: Temperature parameter for acceptance probability.\n" +
                    "  Default: 1.0. Lower values = stricter acceptance.\n\n" +
                    "Algorithm Description:\n" +
                    "MCMC provides probabilistic uncertainty estimates for hypocenter\n" +
                    "location through Bayesian sampling.";
                    
            case "DE":
                return "Differential Evolution (DE) Parameters\n" +
                    "=====================================\n\n" +
                    "Common Parameters:\n" +
                    "- numJobs: Number of parallel jobs (threads) for processing.\n" +
                    "  Default: 4.\n\n" +
                    "- threshold: Weight threshold for filtering data.\n" +
                    "  Default: 0.0 (no filtering).\n\n" +
                    "- hypBottom: Maximum depth for hypocenter location (km).\n" +
                    "  Default: 100.0 km.\n\n" +
                    "DE-Specific Parameters:\n" +
                    "- populationSize: Size of the population in each generation.\n" +
                    "  Default: 20. Larger population = better exploration, slower.\n\n" +
                    "- iterationNumber: Maximum number of iterations.\n" +
                    "  Default: 100. More iterations = better convergence, slower.\n\n" +
                    "- mutationFactor: Mutation scaling factor (F parameter).\n" +
                    "  Default: 0.8. Range: [0.0, 2.0]. Controls mutation strength.\n\n" +
                    "- crossoverProbability: Crossover probability (CR parameter).\n" +
                    "  Default: 0.9. Range: [0.0, 1.0]. Higher = more mixing.\n\n" +
                    "Algorithm Description:\n" +
                    "Differential Evolution is a population-based stochastic optimization\n" +
                    "algorithm that evolves a population of candidate solutions through\n" +
                    "mutation, crossover, and selection operations. It is effective for\n" +
                    "non-linear optimization with multiple local minima.\n\n" +
                    "Key Features:\n" +
                    "- Global search capability\n" +
                    "- Population-based (explores multiple solutions in parallel)\n" +
                    "- Few tunable parameters\n" +
                    "- Good for hypocenter location without gradient information";

                    
            case "TRD":
                return "Triple Difference Relocation (TRD) Parameters\n" +
                    "==============================================\n\n" +
                    "Common Parameters:\n" +
                    "- numJobs: Number of parallel jobs for processing .dat files.\n" +
                    "  Default: 4.\n\n" +
                    "- threshold: Weight threshold for filtering data.\n" +
                    "  Default: 0.0 (no filtering).\n\n" +
                    "- hypBottom: Maximum depth for hypocenter location (km).\n" +
                    "  Default: 100.0 km.\n\n" +
                    "TRD-Specific Parameters:\n" +
                    "- iterNum: Number of iterations (comma-separated for stages).\n" +
                    "  Default: 10,10. Format: stage1,stage2,...\n\n" +
                    "- distKm: Distance thresholds in km (comma-separated).\n" +
                    "  Default: 50,20. Defines event pair distance criteria.\n\n" +
                    "- dampFact: Damping factors (comma-separated).\n" +
                    "  Default: 0,1. Controls smoothing in iterative inversion.\n\n" +
                    "LSQR Parameters (Linear Least Squares Solver):\n" +
                    "- atol: Absolute tolerance.\n" +
                    "  Default: 1e-6.\n\n" +
                    "- btol: Tolerance for Ax=b consistency.\n" +
                    "  Default: 1e-6.\n\n" +
                    "- conlim: Condition limit for matrix conditioning.\n" +
                    "  Default: 1e8.\n\n" +
                    "Algorithm Description:\n" +
                    "Triple difference relocation improves relative earthquake locations\n" +
                    "by using differential measurements between event pairs.";
                    
            case "CLS":
                return "Spatial Clustering (CLS) Parameters\n" +
                    "====================================\n\n" +
                    "CLS-Specific Parameters:\n" +
                    "- minPts: Minimum number of points for core cluster.\n" +
                    "  Default: 4. Affects cluster formation threshold.\n\n" +
                    "- eps: Neighborhood distance threshold (km).\n" +
                    "  Default: -1.0. Use negative value for automatic calculation.\n\n" +
                    "- epsPercentile: Percentile for automatic eps calculation.\n" +
                    "  Default: empty (auto).\n\n" +
                    "- rmsThreshold: RMS threshold for cluster quality.\n" +
                    "  Default: empty (no filtering).\n\n" +
                    "- locErrThreshold: Location error threshold for filtering.\n" +
                    "  Default: empty (no filtering).\n\n" +
                    "- doClustering: true to run DBSCAN clustering; false to use existing cluster IDs in catalog.\n" +
                    "  Default: true.\n\n" +
                    "- calcTripleDiff: true to compute triple differences after clustering; false for clustering only.\n" +
                    "  Default: true. If both doClustering and calcTripleDiff are false, execution is invalid.\n\n" +
                    "Algorithm Description:\n" +
                    "Clustering groups earthquakes based on spatial proximity.\n" +
                    "Uses DBSCAN-like algorithm for density-based clustering.\n" +
                    "Useful for identifying earthquake families and mainshock-aftershock\n" +
                    "sequences.";
                    
            case "SYN":
                return "Synthetic Test Generation (SYN) Parameters\n" +
                    "==========================================\n\n" +
                    "SYN-Specific Parameters:\n" +
                    "- randomSeed: Random seed for reproducibility.\n" +
                    "  Default: 100. Same seed = same synthetic data.\n\n" +
                    "- phsErr: Phase pick error (seconds).\n" +
                    "  Default: 0.1 sec. Simulates measurement uncertainty.\n\n" +
                    "- locErr: Location error (degrees).\n" +
                    "  Default: 0.03 deg. Initial hypocenter perturbation.\n\n" +
                    "- minSelectRate: Minimum rate of station selection.\n" +
                    "  Default: 0.2 (20%). Percentage of stations to use.\n\n" +
                    "- maxSelectRate: Maximum rate of station selection.\n" +
                    "  Default: 0.4 (40%).\n\n" +
                    "Algorithm Description:\n" +
                    "Generates synthetic earthquake data for testing location algorithms.\n" +
                    "Creates perturbed catalogs to evaluate algorithm robustness and\n" +
                    "accuracy under known conditions.\n\n" +
                    "Workflow:\n" +
                    "1. Load catalog with known hypocenters\n" +
                    "2. Add random noise to simulate picking errors\n" +
                    "3. Randomly select subset of stations\n" +
                    "4. Output as synthetic test data";
                    
            default:
                return "Parameter Help\n" +
                    "===============\n\n" +
                    "Select a location mode to view mode-specific parameter information.";
        }
    }
}

