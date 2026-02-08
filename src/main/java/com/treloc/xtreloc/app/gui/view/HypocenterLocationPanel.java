package com.treloc.xtreloc.app.gui.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.PathSerializer;
import com.treloc.xtreloc.solver.HypoGridSearch;
import com.treloc.xtreloc.solver.HypoStationPairDiff;
import com.treloc.xtreloc.solver.SyntheticTest;
import com.treloc.xtreloc.solver.ConvergenceCallback;
import com.treloc.xtreloc.util.ModeNameMapper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.DefaultComboBoxModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Panel providing hypocenter location functionality.
 * This panel allows users to configure and execute various hypocenter location algorithms
 * including grid search, station pair difference, triple difference relocation, clustering, and synthetic tests.
 */
public class HypocenterLocationPanel extends JPanel {
    private static final Logger logger = Logger.getLogger(HypocenterLocationPanel.class.getName());
    
    private final MapView mapView;
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
    private JLabel outputDirLabel;
    private JLabel outputFileLabel;
    private JButton executeButton;
    private JButton cancelButton;
    private JButton exportJsonButton;
    private JTextArea logArea;
    private JPanel logPanel;
    private JScrollPane logScrollPane;
    private JLabel logCommentLabel;
    private JTextArea convergenceLogArea;
    private JPanel convergenceLogPanel;
    private JScrollPane convergenceLogScrollPane;
    private JLabel convergenceLogCommentLabel;
    private JPanel rightPanel;
    private JTabbedPane rightTabbedPane;
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
    
    /** LSQR solver parameters for triple difference relocation (TRD mode). */
    private JTextField lsqrAtolField;
    private JTextField lsqrBtolField;
    private JTextField lsqrConlimField;
    private JTextField lsqrIterLimField;
    private JCheckBox lsqrShowLogCheckBox;
    private JCheckBox lsqrCalcVarCheckBox;
    private JCheckBox showConvergenceLogCheckBox;
    
    /** Levenberg-Marquardt optimization parameters (LMO mode). */
    private JTextField lmInitialStepBoundField;
    private JTextField lmCostRelativeToleranceField;
    private JTextField lmParRelativeToleranceField;
    private JTextField lmOrthoToleranceField;
    private JTextField lmMaxEvaluationsField;
    private JTextField lmMaxIterationsField;
    
    /** Differential Evolution (DE) parameters. */
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
    
    /** Per-mode parameter cache so that switching back to a mode restores previously entered values. */
    private final Map<String, Map<String, String>> modeParameterCache = new HashMap<>();
    private String lastSelectedMode = "GRD";
    
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
    
    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("Hypocenter Location"));
        Color bgColor = UIManager.getColor("Panel.background");
        if (bgColor != null) {
            setBackground(bgColor);
        } else {
            setBackground(Color.WHITE);
        }
        
        leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        Color leftBgColor = UIManager.getColor("Panel.background");
        if (leftBgColor != null) {
            leftPanel.setBackground(leftBgColor);
        } else {
            leftPanel.setBackground(Color.WHITE);
        }
        
        final int PANEL_WIDTH = 450;
        // 幅はリサイズ可能にするため最大値は指定しない（Integer.MAX_VALUE は BoxLayout で問題を起こすため Short.MAX_VALUE を使用）
        final int maxWidth = Short.MAX_VALUE;
        
        JPanel modePanel = createModePanel();
        modePanel.setPreferredSize(new Dimension(PANEL_WIDTH, 70));
        modePanel.setMaximumSize(new Dimension(maxWidth, 70));
        leftPanel.add(modePanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        inputDataPanel = createInputDataPanel();
        inputDataPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 200));
        inputDataPanel.setMaximumSize(new Dimension(maxWidth, 200));
        leftPanel.add(inputDataPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        JPanel paramPanel = createParameterPanel();
        paramPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 240));
        paramPanel.setMaximumSize(new Dimension(maxWidth, 240));
        leftPanel.add(paramPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        JPanel outputPanel = createOutputPanel();
        outputPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 120));
        outputPanel.setMaximumSize(new Dimension(maxWidth, 120));
        leftPanel.add(outputPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        JPanel buttonPanel = createButtonPanel();
        leftPanel.add(buttonPanel);
        
        JScrollPane scrollPane = new JScrollPane(leftPanel);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        logPanel = createLogPanel();
        
        residualPlotPanel = new ResidualPlotPanel();
        residualPlotPanel.setMinimumSize(new Dimension(300, 250));
        residualPlotPanel.setPreferredSize(new Dimension(500, 400));
        setupConvergenceCallback();
        
        // Create convergence log panel with CardLayout to support mode-specific messages
        convergenceLogPanel = new JPanel(new BorderLayout());
        CardLayout convergenceCardLayout = new CardLayout();
        JPanel convergenceContentPanel = new JPanel(convergenceCardLayout);
        convergenceContentPanel.setMinimumSize(new Dimension(300, 250));
        convergenceContentPanel.setPreferredSize(new Dimension(500, 400));
        
        // Add residual plot panel
        convergenceContentPanel.add(residualPlotPanel, "PLOT");
        
        // Create message label for SYN mode
        convergenceLogCommentLabel = new JLabel(
            "<html><div style='text-align: left; padding: 20px; color: #333;'>" +
            "<div style='font-size: 18px; font-weight: bold;'>Convergence log is available only for location modes</div>" +
            "</div></html>"
        );
        convergenceLogCommentLabel.setHorizontalAlignment(SwingConstants.LEFT);
        convergenceLogCommentLabel.setVerticalAlignment(SwingConstants.CENTER);
        convergenceLogCommentLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        convergenceLogCommentLabel.setMinimumSize(new Dimension(300, 250));
        convergenceLogCommentLabel.setPreferredSize(new Dimension(500, 400));
        convergenceContentPanel.add(convergenceLogCommentLabel, "MESSAGE");
        
        convergenceLogPanel.add(convergenceContentPanel, BorderLayout.CENTER);
        convergenceLogPanel.setMinimumSize(new Dimension(300, 250));
        convergenceLogPanel.setPreferredSize(new Dimension(500, 400));
        
        if (modeCombo != null) {
            String initialMode = getSelectedModeAbbreviation();
            if (initialMode != null) {
                residualPlotPanel.setMode(initialMode);
            }
        }
        
        logPanel.setVisible(true);
        logPanel.setOpaque(true);
        updateLogPanelVisibility(true);
        
        convergenceLogPanel.setVisible(true);
        convergenceLogPanel.setOpaque(true);
        residualPlotPanel.setVisible(true);
        residualPlotPanel.setOpaque(true);
        
        rightTabbedPane = new JTabbedPane();
        rightTabbedPane.addTab("Execution Log", logPanel);
        rightTabbedPane.addTab("Convergence Log", convergenceLogPanel);
        rightTabbedPane.setSelectedIndex(0); // Default to Execution Log
        
        // Initialize convergence log display based on initial mode
        if (modeCombo != null) {
            String initialMode = getSelectedModeAbbreviation();
            if (initialMode != null) {
                updateConvergenceLogForMode(initialMode);
            }
        }
        
        rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(rightTabbedPane, BorderLayout.CENTER);
        
        loadLogHistory();
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, rightPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(400);
        
        add(splitPane, BorderLayout.CENTER);
        
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
        return rightPanel != null ? rightPanel : logPanel;
    }
    
    
    private void updateLayoutForMode(String mode) {
        if (inputDataPanel != null) {
            updateInputDataPanelForMode(mode);
        }
        updateConvergenceLogForMode(mode);
    }
    
    /**
     * Updates Convergence Log panel based on selected mode.
     * For SYN mode, shows available modes message instead of convergence log.
     * For CLS mode, shows k-distance plot layout.
     */
    private void updateConvergenceLogForMode(String mode) {
        if (rightTabbedPane == null || convergenceLogPanel == null) {
            return;
        }
        
        boolean isSynMode = "SYN".equals(mode);
        boolean isClsMode = "CLS".equals(mode);
        
        // Enable/disable Convergence Log tab
        int convergenceLogTabIndex = 1;
        if (rightTabbedPane.getTabCount() > convergenceLogTabIndex) {
            rightTabbedPane.setEnabledAt(convergenceLogTabIndex, !isSynMode);
        }
        
        // Update content based on mode
        if (isSynMode) {
            // Show message for SYN mode
            if (convergenceLogCommentLabel != null) {
                convergenceLogCommentLabel.setText(
                    "<html><div style='text-align: left; padding: 20px; color: #333;'>" +
                    "<div style='font-size: 18px; font-weight: bold;'>Convergence log is available only for location modes</div>" +
                    "</div></html>"
                );
            }
            
            // Show message instead of plot
            Component[] components = convergenceLogPanel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel) {
                    LayoutManager layout = ((JPanel) comp).getLayout();
                    if (layout instanceof CardLayout) {
                        CardLayout cardLayout = (CardLayout) layout;
                        cardLayout.show((JPanel) comp, "MESSAGE");
                        break;
                    }
                }
            }
        } else if (isClsMode) {
            // For CLS mode, show k-distance plot layout
            if (residualPlotPanel != null) {
                // Switch to k-distance plot view (empty state will be shown until data is available)
                residualPlotPanel.setMode("CLS");
            }
            
            // Show plot (k-distance will be displayed when clustering is executed)
            Component[] components = convergenceLogPanel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel) {
                    LayoutManager layout = ((JPanel) comp).getLayout();
                    if (layout instanceof CardLayout) {
                        CardLayout cardLayout = (CardLayout) layout;
                        cardLayout.show((JPanel) comp, "PLOT");
                        break;
                    }
                }
            }
        } else {
            // For other modes, show residual convergence plot
            if (residualPlotPanel != null) {
                residualPlotPanel.setMode(mode);
            }
            
            // Show plot
            Component[] components = convergenceLogPanel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel) {
                    LayoutManager layout = ((JPanel) comp).getLayout();
                    if (layout instanceof CardLayout) {
                        CardLayout cardLayout = (CardLayout) layout;
                        cardLayout.show((JPanel) comp, "PLOT");
                        break;
                    }
                }
            }
        }
        
        convergenceLogPanel.revalidate();
        convergenceLogPanel.repaint();
    }
    
    private void updateInputDataPanelForMode(String mode) {
        boolean isSynOrClsMode = "SYN".equals(mode) || "CLS".equals(mode);
        boolean isTrdMode = "TRD".equals(mode);
        boolean isSynOrClsOrTrdMode = isSynOrClsMode || isTrdMode;
        boolean isOtherMode = !isSynOrClsMode;
        
        if (targetDirField != null) {
            targetDirField.setEnabled(isOtherMode || isTrdMode);
            if (!(isOtherMode || isTrdMode)) {
                targetDirField.setBackground(new Color(240, 240, 240));
                targetDirField.setForeground(new Color(150, 150, 150));
            } else {
                targetDirField.setBackground(Color.WHITE);
                targetDirField.setForeground(Color.BLACK);
            }
        }
        if (selectDirButton != null) {
            selectDirButton.setEnabled(isOtherMode || isTrdMode);
        }
        if (targetDirLabel != null) {
            targetDirLabel.setEnabled(isOtherMode || isTrdMode);
            if (!(isOtherMode || isTrdMode)) {
                targetDirLabel.setForeground(new Color(150, 150, 150));
            } else {
                targetDirLabel.setForeground(null); // Reset to default color
            }
        }
        
        if (catalogFileField != null) {
            catalogFileField.setEnabled(isSynOrClsOrTrdMode);
            if (!isSynOrClsOrTrdMode) {
                catalogFileField.setBackground(new Color(240, 240, 240));
                catalogFileField.setForeground(new Color(150, 150, 150));
            } else {
                catalogFileField.setBackground(Color.WHITE);
                catalogFileField.setForeground(Color.BLACK);
            }
        }
        if (selectCatalogButton != null) {
            selectCatalogButton.setEnabled(isSynOrClsOrTrdMode);
        }
        if (catalogFileLabel != null) {
            catalogFileLabel.setEnabled(isSynOrClsOrTrdMode);
            if (!isSynOrClsOrTrdMode) {
                catalogFileLabel.setForeground(new Color(150, 150, 150));
            } else {
                catalogFileLabel.setForeground(null); // Reset to default color
            }
        }
        
        boolean isClsMode = "CLS".equals(mode);
        if (taupModelCombo != null) {
            taupModelCombo.setEnabled(!isClsMode);
            if (isClsMode) {
                taupModelCombo.setBackground(new Color(240, 240, 240));
                taupModelCombo.setForeground(new Color(150, 150, 150));
            } else {
                taupModelCombo.setBackground(Color.WHITE);
                taupModelCombo.setForeground(Color.BLACK);
            }
        }
        if (taupModelLabel != null) {
            taupModelLabel.setEnabled(!isClsMode);
            if (isClsMode) {
                taupModelLabel.setForeground(new Color(150, 150, 150));
            } else {
                taupModelLabel.setForeground(null);
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
    }
    
    private JPanel createInputDataPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        Color bgColor = UIManager.getColor("Panel.background");
        panel.setBackground(bgColor != null ? bgColor : Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 220), 1),
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                "📁 Input Data",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13),
                new Color(60, 60, 80)
            )
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0;
        targetDirLabel = new JLabel("Directory:");
        panel.add(targetDirLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        targetDirField = new JTextField();
        targetDirField.setEditable(false);
        targetDirField.setHorizontalAlignment(JTextField.LEFT);
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
        selectDirButton.setBackground(new Color(70, 130, 180));
        selectDirButton.setForeground(Color.WHITE);
        selectDirButton.setFocusPainted(false);
        selectDirButton.setBorderPainted(false);
        selectDirButton.setOpaque(true);
        selectDirButton.addActionListener(e -> selectTargetDirectory());
        JPanel dirPanel = new JPanel(new BorderLayout());
        dirPanel.add(selectDirButton, BorderLayout.WEST);
        dirPanel.add(targetDirField, BorderLayout.CENTER);
        panel.add(dirPanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 1;
        catalogFileLabel = new JLabel("Catalog File:");
        panel.add(catalogFileLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        catalogFileField = new JTextField();
        catalogFileField.setEditable(true);
        catalogFileField.setHorizontalAlignment(JTextField.LEFT);
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
        selectCatalogButton.setBackground(new Color(70, 130, 180));
        selectCatalogButton.setForeground(Color.WHITE);
        selectCatalogButton.setFocusPainted(false);
        selectCatalogButton.setBorderPainted(false);
        selectCatalogButton.setOpaque(true);
        selectCatalogButton.addActionListener(e -> selectCatalogFile());
        JPanel catalogPanel = new JPanel(new BorderLayout());
        catalogPanel.add(selectCatalogButton, BorderLayout.WEST);
        catalogPanel.add(catalogFileField, BorderLayout.CENTER);
        panel.add(catalogPanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Station File:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        stationFileField = new JTextField();
        stationFileField.setEditable(true);
        stationFileField.setHorizontalAlignment(JTextField.LEFT);
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
        selectStationButton.setBackground(new Color(70, 130, 180));
        selectStationButton.setForeground(Color.WHITE);
        selectStationButton.setFocusPainted(false);
        selectStationButton.setBorderPainted(false);
        selectStationButton.setOpaque(true);
        selectStationButton.addActionListener(e -> selectStationFile());
        JPanel stationPanel = new JPanel(new BorderLayout());
        stationPanel.add(selectStationButton, BorderLayout.WEST);
        stationPanel.add(stationFileField, BorderLayout.CENTER);
        panel.add(stationPanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 3;
        taupModelLabel = new JLabel("Velocity Model:");
        panel.add(taupModelLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        taupModelCombo = new JComboBox<>(new String[]{"prem", "iasp91", "ak135", "ak135f", "Select from file..."});
        taupModelCombo.setSelectedItem("prem");
        taupModelCombo.addActionListener(e -> {
            String selected = (String) taupModelCombo.getSelectedItem();
            if (selected != null) {
                if ("Select from file...".equals(selected)) {
                    selectTaupFile();
                    // Reset selection to prevent re-triggering on focus
                    if (selectedTaupFile != null) {
                        // Keep the selected file, already set in selectTaupFile()
                    } else {
                        taupModelCombo.setSelectedItem("prem");
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
        Color bgColor = UIManager.getColor("Panel.background");
        panel.setBackground(bgColor != null ? bgColor : Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 200, 200), 1),
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                "💾 Output Data",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13),
                new Color(80, 60, 60)
            )
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0;
        outputDirLabel = new JLabel("Output Directory:");
        outputDirLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        panel.add(outputDirLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        outputDirField = new JTextField();
        outputDirField.setEditable(false);
        outputDirField.setHorizontalAlignment(JTextField.LEFT);
        outputDirField.setBackground(new Color(255, 255, 255));
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
        selectOutputDirButton.setBackground(new Color(70, 130, 180));
        selectOutputDirButton.setForeground(Color.WHITE);
        selectOutputDirButton.setFocusPainted(false);
        selectOutputDirButton.setBorderPainted(false);
        selectOutputDirButton.setOpaque(true);
        JPanel outputDirPanel = new JPanel(new BorderLayout());
        outputDirPanel.add(selectOutputDirButton, BorderLayout.WEST);
        outputDirPanel.add(outputDirField, BorderLayout.CENTER);
        panel.add(outputDirPanel, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 1;
        outputFileLabel = new JLabel("Output File:");
        outputFileLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        panel.add(outputFileLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        outputFileField = new JTextField();
        outputFileField.setEditable(true);
        outputFileField.setHorizontalAlignment(JTextField.LEFT);
        outputFileField.setBackground(new Color(255, 255, 255));
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
        selectOutputFileButton.setBackground(new Color(70, 130, 180));
        selectOutputFileButton.setForeground(Color.WHITE);
        selectOutputFileButton.setFocusPainted(false);
        selectOutputFileButton.setBorderPainted(false);
        selectOutputFileButton.setOpaque(true);
        JPanel outputFilePanel = new JPanel(new BorderLayout());
        outputFilePanel.add(selectOutputFileButton, BorderLayout.WEST);
        outputFilePanel.add(outputFileField, BorderLayout.CENTER);
        panel.add(outputFilePanel, gbc);
        gbc.weightx = 0.0;
        
        return panel;
    }
    
    private void selectOutputFile() {
        fileChooser.setDialogTitle("Select Output File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "All files (*.*)", "*"));
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
            
            File catalogFile = new File(selectedOutputDir, "catalog.csv");
            outputFileField.setText(catalogFile.getAbsolutePath());
            
            updateExecuteButtonState();
            appendLog("Output directory selected: " + selectedOutputDir.getAbsolutePath());
            appendLog("Output file auto-configured: " + catalogFile.getAbsolutePath());
        }
    }
    
    private JPanel createModePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        Color bgColor = UIManager.getColor("Panel.background");
        panel.setBackground(bgColor != null ? bgColor : Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 220, 200), 1),
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                "⚙️ Mode Settings",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13),
                new Color(60, 80, 60)
            )
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Mode:"), gbc);
        gbc.gridx = 1;
        // Use display names in ComboBox
        modeCombo = new JComboBox<>(ModeNameMapper.getAllDisplayNames());
        modeCombo.setSelectedItem(ModeNameMapper.getDisplayName("GRD"));
        modeCombo.addActionListener(e -> {
            String displayName = (String) modeCombo.getSelectedItem();
            String mode = ModeNameMapper.getAbbreviation(displayName);
            saveCurrentModeStateToCache(lastSelectedMode);
            updateParameterFields();
            updateLayoutForMode(mode);
            restoreModeStateFromCache(mode);
            lastSelectedMode = mode;
            if (residualPlotPanel != null) {
                residualPlotPanel.setMode(mode);
            }
        });
        panel.add(modeCombo, gbc);
        
        return panel;
    }
    
    private JPanel createParameterPanel() {
        parameterPanel = new JPanel(new BorderLayout());
        Color bgColor = UIManager.getColor("Panel.background");
        parameterPanel.setBackground(bgColor != null ? bgColor : Color.WHITE);
        
        // Create title panel with help button
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        JLabel titleLabel = new JLabel("📊 Parameters");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titleLabel.setForeground(new Color(80, 80, 60));
        titlePanel.add(titleLabel, BorderLayout.WEST);
        
        // Create help button with icon
        try {
            java.net.URL helpImageUrl = HypocenterLocationPanel.class.getResource("/images/help.png");
            if (helpImageUrl != null) {
                ImageIcon helpIcon = new ImageIcon(helpImageUrl);
                // Scale icon to small size
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
        
        // Create border with custom title component
        javax.swing.border.Border lineBorder = BorderFactory.createLineBorder(new Color(220, 220, 200), 1);
        javax.swing.border.Border emptyBorder = BorderFactory.createEmptyBorder(5, 5, 5, 5);
        parameterPanel.setBorder(BorderFactory.createCompoundBorder(lineBorder, emptyBorder));
        
        // Add title panel at top
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(titlePanel, BorderLayout.CENTER);
        topPanel.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        parameterPanel.add(topPanel, BorderLayout.NORTH);
        
        String[] columnNames = {"Parameter", "Value", "Unit"};
        parameterTableModel = new javax.swing.table.DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1; // Only the value column is editable
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
        
        lsqrAtolField = new JTextField("1e-6", 10);
        lsqrBtolField = new JTextField("1e-6", 10);
        lsqrConlimField = new JTextField("1e8", 10);
        lsqrIterLimField = new JTextField("1000", 10);
        lsqrShowLogCheckBox = new JCheckBox("Show LSQR Convergence Log", true);
        lsqrCalcVarCheckBox = new JCheckBox("Calculate Variance (Error Estimation)", true);
        
        showConvergenceLogCheckBox = new JCheckBox("Show Convergence Log", true);
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
        Color bgColor = UIManager.getColor("Panel.background");
        panel.setBackground(bgColor != null ? bgColor : Color.WHITE);
        
        executeButton = new JButton("▶ Execute");
        executeButton.setEnabled(false);
        executeButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        executeButton.setPreferredSize(new Dimension(120, 35));
        executeButton.setBackground(new Color(50, 150, 50));
        executeButton.setForeground(Color.WHITE);
        executeButton.setFocusPainted(false);
        executeButton.setBorderPainted(false);
        executeButton.setOpaque(true);
        executeButton.addActionListener(e -> executeLocation());
        panel.add(executeButton);
        
        cancelButton = new JButton("⏹ Cancel");
        cancelButton.setEnabled(false);
        cancelButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        cancelButton.setPreferredSize(new Dimension(120, 35));
        cancelButton.setBackground(new Color(200, 50, 50));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        cancelButton.setOpaque(true);
        cancelButton.addActionListener(e -> cancelExecution());
        panel.add(cancelButton);
        
        exportJsonButton = new JButton("Export (Json)");
        exportJsonButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        exportJsonButton.setPreferredSize(new Dimension(120, 35));
        exportJsonButton.setBackground(new Color(70, 130, 180));
        exportJsonButton.setForeground(Color.WHITE);
        exportJsonButton.setFocusPainted(false);
        exportJsonButton.setBorderPainted(false);
        exportJsonButton.setOpaque(true);
        exportJsonButton.addActionListener(e -> exportConfigToJson());
        panel.add(exportJsonButton);
        
        return panel;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        Color bgColor = UIManager.getColor("Panel.background");
        panel.setBackground(bgColor != null ? bgColor : Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 200), 1),
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                "📝 Execution Log",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13),
                new Color(60, 60, 80)
            )
        ));
        
        panel.setMinimumSize(new Dimension(300, 100));
        panel.setPreferredSize(new Dimension(500, 200));
        
        CardLayout cardLayout = new CardLayout();
        JPanel contentPanel = new JPanel(cardLayout);
        contentPanel.setMinimumSize(new Dimension(300, 100));
        contentPanel.setPreferredSize(new Dimension(500, 200));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        
        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(500, 150));
        logScrollPane.setMinimumSize(new Dimension(300, 100));
        contentPanel.add(logScrollPane, "LOG");
        
        logCommentLabel = new JLabel(
            "<html><div style='text-align: center; padding: 20px; color: #666;'>"
            + "Execution log is hidden. Enable 'Show Execution Log' to display execution logs."
            + "</div></html>"
        );
        logCommentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logCommentLabel.setVerticalAlignment(SwingConstants.CENTER);
        logCommentLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        logCommentLabel.setMinimumSize(new Dimension(300, 100));
        logCommentLabel.setPreferredSize(new Dimension(500, 200));
        contentPanel.add(logCommentLabel, "COMMENT");
        
        cardLayout.show(contentPanel, "COMMENT");
        
        panel.add(contentPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createConvergenceLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        Color bgColor = UIManager.getColor("Panel.background");
        panel.setBackground(bgColor != null ? bgColor : Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 200), 1),
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                "📊 Convergence Log (LMO/MCMC/DE/TRD)",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13),
                new Color(60, 60, 80)
            )
        ));
        
        panel.setMinimumSize(new Dimension(300, 100));
        panel.setPreferredSize(new Dimension(500, 200));
        
        CardLayout cardLayout = new CardLayout();
        JPanel contentPanel = new JPanel(cardLayout);
        contentPanel.setMinimumSize(new Dimension(300, 100));
        contentPanel.setPreferredSize(new Dimension(500, 150));
        
        convergenceLogArea = new JTextArea();
        convergenceLogArea.setEditable(false);
        convergenceLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        convergenceLogArea.setBackground(new Color(20, 20, 30));
        convergenceLogArea.setForeground(new Color(200, 220, 255));
        
        convergenceLogScrollPane = new JScrollPane(convergenceLogArea);
        convergenceLogScrollPane.setPreferredSize(new Dimension(500, 120));
        convergenceLogScrollPane.setMinimumSize(new Dimension(300, 100));
        contentPanel.add(convergenceLogScrollPane, "LOG");
        
        convergenceLogCommentLabel = new JLabel(
            "<html><div style='text-align: center; padding: 20px; color: #666;'>" +
            "This panel will be displayed when 'Show Convergence Log' is enabled for specific modes (LMO, MCMC, DE, TRD)." +
            "</div></html>"
        );
        convergenceLogCommentLabel.setName("convergenceLogCommentLabel"); // For easy access
        convergenceLogCommentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        convergenceLogCommentLabel.setVerticalAlignment(SwingConstants.CENTER);
        convergenceLogCommentLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        convergenceLogCommentLabel.setMinimumSize(new Dimension(300, 100));
        convergenceLogCommentLabel.setPreferredSize(new Dimension(500, 150));
        contentPanel.add(convergenceLogCommentLabel, "COMMENT");
        
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
        
        Component[] components = convergenceLogPanel.getComponents();
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
        
        convergenceLogPanel.revalidate();
        convergenceLogPanel.repaint();
    }
    
    /**
     * Sets up the convergence callback for real-time residual plotting.
     */
    private void setupConvergenceCallback() {
        convergenceCallback = new ConvergenceCallback() {
            @Override
            public void onResidualUpdate(int iteration, double residual) {
                String eventName = getCurrentEventName();
                
                // Update graph if panel is visible
                if (residualPlotPanel != null && residualPlotPanel.isVisible()) {
                    if (eventName != null) {
                        residualPlotPanel.addResidualPoint(eventName, iteration, residual);
                    } else {
                        residualPlotPanel.addResidualPoint(iteration, residual);
                    }
                }
                
                // Update convergence log text if panel is visible
                if (convergenceLogPanel != null && convergenceLogPanel.isVisible() && 
                    convergenceLogArea != null && convergenceLogArea.isVisible()) {
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
                
                if (convergenceLogPanel != null && convergenceLogPanel.isVisible() && 
                    convergenceLogArea != null && convergenceLogArea.isVisible()) {
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
                
                if (convergenceLogPanel != null && convergenceLogPanel.isVisible() && 
                    convergenceLogArea != null && convergenceLogArea.isVisible()) {
                    String logMessage = String.format("Cluster %d, Iteration %d: Residual = %.6f s", 
                        clusterId, iteration, residual);
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
                
                // Update graph if panel is visible
                if (residualPlotPanel != null && residualPlotPanel.isVisible()) {
                    if (eventName != null) {
                        residualPlotPanel.addResidualPoint(eventName, iteration, residual);
                    } else {
                        residualPlotPanel.addResidualPoint(iteration, residual);
                    }
                }
                
                // Update convergence log text if panel is visible
                if (convergenceLogPanel != null && convergenceLogPanel.isVisible() && 
                    convergenceLogArea != null && convergenceLogArea.isVisible()) {
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
        fileChooser.setDialogTitle("Select TauP Model File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "TauP model files (*.taup, *.nd)", "taup", "nd"));
        fileChooser.setAcceptAllFileFilterUsed(true);
        
        // Set initial directory to $HOME if not already set
        String homeDir = System.getProperty("user.home");
        if (homeDir != null && !homeDir.isEmpty()) {
            fileChooser.setCurrentDirectory(new File(homeDir));
        }
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedTaupFile = fileChooser.getSelectedFile();
            String filePath = selectedTaupFile.getAbsolutePath();
            
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) taupModelCombo.getModel();
            
            // Check if this file is already in the list
            boolean alreadyExists = false;
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).equals(filePath)) {
                    alreadyExists = true;
                    break;
                }
            }
            
            // Add the file to the model if it doesn't already exist
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
        String filePath = catalogFileField.getText().trim();
        if (!filePath.isEmpty()) {
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                return file;
            }
        }
        return null;
    }
    
    private void updateParameterFields() {
        if (parameterTableModel == null) {
            return;
        }
        
        parameterTableModel.setRowCount(0);
        
        String selectedMode = getSelectedModeAbbreviation();
        
        if (!"SYN".equals(selectedMode) && !"CLS".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Parallelization (numJobs)", numJobsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Weight Threshold (threshold)", thresholdField.getText(), "weight (empty or 0.0 for no filtering)"});
            parameterTableModel.addRow(new Object[]{"Maximum Depth (hypBottom)", hypBottomField.getText(), "km"});
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
            parameterTableModel.addRow(new Object[]{"Show Convergence Log (showConvergenceLog)", showConvergenceLogCheckBox.isSelected() ? "true" : "false", "Display convergence log in execution log"});
        } else if ("TRD".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Iteration Count (iterNum)", trdIterNumField.getText(), "Comma-separated (e.g., 10,10)"});
            parameterTableModel.addRow(new Object[]{"Distance Threshold (distKm)", trdDistKmField.getText(), "km, comma-separated (e.g., 50,20)"});
            parameterTableModel.addRow(new Object[]{"Damping Factor (dampFact)", trdDampFactField.getText(), "Comma-separated (e.g., 0,1)"});
            parameterTableModel.addRow(new Object[]{"LSQR ATOL (atol)", lsqrAtolField.getText(), "Stopping tolerance (default: 1e-6)"});
            parameterTableModel.addRow(new Object[]{"LSQR BTOL (btol)", lsqrBtolField.getText(), "Stopping tolerance (default: 1e-6)"});
            parameterTableModel.addRow(new Object[]{"LSQR CONLIM (conlim)", lsqrConlimField.getText(), "Condition number limit (default: 1e8)"});
            parameterTableModel.addRow(new Object[]{"LSQR Iteration Limit (iter_lim)", lsqrIterLimField.getText(), "Maximum iterations (default: 1000)"});
            parameterTableModel.addRow(new Object[]{"LSQR Show Log (showLSQR)", lsqrShowLogCheckBox.isSelected() ? "true" : "false", "Display LSQR iteration log"});
            parameterTableModel.addRow(new Object[]{"LSQR Calculate Variance (calcVar)", lsqrCalcVarCheckBox.isSelected() ? "true" : "false", "Estimate error covariance diagonal elements"});
            parameterTableModel.addRow(new Object[]{"Show Convergence Log (showConvergenceLog)", showConvergenceLogCheckBox.isSelected() ? "true" : "false", "Display convergence log in execution log"});
        } else if ("MCMC".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Sample Count (nSamples)", nSamplesField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Burn-in Period (burnIn)", burnInField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Step Size (stepSize)", stepSizeField.getText(), "degree"});
            parameterTableModel.addRow(new Object[]{"Depth Step Size (stepSizeDepth)", stepSizeDepthField.getText(), "km"});
            parameterTableModel.addRow(new Object[]{"Temperature Parameter (temperature)", temperatureField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Show Convergence Log (showConvergenceLog)", showConvergenceLogCheckBox.isSelected() ? "true" : "false", "Display convergence log in execution log"});
        } else if ("DE".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Population Size (populationSize)", dePopulationSizeField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Max Generations (maxGenerations)", deMaxGenerationsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Scaling Factor (scalingFactor)", deScalingFactorField.getText(), "[0-2]"});
            parameterTableModel.addRow(new Object[]{"Crossover Rate (crossoverRate)", deCrossoverRateField.getText(), "[0-1]"});
            parameterTableModel.addRow(new Object[]{"Show Convergence Log (showConvergenceLog)", showConvergenceLogCheckBox.isSelected() ? "true" : "false", "Display convergence log in execution log"});
        } else if ("CLS".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Weight Threshold (threshold)", thresholdField.getText(), "weight (empty or 0.0 for no filtering)"});
            parameterTableModel.addRow(new Object[]{"Minimum Points (minPts)", minPtsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Epsilon (eps)", epsField.getText(), "km (negative value for auto-estimation)"});
            parameterTableModel.addRow(new Object[]{"Data Inclusion Rate (epsPercentile)", epsPercentileField.getText(), "[0-1] (elbow method if eps<0 and empty)"});
            parameterTableModel.addRow(new Object[]{"RMS Threshold (rmsThreshold)", rmsThresholdField.getText(), "s (empty for no filtering)"});
            parameterTableModel.addRow(new Object[]{"Location Error Threshold (locErrThreshold)", locErrThresholdField.getText(), "km (xerr and yerr must both be <= this, empty for no filtering)"});
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
    
    /**
     * Saves the current parameter table, shared fields, and input paths into the per-mode cache for the given mode.
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
    }
    
    /**
     * Restores parameter values and input paths for the given mode from the cache into fields and refreshes the table.
     */
    private void restoreModeStateFromCache(String mode) {
        Map<String, String> cache = modeParameterCache.get(mode);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> e : cache.entrySet()) {
            String k = e.getKey();
            if ("targetDir".equals(k) || "outputDir".equals(k) || "stationFile".equals(k) || "catalogFile".equals(k)) {
                continue;
            }
            setParameterValue(k, e.getValue());
        }
        String v;
        if ((v = cache.get("targetDir")) != null && targetDirField != null) {
            targetDirField.setText(v);
            if (!v.isEmpty()) {
                File f = new File(v);
                if (f.exists()) selectedTargetDir = f.isDirectory() ? f : f.getParentFile();
            }
        }
        if ((v = cache.get("outputDir")) != null && outputDirField != null) {
            outputDirField.setText(v);
            if (!v.isEmpty()) {
                File f = new File(v);
                if (f.exists() && f.isDirectory()) selectedOutputDir = f;
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
        updateParameterFields();
    }
    
    /**
     * Sets a parameter value by key (updates the corresponding field). Used when restoring cached mode state.
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
            case "randomSeed": if (randomSeedField != null) randomSeedField.setText(value); break;
            case "phsErr": if (phsErrField != null) phsErrField.setText(value); break;
            case "locErr": if (locErrField != null) locErrField.setText(value); break;
            case "minSelectRate": if (minSelectRateField != null) minSelectRateField.setText(value); break;
            case "maxSelectRate": if (maxSelectRateField != null) maxSelectRateField.setText(value); break;
            default:
                break;
        }
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
        
        boolean canExecute;
        String missingItem = null;
        
        if ("SYN".equals(selectedMode) || "CLS".equals(selectedMode)) {
            File catalogFile = getCatalogFileFromField();
            if (catalogFile == null || !catalogFile.exists()) {
                missingItem = "Catalog file";
            } else if (selectedOutputDir == null) {
                missingItem = "Output directory";
            } else if (selectedStationFile == null || !selectedStationFile.exists()) {
                missingItem = "Station file";
            }
            canExecute = catalogFile != null && catalogFile.exists() &&
                        selectedOutputDir != null &&
                        selectedStationFile != null && selectedStationFile.exists();
            
            if ("CLS".equals(selectedMode)) {
                String minPtsStr = getParameterValue("minPts");
                String epsStr = getParameterValue("eps");
                canExecute = canExecute &&
                    !minPtsStr.isEmpty() && !epsStr.isEmpty();
            }
        } else if ("TRD".equals(selectedMode)) {
            File catalogFile = getCatalogFileFromField();
            boolean hasCatalog = catalogFile != null && catalogFile.exists();
            boolean hasDatFiles = selectedTargetDir != null && !findDatFiles(selectedTargetDir).isEmpty();
            
            if (!hasCatalog && !hasDatFiles) {
                missingItem = "Catalog file or target directory (dat files)";
            } else if (selectedOutputDir == null) {
                missingItem = "Output directory";
            } else if (selectedStationFile == null || !selectedStationFile.exists()) {
                missingItem = "Station file";
            }
            
            canExecute = (hasCatalog || hasDatFiles) &&
                        selectedOutputDir != null &&
                        selectedStationFile != null && selectedStationFile.exists();
            
            if (canExecute) {
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
                
                canExecute = !iterNumStr.isEmpty() && !distKmStr.isEmpty() && !dampFactStr.isEmpty();
                
                if (canExecute) {
                    String[] iterNumArray = iterNumStr.split(",");
                    String[] distKmArray = distKmStr.split(",");
                    String[] dampFactArray = dampFactStr.split(",");
                    if (iterNumArray.length != distKmArray.length || 
                        iterNumArray.length != dampFactArray.length) {
                        canExecute = false;
                        missingItem = "TRD parameter array lengths do not match";
                    }
                }
            }
        } else {
            canExecute = selectedTargetDir != null && 
                       selectedStationFile != null &&
                       selectedOutputDir != null;
            
            String thresholdStr = getParameterValue("threshold");
            String hypBottomStr = getParameterValue("hypBottom");
            canExecute = canExecute && !thresholdStr.isEmpty() && !hypBottomStr.isEmpty();
            
            if ("GRD".equals(selectedMode)) {
                String totalGridsStr = getParameterValue("totalGrids");
                String numFocusStr = getParameterValue("numFocus");
                canExecute = canExecute && 
                    !totalGridsStr.isEmpty() && !numFocusStr.isEmpty();
            } else if ("LMO".equals(selectedMode)) {
            } else if ("TRD".equals(selectedMode)) {
            } else if ("MCMC".equals(selectedMode)) {
                String nSamplesStr = getParameterValue("nSamples");
                String burnInStr = getParameterValue("burnIn");
                String stepSizeStr = getParameterValue("stepSize");
                String stepSizeDepthStr = getParameterValue("stepSizeDepth");
                String temperatureStr = getParameterValue("temperature");
                canExecute = canExecute &&
                    !nSamplesStr.isEmpty() && !burnInStr.isEmpty() &&
                    !stepSizeStr.isEmpty() && !stepSizeDepthStr.isEmpty() && !temperatureStr.isEmpty();
            } else if ("DE".equals(selectedMode)) {
                String populationSizeStr = dePopulationSizeField != null ? dePopulationSizeField.getText().trim() : "";
                String maxGenerationsStr = deMaxGenerationsField != null ? deMaxGenerationsField.getText().trim() : "";
                String scalingFactorStr = deScalingFactorField != null ? deScalingFactorField.getText().trim() : "";
                String crossoverRateStr = deCrossoverRateField != null ? deCrossoverRateField.getText().trim() : "";
                canExecute = canExecute &&
                    !populationSizeStr.isEmpty() && !maxGenerationsStr.isEmpty() &&
                    !scalingFactorStr.isEmpty() && !crossoverRateStr.isEmpty();
            }
        }
        
        executeButton.setEnabled(canExecute);
        if (cancelButton != null) {
            cancelButton.setEnabled(false);
        }
        
        if (!canExecute && missingItem != null) {
            executeButton.setToolTipText("The following is required to execute: " + missingItem);
        } else if (!canExecute) {
            executeButton.setToolTipText("Please enter required settings to execute");
        } else {
            executeButton.setToolTipText(null);
        }
    }
    
    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now().toString() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    private void appendConvergenceLog(String message) {
        SwingUtilities.invokeLater(() -> {
            if (convergenceLogArea != null && convergenceLogArea.isVisible()) {
                convergenceLogArea.append("[" + java.time.LocalTime.now().toString() + "] " + message + "\n");
                convergenceLogArea.setCaretPosition(convergenceLogArea.getDocument().getLength());
            }
        });
    }
    
    /**
     * Loads and displays log history in the GUI log panel.
     * Includes a separator to distinguish from previous session.
     */
    private void loadLogHistory() {
        try {
            com.treloc.xtreloc.app.gui.util.AppSettings settings = 
                com.treloc.xtreloc.app.gui.util.AppSettings.load();
            int historyLines = settings.getHistoryLines();
            String history = com.treloc.xtreloc.app.gui.util.LogHistoryManager.getHistoryForGUI(historyLines);
            
            SwingUtilities.invokeLater(() -> {
                logArea.setText(history);
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        } catch (Exception e) {
            // If history loading fails, just start with empty log
            SwingUtilities.invokeLater(() -> {
                logArea.setText("───────────────────────────────────────────────────────────────\n");
                logArea.append("Current Session\n");
                logArea.append("───────────────────────────────────────────────────────────────\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }
    
    /**
     * Builds AppConfig from current UI state (common + current mode solver/modes).
     * Used by executeLocation() and exportConfigToJson().
     */
    private AppConfig buildConfigFromUI() {
        AppConfig cfg = new AppConfig();
        
        String selectedModel = (String) taupModelCombo.getSelectedItem();
        if (selectedModel != null && !"Select from file...".equals(selectedModel)) {
            cfg.taupFile = selectedModel;
        }
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
        cfg.solver = new java.util.HashMap<>();
        cfg.modes = new java.util.HashMap<>();
        
        if ("GRD".equals(selectedMode)) {
            var grdMode = cfg.modes.computeIfAbsent("GRD", k -> new AppConfig.ModeConfig());
            if (selectedTargetDir != null) grdMode.datDirectory = selectedTargetDir.toPath();
            if (selectedOutputDir != null) grdMode.outDirectory = selectedOutputDir.toPath();
            var grdSolver = cfg.solver.computeIfAbsent("GRD", k -> 
                new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
            if (grdSolver instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                String totalGridsStr = getParameterValue("totalGrids");
                String numFocusStr = getParameterValue("numFocus");
                if (!totalGridsStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) grdSolver)
                        .put("totalGrids", Integer.parseInt(totalGridsStr));
                }
                if (!numFocusStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) grdSolver)
                        .put("numFocus", Integer.parseInt(numFocusStr));
                }
            }
        } else if ("LMO".equals(selectedMode)) {
            var lmoMode = cfg.modes.computeIfAbsent("LMO", k -> new AppConfig.ModeConfig());
            if (selectedTargetDir != null) lmoMode.datDirectory = selectedTargetDir.toPath();
            if (selectedOutputDir != null) lmoMode.outDirectory = selectedOutputDir.toPath();
            var lmoSolver = cfg.solver.computeIfAbsent("LMO", k -> 
                new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
            if (lmoSolver instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                String showConvergenceLogStr = getParameterValue("showConvergenceLog");
                boolean showConvergenceLog = false;
                if (!showConvergenceLogStr.isEmpty()) {
                    showConvergenceLog = "true".equalsIgnoreCase(showConvergenceLogStr.trim()) || "1".equals(showConvergenceLogStr.trim());
                } else if (showConvergenceLogCheckBox != null) {
                    showConvergenceLog = showConvergenceLogCheckBox.isSelected();
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) lmoSolver).put("showConvergenceLog", showConvergenceLog);
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
                    ((com.fasterxml.jackson.databind.node.ObjectNode) lmoSolver)
                        .put("initialStepBoundFactor", Double.parseDouble(initialStepBoundStr));
                }
                if (!costRelativeTolStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) lmoSolver)
                        .put("costRelativeTolerance", Double.parseDouble(costRelativeTolStr));
                }
                if (!parRelativeTolStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) lmoSolver)
                        .put("parRelativeTolerance", Double.parseDouble(parRelativeTolStr));
                }
                if (!orthoTolStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) lmoSolver)
                        .put("orthoTolerance", Double.parseDouble(orthoTolStr));
                }
                if (!maxEvalStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) lmoSolver)
                        .put("maxEvaluations", Integer.parseInt(maxEvalStr));
                }
                if (!maxIterStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) lmoSolver)
                        .put("maxIterations", Integer.parseInt(maxIterStr));
                }
            }
        } else if ("TRD".equals(selectedMode)) {
            var trdMode = cfg.modes.computeIfAbsent("TRD", k -> new AppConfig.ModeConfig());
            
            File catalogFile = getCatalogFileFromField();
            if (catalogFile != null && catalogFile.exists()) {
                trdMode.catalogFile = catalogFile.getAbsolutePath();
            }
            
            if (selectedTargetDir != null) {
                trdMode.datDirectory = selectedTargetDir.toPath();
            }
            
            if (selectedOutputDir != null) {
                trdMode.outDirectory = selectedOutputDir.toPath();
            }
            
            var trdSolver = cfg.solver.computeIfAbsent("TRD", k -> 
                new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
            if (trdSolver instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
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
                    var iterNumNode = ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver)
                        .putArray("iterNum");
                    for (String val : iterNumArray) {
                        iterNumNode.add(Integer.parseInt(val.trim()));
                    }
                }
                if (!distKmStr.isEmpty()) {
                    String[] distKmArray = distKmStr.split(",");
                    var distKmNode = ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver)
                        .putArray("distKm");
                    for (String val : distKmArray) {
                        distKmNode.add(Integer.parseInt(val.trim()));
                    }
                }
                if (!dampFactStr.isEmpty()) {
                    String[] dampFactArray = dampFactStr.split(",");
                    var dampFactNode = ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver)
                        .putArray("dampFact");
                    for (String val : dampFactArray) {
                        dampFactNode.add(Integer.parseInt(val.trim()));
                    }
                }
                
                String showConvergenceLogStr = getParameterValue("showConvergenceLog");
                boolean showConvergenceLog = false;
                if (!showConvergenceLogStr.isEmpty()) {
                    showConvergenceLog = "true".equalsIgnoreCase(showConvergenceLogStr.trim()) || "1".equals(showConvergenceLogStr.trim());
                } else if (showConvergenceLogCheckBox != null) {
                    showConvergenceLog = showConvergenceLogCheckBox.isSelected();
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver).put("showConvergenceLog", showConvergenceLog);
                if (showConvergenceLogCheckBox != null) {
                    showConvergenceLogCheckBox.setSelected(showConvergenceLog);
                }
                
                if (lsqrAtolField != null && !lsqrAtolField.getText().trim().isEmpty()) {
                    try {
                        double atol = Double.parseDouble(lsqrAtolField.getText().trim());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver).put("lsqrAtol", atol);
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid LSQR ATOL value: " + lsqrAtolField.getText());
                    }
                }
                if (lsqrBtolField != null && !lsqrBtolField.getText().trim().isEmpty()) {
                    try {
                        double btol = Double.parseDouble(lsqrBtolField.getText().trim());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver).put("lsqrBtol", btol);
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid LSQR BTOL value: " + lsqrBtolField.getText());
                    }
                }
                if (lsqrConlimField != null && !lsqrConlimField.getText().trim().isEmpty()) {
                    try {
                        double conlim = Double.parseDouble(lsqrConlimField.getText().trim());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver).put("lsqrConlim", conlim);
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid LSQR CONLIM value: " + lsqrConlimField.getText());
                    }
                }
                if (lsqrIterLimField != null && !lsqrIterLimField.getText().trim().isEmpty()) {
                    try {
                        int iterLim = Integer.parseInt(lsqrIterLimField.getText().trim());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver).put("lsqrIterLim", iterLim);
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid LSQR Iteration Limit value: " + lsqrIterLimField.getText());
                    }
                }
                if (lsqrShowLogCheckBox != null) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver).put("lsqrShowLog", lsqrShowLogCheckBox.isSelected());
                }
                if (lsqrCalcVarCheckBox != null) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) trdSolver).put("lsqrCalcVar", lsqrCalcVarCheckBox.isSelected());
                }
            }
        } else if ("MCMC".equals(selectedMode)) {
            var mcmcMode = cfg.modes.computeIfAbsent("MCMC", k -> new AppConfig.ModeConfig());
            if (selectedTargetDir != null) mcmcMode.datDirectory = selectedTargetDir.toPath();
            if (selectedOutputDir != null) mcmcMode.outDirectory = selectedOutputDir.toPath();
            var mcmcSolver = cfg.solver.computeIfAbsent("MCMC", k -> 
                new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
            if (mcmcSolver instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                String nSamplesStr = getParameterValue("nSamples");
                String burnInStr = getParameterValue("burnIn");
                String stepSizeStr = getParameterValue("stepSize");
                String stepSizeDepthStr = getParameterValue("stepSizeDepth");
                String temperatureStr = getParameterValue("temperature");
                if (!nSamplesStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) mcmcSolver)
                        .put("nSamples", Integer.parseInt(nSamplesStr));
                }
                if (!burnInStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) mcmcSolver)
                        .put("burnIn", Integer.parseInt(burnInStr));
                }
                if (!stepSizeStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) mcmcSolver)
                        .put("stepSize", Double.parseDouble(stepSizeStr));
                }
                if (!stepSizeDepthStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) mcmcSolver)
                        .put("stepSizeDepth", Double.parseDouble(stepSizeDepthStr));
                }
                if (!temperatureStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) mcmcSolver)
                        .put("temperature", Double.parseDouble(temperatureStr));
                }
                String showConvergenceLogStr = getParameterValue("showConvergenceLog");
                boolean showConvergenceLog = false;
                if (!showConvergenceLogStr.isEmpty()) {
                    showConvergenceLog = "true".equalsIgnoreCase(showConvergenceLogStr.trim()) || "1".equals(showConvergenceLogStr.trim());
                } else if (showConvergenceLogCheckBox != null) {
                    showConvergenceLog = showConvergenceLogCheckBox.isSelected();
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) mcmcSolver).put("showConvergenceLog", showConvergenceLog);
                if (showConvergenceLogCheckBox != null) {
                    showConvergenceLogCheckBox.setSelected(showConvergenceLog);
                }
            }
        } else if ("DE".equals(selectedMode)) {
            var deMode = cfg.modes.computeIfAbsent("DE", k -> new AppConfig.ModeConfig());
            if (selectedTargetDir != null) deMode.datDirectory = selectedTargetDir.toPath();
            if (selectedOutputDir != null) deMode.outDirectory = selectedOutputDir.toPath();
            var deSolver = cfg.solver.computeIfAbsent("DE", k ->
                new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
            if (deSolver instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                String populationSizeStr = dePopulationSizeField != null ? dePopulationSizeField.getText().trim() : "";
                String maxGenerationsStr = deMaxGenerationsField != null ? deMaxGenerationsField.getText().trim() : "";
                String scalingFactorStr = deScalingFactorField != null ? deScalingFactorField.getText().trim() : "";
                String crossoverRateStr = deCrossoverRateField != null ? deCrossoverRateField.getText().trim() : "";
                if (!populationSizeStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) deSolver)
                        .put("populationSize", Integer.parseInt(populationSizeStr));
                }
                if (!maxGenerationsStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) deSolver)
                        .put("maxGenerations", Integer.parseInt(maxGenerationsStr));
                }
                if (!scalingFactorStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) deSolver)
                        .put("scalingFactor", Double.parseDouble(scalingFactorStr));
                }
                if (!crossoverRateStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) deSolver)
                        .put("crossoverRate", Double.parseDouble(crossoverRateStr));
                }
                String showConvergenceLogStr = getParameterValue("showConvergenceLog");
                boolean showConvergenceLog = false;
                if (!showConvergenceLogStr.isEmpty()) {
                    showConvergenceLog = "true".equalsIgnoreCase(showConvergenceLogStr.trim()) || "1".equals(showConvergenceLogStr.trim());
                } else if (showConvergenceLogCheckBox != null) {
                    showConvergenceLog = showConvergenceLogCheckBox.isSelected();
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) deSolver).put("showConvergenceLog", showConvergenceLog);
                if (showConvergenceLogCheckBox != null) {
                    showConvergenceLogCheckBox.setSelected(showConvergenceLog);
                }
            }
        }
        
        return cfg;
    }
    
    private void executeLocation() {
        config = buildConfigFromUI();
        String selectedMode = getSelectedModeAbbreviation();
        
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
            JOptionPane.showMessageDialog(this,
                "Please select an output directory.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (!selectedOutputDir.exists()) {
            String errorMsg = String.format(
                "Output directory does not exist: %s\n" +
                "  Please create the directory before running the task.",
                selectedOutputDir.getAbsolutePath());
            appendLog("ERROR: " + errorMsg);
            JOptionPane.showMessageDialog(this,
                errorMsg,
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!selectedOutputDir.isDirectory()) {
            String errorMsg = String.format(
                "Output path is not a directory: %s\n" +
                "  Please select a valid directory.",
                selectedOutputDir.getAbsolutePath());
            appendLog("ERROR: " + errorMsg);
            JOptionPane.showMessageDialog(this,
                errorMsg,
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        File outputDir = selectedOutputDir;
        
        // Validate target directory exists (for modes that require it)
        final String currentMode = getSelectedModeAbbreviation();
        if (selectedTargetDir != null) {
            if (!selectedTargetDir.exists()) {
                String errorMsg = String.format(
                    "Target directory does not exist: %s\n" +
                    "  Please select a valid target directory.",
                    selectedTargetDir.getAbsolutePath());
                appendLog("ERROR: " + errorMsg);
                JOptionPane.showMessageDialog(this,
                    errorMsg,
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!selectedTargetDir.isDirectory()) {
                String errorMsg = String.format(
                    "Target path is not a directory: %s\n" +
                    "  Please select a valid directory.",
                    selectedTargetDir.getAbsolutePath());
                appendLog("ERROR: " + errorMsg);
                JOptionPane.showMessageDialog(this,
                    errorMsg,
                    "Error", JOptionPane.ERROR_MESSAGE);
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
        
        // currentMode is already set above
        if ("TRD".equals(currentMode)) {
            File catalogFile = getCatalogFileFromField();
            if (catalogFile == null || !catalogFile.exists()) {
                appendLog("Error: Catalog file is required for TRD mode.");
                JOptionPane.showMessageDialog(this,
                    "Catalog file is required for TRD mode.\nPlease select a catalog file.",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (selectedTargetDir == null) {
                appendLog("Error: Target directory (dat files) is required for TRD mode.");
                JOptionPane.showMessageDialog(this,
                    "Target directory (dat files) is required for TRD mode.\nPlease select a target directory.",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            appendLog("Catalog file: " + catalogFile.getAbsolutePath());
            appendLog("Target directory: " + selectedTargetDir.getAbsolutePath());
        } else {
            List<File> datFiles = findDatFiles(selectedTargetDir);
            if (datFiles.isEmpty()) {
                appendLog("Error: No .dat files found in the selected directory.");
                JOptionPane.showMessageDialog(this,
                    "No .dat files found in the selected directory.",
                    "Information", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            appendLog("Number of .dat files found: " + datFiles.size());
        }
        
        currentWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                executeButton.setEnabled(false);
                cancelButton.setEnabled(true);
                publish("Execution started...");
                
                if ("TRD".equals(currentMode)) {
                    try {
                        var trdMode = config.modes.get("TRD");
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
                        java.io.StringWriter sw = new java.io.StringWriter();
                        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                        e.printStackTrace(pw);
                        logger.severe("Stack trace:\n" + sw.toString());
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
                
                ExecutorService executor = (numJobs > 1) 
                    ? Executors.newFixedThreadPool(numJobs)
                    : null;
                
                List<File> datFiles = findDatFiles(selectedTargetDir);
                
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
                                    publish("Processing: " + finalDatFile.getName() + " (" + current + "/" + datFiles.size() + ")");
                                    
                                    String mode = getSelectedModeAbbreviation();
                                    String eventName = finalDatFile.getName();
                                    currentEventName = eventName;
                                    
                                    if (residualPlotPanel != null) {
                                        SwingUtilities.invokeLater(() -> {
                                            residualPlotPanel.startProcessingEvent(eventName);
                                        });
                                    }
                                    
                                    if ("GRD".equals(mode)) {
                                        HypoGridSearch solver = new HypoGridSearch(config);
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
                                        publish("Success: " + finalDatFile.getName() + " - " + hypocenters.size() + " hypocenter data");
                                    } catch (Exception e) {
                                        publish("Warning: Failed to load results from " + finalDatFile.getName() + ": " + e.getMessage());
                                        logger.warning("Result loading error: " + finalDatFile.getName() + " - " + e.getMessage());
                                    }
                                    
                                } catch (Exception e) {
                                    if (isCancelled() || Thread.currentThread().isInterrupted() || 
                                        (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                                        return null;
                                    }
                                    errorCount.incrementAndGet();
                                    publish("Error: Skipping processing of " + finalDatFile.getName() + ": " + e.getMessage());
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
                                        logger.warning("Task execution error: " + e.getMessage());
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
                                publish("Processing: " + datFile.getName() + " (" + current + "/" + datFiles.size() + ")");
                                
                                String mode = getSelectedModeAbbreviation();
                                String eventName = datFile.getName();
                                currentEventName = eventName;
                                
                                if (residualPlotPanel != null) {
                                    SwingUtilities.invokeLater(() -> {
                                        residualPlotPanel.setActiveEvent(eventName);
                                    });
                                }
                                
                                if ("GRD".equals(mode)) {
                                    HypoGridSearch solver = new HypoGridSearch(config);
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
                                
                                // Mark event as completed
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
                                    publish("Success: " + datFile.getName() + " - " + hypocenters.size() + " hypocenter data");
                                } catch (Exception e) {
                                    publish("Warning: Failed to load results from " + datFile.getName() + ": " + e.getMessage());
                                    logger.warning("Result loading error: " + datFile.getName() + " - " + e.getMessage());
                                }
                                
                            } catch (Exception e) {
                                if (isCancelled() || Thread.currentThread().isInterrupted() || 
                                    (e.getMessage() != null && e.getMessage().contains("interrupted"))) {
                                    break;
                                }
                                errorCount.incrementAndGet();
                                publish("Error: Skipping processing of " + datFile.getName() + ": " + e.getMessage());
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
                        File inputCatalogFile = getCatalogFileFromField();
                        String mode = getSelectedModeAbbreviation();
                        File catalogFile = com.treloc.xtreloc.util.CatalogFileNameGenerator.generateCatalogFileName(
                            inputCatalogFile != null ? inputCatalogFile.getAbsolutePath() : null,
                            mode, outputDir);
                        try {
                            com.treloc.xtreloc.app.gui.service.CsvExporter.exportHypocenters(allHypocenters, catalogFile);
                            publish("Exported in catalog format: " + catalogFile.getAbsolutePath() + " (" + allHypocenters.size() + " entries)");
                        } catch (Exception e) {
                            publish("Warning: Failed to export in catalog format: " + e.getMessage());
                            logger.warning("Catalog export error: " + e.getMessage());
                        }
                    } else {
                        publish("Warning: No processed hypocenter data");
                    }
                    
                    if (!allHypocenters.isEmpty() && mapView != null) {
                        SwingUtilities.invokeLater(() -> {
                            try {
                                mapView.showHypocenters(allHypocenters);
                            } catch (Exception e) {
                                logger.warning("Map display error: " + e.getMessage());
                            }
                        });
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
                    // Print stack trace to logger
                    java.io.StringWriter sw = new java.io.StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                    e.printStackTrace(pw);
                    logger.severe("Stack trace:\n" + sw.toString());
                } finally {
                    if (executor != null) {
                        // If cancelled, force shutdown immediately
                        if (isCancelled()) {
                            executor.shutdownNow();
                            try {
                                // Wait a short time for threads to terminate
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
                    executeButton.setEnabled(true);
                    cancelButton.setEnabled(false);
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
                    } catch (java.util.concurrent.CancellationException e) {
                        appendLog("Cancelled successfully");
                    } catch (Exception e) {
                        // Detailed error message for GUI log panel
                        StringBuilder errorMsg = new StringBuilder("Execution error:\n");
                        errorMsg.append("  Error type: ").append(e.getClass().getSimpleName()).append("\n");
                        errorMsg.append("  Error message: ").append(e.getMessage()).append("\n");
                        if (e.getCause() != null) {
                            errorMsg.append("  Caused by: ").append(e.getCause().getClass().getSimpleName())
                                   .append(": ").append(e.getCause().getMessage()).append("\n");
                        }
                        appendLog(errorMsg.toString());
                        JOptionPane.showMessageDialog(HypocenterLocationPanel.this,
                            "Error: " + e.getMessage() + 
                            (e.getCause() != null ? "\nCaused by: " + e.getCause().getMessage() : ""),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        executeButton.setEnabled(true);
                        cancelButton.setEnabled(false);
                    }
                });
            }
        };
        
        currentWorker.execute();
    }
    
    /**
     * Execute SYN mode (synthetic test data generation).
     */
    private void executeSyntheticTest() {
        if (config == null) {
            config = new AppConfig();
        }
        
        File catalogFile = getCatalogFileFromField();
        if (catalogFile == null || !catalogFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Please select a catalog file",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (selectedOutputDir == null) {
            JOptionPane.showMessageDialog(this,
                "Please select an output directory",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (!selectedOutputDir.exists()) {
            String errorMsg = String.format(
                "Output directory does not exist: %s\n" +
                "  Please create the directory before running SYN mode.",
                selectedOutputDir.getAbsolutePath());
            appendLog("ERROR: " + errorMsg);
            JOptionPane.showMessageDialog(this,
                errorMsg,
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!selectedOutputDir.isDirectory()) {
            String errorMsg = String.format(
                "Output path is not a directory: %s\n" +
                "  Please select a valid directory.",
                selectedOutputDir.getAbsolutePath());
            appendLog("ERROR: " + errorMsg);
            JOptionPane.showMessageDialog(this,
                errorMsg,
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (selectedStationFile == null || !selectedStationFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Please select a station file",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String selectedModel = (String) taupModelCombo.getSelectedItem();
        config.taupFile = selectedModel;
        config.stationFile = selectedStationFile.getAbsolutePath();
        
        if (config.modes == null) {
            config.modes = new java.util.HashMap<>();
        }
        AppConfig.ModeConfig synConfig = config.modes.get("SYN");
        if (synConfig == null) {
            synConfig = new AppConfig.ModeConfig();
            config.modes.put("SYN", synConfig);
        }
        synConfig.catalogFile = catalogFile.getAbsolutePath();
        synConfig.outDirectory = selectedOutputDir.toPath();
        
        executeButton.setEnabled(false);
        cancelButton.setEnabled(true);
        appendLog("Starting synthetic test data generation...");
        
        currentWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    if (config.taupFile == null || config.taupFile.isEmpty()) {
                        throw new IllegalArgumentException("Velocity model file (taupFile) is not set");
                    }
                    if (config.stationFile == null || config.stationFile.isEmpty()) {
                        throw new IllegalArgumentException("Station file (stationFile) is not set");
                    }
                    
                    publish("Catalog file: " + catalogFile.getAbsolutePath());
                    publish("Output directory: " + selectedOutputDir.getAbsolutePath());
                    String randomSeedStr = getParameterValue("randomSeed");
                    String phsErrStr = getParameterValue("phsErr");
                    String locErrStr = getParameterValue("locErr");
                    String minSelectRateStr = getParameterValue("minSelectRate");
                    String maxSelectRateStr = getParameterValue("maxSelectRate");
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
                    
                    // Auto-generate catalog after SYN data creation
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
                    logger.severe("Synthetic test data generation error: " + e.getMessage());
                    publish("Error: " + e.getMessage());
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
                    } catch (java.util.concurrent.CancellationException e) {
                        appendLog("Cancelled successfully");
                    } catch (Exception e) {
                        // Error should already be logged via publish() in doInBackground
                        StringBuilder errorMsg = new StringBuilder("Error: " + e.getMessage());
                        if (e.getCause() != null) {
                            errorMsg.append("\nCaused by: ").append(e.getCause().getClass().getSimpleName())
                                   .append(": ").append(e.getCause().getMessage());
                        }
                        JOptionPane.showMessageDialog(HypocenterLocationPanel.this,
                            errorMsg.toString(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        executeButton.setEnabled(true);
                        cancelButton.setEnabled(false);
                    }
                });
            }
        };
        
        currentWorker.execute();
    }
    
    /**
     * Execute CLS mode (clustering).
     */
    private void executeClustering() {
        if (config == null) {
            config = new AppConfig();
        }
        
        File catalogFile = getCatalogFileFromField();
        if (catalogFile == null || !catalogFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Please select a catalog file",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (selectedOutputDir == null) {
            JOptionPane.showMessageDialog(this,
                "Please select an output directory",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (selectedStationFile == null || !selectedStationFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Please select a station file",
                "Error", JOptionPane.ERROR_MESSAGE);
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
        
        if (config.modes == null) {
            config.modes = new java.util.HashMap<>();
        }
        AppConfig.ModeConfig clsConfig = config.modes.get("CLS");
        if (clsConfig == null) {
            clsConfig = new AppConfig.ModeConfig();
            config.modes.put("CLS", clsConfig);
        }
        clsConfig.catalogFile = catalogFile.getAbsolutePath();
        clsConfig.outDirectory = selectedOutputDir.toPath();
        if (selectedTargetDir != null) {
            clsConfig.datDirectory = selectedTargetDir.toPath();
        }
        String minPtsStr = getParameterValue("minPts");
        String epsStr = getParameterValue("eps");
        String epsPercentileStr = getParameterValue("epsPercentile");
        String rmsThresholdStr = getParameterValue("rmsThreshold");
        String locErrThresholdStr = getParameterValue("locErrThreshold");
        if (!minPtsStr.isEmpty()) {
            clsConfig.minPts = Integer.parseInt(minPtsStr);
        }
        if (!epsStr.isEmpty()) {
            clsConfig.eps = Double.parseDouble(epsStr);
        }
        if (!epsPercentileStr.isEmpty()) {
            try {
                double percentile = Double.parseDouble(epsPercentileStr);
                if (percentile > 0 && percentile <= 1) {
                    clsConfig.epsPercentile = percentile;
                }
            } catch (NumberFormatException e) {
                // Ignore invalid values
            }
        }
        if (!rmsThresholdStr.isEmpty()) {
            try {
                clsConfig.rmsThreshold = Double.parseDouble(rmsThresholdStr);
            } catch (NumberFormatException e) {
                // Ignore invalid values
            }
        }
        if (!locErrThresholdStr.isEmpty()) {
            try {
                clsConfig.locErrThreshold = Double.parseDouble(locErrThresholdStr);
            } catch (NumberFormatException e) {
                // Ignore invalid values
            }
        }
        clsConfig.useBinaryFormat = true;
        
        final int finalMinPts = clsConfig.minPts;
        final double finalEps = clsConfig.eps;
        
        executeButton.setEnabled(false);
        cancelButton.setEnabled(true);
        appendLog("Starting clustering process...");
        
        currentWorker = new SwingWorker<Void, String>() {
            private com.treloc.xtreloc.solver.SpatialClustering clustering;
            
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    publish("Catalog file: " + catalogFile.getAbsolutePath());
                    publish("Output directory: " + selectedOutputDir.getAbsolutePath());
                    publish("Parameters: minPts=" + finalMinPts + ", eps=" + finalEps);
                    
                    clustering = new com.treloc.xtreloc.solver.SpatialClustering(config);
                    clustering.start("", "");
                    
                    publish("Clustering process completed");
                    publish("Output directory: " + selectedOutputDir.getAbsolutePath());
                    publish("Catalog file with cluster IDs: " + new File(selectedOutputDir, "catalog.csv").getAbsolutePath());
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
                    StringBuilder errorMsg = new StringBuilder("Clustering process error:\n");
                    errorMsg.append("  Error type: ").append(e.getClass().getName()).append("\n");
                    errorMsg.append("  Error message: ").append(e.getMessage()).append("\n");
                    if (e.getCause() != null) {
                        errorMsg.append("  Caused by: ").append(e.getCause().getClass().getName())
                               .append(": ").append(e.getCause().getMessage()).append("\n");
                    }
                    String errorStr = errorMsg.toString();
                    logger.severe(errorStr);
                    publish(errorStr);
                    // Print stack trace to logger
                    java.io.StringWriter sw = new java.io.StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                    e.printStackTrace(pw);
                    logger.severe("Stack trace:\n" + sw.toString());
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
                    } catch (java.util.concurrent.CancellationException e) {
                        appendLog("Cancelled successfully");
                    } catch (Exception e) {
                        // Error should already be logged via publish() in doInBackground
                        StringBuilder errorMsg = new StringBuilder("Error: " + e.getMessage());
                        if (e.getCause() != null) {
                            errorMsg.append("\nCaused by: ").append(e.getCause().getClass().getSimpleName())
                                   .append(": ").append(e.getCause().getMessage());
                        }
                        JOptionPane.showMessageDialog(HypocenterLocationPanel.this,
                            errorMsg.toString(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        executeButton.setEnabled(true);
                        cancelButton.setEnabled(false);
                    }
                });
            }
        };
        
        currentWorker.execute();
    }
    
    /**
     * K-distanceグラフを表示（Convergence Logタブに表示）
     */
    private void showKDistanceGraph(List<Double> kDistances, double estimatedEps) {
        if (residualPlotPanel != null) {
            residualPlotPanel.showKDistanceGraph(kDistances, estimatedEps);
        }
    }
    
    /**
     * 実行を中断
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
     * Exports current solver settings to a JSON file.
     */
    private void exportConfigToJson() {
        try {
            AppConfig cfg = buildConfigFromUI();
            
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export Configuration (JSON)");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files (*.json)", "json"));
            chooser.setSelectedFile(new File("config.json"));
            
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            
            File file = chooser.getSelectedFile();
            if (file == null) return;
            String path = file.getAbsolutePath();
            if (!path.endsWith(".json")) {
                path = path + ".json";
                file = new File(path);
            }
            
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();
            module.addSerializer(Path.class, new PathSerializer());
            mapper.registerModule(module);
            
            mapper.writeValue(file, cfg);
            appendLog("Configuration exported to: " + path);
            JOptionPane.showMessageDialog(this,
                "Configuration exported successfully to:\n" + path,
                "Export (Json)", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            logger.warning("Failed to export config: " + e.getMessage());
            appendLog("ERROR: Failed to export configuration: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to export configuration:\n" + e.getMessage(),
                "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Loads hypocenter data from a dat file.
     * 
     * <p>The dat file format consists of two lines:
     * <ul>
     *   <li>Line 1: latitude longitude depth [type]</li>
     *   <li>Line 2: xerr (km) yerr (km) zerr (km) rms residual</li>
     * </ul>
     * 
     * @param datFile the dat file to load
     * @return list of hypocenters loaded from the file
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
    
    public void setConfig(AppConfig config) {
        this.config = config;
        if (config != null) {
            // Common parameters
            thresholdField.setText(String.valueOf(config.threshold));
            hypBottomField.setText(String.valueOf(config.hypBottom));
            
            if (config.taupFile != null && !config.taupFile.isEmpty()) {
                if (taupModelCombo != null) {
                    taupModelCombo.setSelectedItem(config.taupFile);
                }
                com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setTaupFile(config.taupFile);
            }
            
            // Mode-specific parameters
            if (config.solver != null) {
                if (config.solver.containsKey("GRD")) {
                    var grdSolver = config.solver.get("GRD");
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
                if (config.solver.containsKey("LMO")) {
                    var lmoSolver = config.solver.get("LMO");
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
                if (config.solver.containsKey("MCMC")) {
                    var mcmcSolver = config.solver.get("MCMC");
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
                if (config.solver.containsKey("DE")) {
                    var deSolver = config.solver.get("DE");
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
                if (config.solver.containsKey("TRD")) {
                    var trdSolver = config.solver.get("TRD");
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
        }
    }
    
    /**
     * Show parameter help dialog for the selected mode
     */
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
        textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
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
                    "- threshold: Weight threshold for filtering data.\n" +
                    "  Default: 0.0 (no filtering).\n\n" +
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

