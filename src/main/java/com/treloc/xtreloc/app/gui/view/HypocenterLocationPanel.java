package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.solver.HypoGridSearch;
import com.treloc.xtreloc.solver.HypoStationPairDiff;
import com.treloc.xtreloc.solver.SyntheticTest;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.DefaultComboBoxModel;
import java.awt.*;
import java.io.File;
import java.util.List;
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
    private JTextArea logArea;
    private JPanel logPanel;
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
    
    // LM optimization parameters for STD mode
    private JTextField lmInitialStepBoundField;
    private JTextField lmCostRelativeToleranceField;
    private JTextField lmParRelativeToleranceField;
    private JTextField lmOrthoToleranceField;
    private JTextField lmMaxEvaluationsField;
    private JTextField lmMaxIterationsField;
    
    private JPanel parameterPanel;
    private JTable parameterTable;
    private javax.swing.table.DefaultTableModel parameterTableModel;
    
    private JPanel leftPanel;
    private JPanel inputDataPanel;
    
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
        
        JPanel modePanel = createModePanel();
        modePanel.setPreferredSize(new Dimension(PANEL_WIDTH, 70));
        modePanel.setMaximumSize(new Dimension(PANEL_WIDTH, 70));
        leftPanel.add(modePanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        inputDataPanel = createInputDataPanel();
        inputDataPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 200));
        inputDataPanel.setMaximumSize(new Dimension(PANEL_WIDTH, 200));
        leftPanel.add(inputDataPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        JPanel paramPanel = createParameterPanel();
        paramPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 200));
        paramPanel.setMaximumSize(new Dimension(PANEL_WIDTH, 200));
        leftPanel.add(paramPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        JPanel outputPanel = createOutputPanel();
        outputPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 120));
        outputPanel.setMaximumSize(new Dimension(PANEL_WIDTH, 120));
        leftPanel.add(outputPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        JPanel buttonPanel = createButtonPanel();
        leftPanel.add(buttonPanel);
        
        JScrollPane scrollPane = new JScrollPane(leftPanel);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        logPanel = createLogPanel();
        
        // Load and display log history on startup
        loadLogHistory();
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, logPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(400);
        
        add(splitPane, BorderLayout.CENTER);
        
        updateParameterFields();
        updateExecuteButtonState();
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
    
    
    private void updateLayoutForMode(String mode) {
        if (inputDataPanel != null) {
            updateInputDataPanelForMode(mode);
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
        modeCombo = new JComboBox<>(new String[]{"GRD", "STD", "TRD", "MCMC", "CLS", "SYN"});
        modeCombo.setSelectedItem("GRD");
        modeCombo.addActionListener(e -> {
            String mode = (String) modeCombo.getSelectedItem();
            updateParameterFields();
            updateLayoutForMode(mode);
        });
        panel.add(modeCombo, gbc);
        
        return panel;
    }
    
    private JPanel createParameterPanel() {
        parameterPanel = new JPanel(new BorderLayout());
        Color bgColor = UIManager.getColor("Panel.background");
        parameterPanel.setBackground(bgColor != null ? bgColor : Color.WHITE);
        parameterPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 200), 1),
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                "📊 Parameters",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13),
                new Color(80, 80, 60)
            )
        ));
        
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
                updateExecuteButtonState();
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(parameterTable);
        scrollPane.setPreferredSize(new Dimension(450, 200));
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
        
        minPtsField = new JTextField("4", 10);
        epsField = new JTextField("-1.0", 10);
        epsPercentileField = new JTextField("", 10);
        rmsThresholdField = new JTextField("", 10);
        locErrThresholdField = new JTextField("", 10);
        
        trdIterNumField = new JTextField("10,10", 10);
        trdDistKmField = new JTextField("50,20", 10);
        trdDampFactField = new JTextField("0,1", 10);
        
        // LM optimization parameters for STD mode
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
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(500, 150));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
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
        
        // Set the current text field path as the initial directory
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
            "TauP model files (*.taup, *.tvel)", "taup", "tvel"));
        fileChooser.setAcceptAllFileFilterUsed(true);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedTaupFile = fileChooser.getSelectedFile();
            String filePath = selectedTaupFile.getAbsolutePath();
            
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) taupModelCombo.getModel();
            model.removeElement("Select from file...");
            model.addElement(filePath);
            taupModelCombo.setSelectedItem(filePath);
            
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setTaupFile(filePath);
            appendLog("TauP model file selected: " + filePath);
        } else {
            taupModelCombo.setSelectedItem("prem");
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
        
        String selectedMode = (modeCombo != null) ? (String) modeCombo.getSelectedItem() : "GRD";
        if (selectedMode == null) {
            selectedMode = "GRD";
        }
        
        if (!"SYN".equals(selectedMode) && !"CLS".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Parallelization (numJobs)", numJobsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Weight Threshold (threshold)", thresholdField.getText(), "weight (empty or 0.0 for no filtering)"});
            parameterTableModel.addRow(new Object[]{"Maximum Depth (hypBottom)", hypBottomField.getText(), "km"});
        }
        
        if ("GRD".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Total Grids (totalGrids)", totalGridsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Focus Count (numFocus)", numFocusField.getText(), ""});
        } else if ("STD".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"LM Initial Step Bound (initialStepBoundFactor)", lmInitialStepBoundField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Cost Relative Tolerance (costRelativeTolerance)", lmCostRelativeToleranceField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Parameter Relative Tolerance (parRelativeTolerance)", lmParRelativeToleranceField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Orthogonal Tolerance (orthoTolerance)", lmOrthoToleranceField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Max Evaluations (maxEvaluations)", lmMaxEvaluationsField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"LM Max Iterations (maxIterations)", lmMaxIterationsField.getText(), ""});
        } else if ("TRD".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Iteration Count (iterNum)", trdIterNumField.getText(), "Comma-separated (e.g., 10,10)"});
            parameterTableModel.addRow(new Object[]{"Distance Threshold (distKm)", trdDistKmField.getText(), "km, comma-separated (e.g., 50,20)"});
            parameterTableModel.addRow(new Object[]{"Damping Factor (dampFact)", trdDampFactField.getText(), "Comma-separated (e.g., 0,1)"});
        } else if ("MCMC".equals(selectedMode)) {
            parameterTableModel.addRow(new Object[]{"Sample Count (nSamples)", nSamplesField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Burn-in Period (burnIn)", burnInField.getText(), ""});
            parameterTableModel.addRow(new Object[]{"Step Size (stepSize)", stepSizeField.getText(), "degree"});
            parameterTableModel.addRow(new Object[]{"Depth Step Size (stepSizeDepth)", stepSizeDepthField.getText(), "km"});
            parameterTableModel.addRow(new Object[]{"Temperature Parameter (temperature)", temperatureField.getText(), ""});
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
    
    private void updateExecuteButtonState() {
        if (executeButton == null) {
            return;
        }
        
        String selectedMode = (modeCombo != null && modeCombo.getSelectedItem() != null) 
            ? (String) modeCombo.getSelectedItem() 
            : "GRD";
        
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
                
                // If not available from parameter table, get directly from field
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
            } else if ("STD".equals(selectedMode)) {
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
            }
        }
        
        executeButton.setEnabled(canExecute);
        
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
    
    private void executeLocation() {
        if (config == null) {
            config = new AppConfig();
        }
        
        String selectedModel = (String) taupModelCombo.getSelectedItem();
        if (selectedModel != null && !"Select from file...".equals(selectedModel)) {
            config.taupFile = selectedModel;
        }
        config.stationFile = selectedStationFile.getAbsolutePath();
        
        String numJobsStr = getParameterValue("numJobs");
        String thresholdStr = getParameterValue("threshold");
        String hypBottomStr = getParameterValue("hypBottom");
        if (!numJobsStr.isEmpty()) {
            config.numJobs = Integer.parseInt(numJobsStr);
        }
        if (!thresholdStr.isEmpty()) {
            config.threshold = Double.parseDouble(thresholdStr);
        }
        if (!hypBottomStr.isEmpty()) {
            config.hypBottom = Double.parseDouble(hypBottomStr);
        }
        
        String selectedMode = (String) modeCombo.getSelectedItem();
        
        if ("SYN".equals(selectedMode)) {
            executeSyntheticTest();
            return;
        }
        
        if ("CLS".equals(selectedMode)) {
            executeClustering();
            return;
        }
        
        if (config.solver == null) {
            config.solver = new java.util.HashMap<>();
        }
        
        if (config.modes == null) {
            config.modes = new java.util.HashMap<>();
        }
        
        if ("GRD".equals(selectedMode)) {
            var grdSolver = config.solver.computeIfAbsent("GRD", k -> 
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
        } else if ("STD".equals(selectedMode)) {
            var stdSolver = config.solver.computeIfAbsent("STD", k -> 
                new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
            if (stdSolver instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                String initialStepBoundStr = getParameterValue("initialStepBoundFactor");
                String costRelativeTolStr = getParameterValue("costRelativeTolerance");
                String parRelativeTolStr = getParameterValue("parRelativeTolerance");
                String orthoTolStr = getParameterValue("orthoTolerance");
                String maxEvalStr = getParameterValue("maxEvaluations");
                String maxIterStr = getParameterValue("maxIterations");
                
                // If not available from parameter table, get directly from field
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
                    ((com.fasterxml.jackson.databind.node.ObjectNode) stdSolver)
                        .put("initialStepBoundFactor", Double.parseDouble(initialStepBoundStr));
                }
                if (!costRelativeTolStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) stdSolver)
                        .put("costRelativeTolerance", Double.parseDouble(costRelativeTolStr));
                }
                if (!parRelativeTolStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) stdSolver)
                        .put("parRelativeTolerance", Double.parseDouble(parRelativeTolStr));
                }
                if (!orthoTolStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) stdSolver)
                        .put("orthoTolerance", Double.parseDouble(orthoTolStr));
                }
                if (!maxEvalStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) stdSolver)
                        .put("maxEvaluations", Integer.parseInt(maxEvalStr));
                }
                if (!maxIterStr.isEmpty()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) stdSolver)
                        .put("maxIterations", Integer.parseInt(maxIterStr));
                }
            }
        } else if ("TRD".equals(selectedMode)) {
            var trdMode = config.modes.computeIfAbsent("TRD", k -> new AppConfig.ModeConfig());
            
            File catalogFile = getCatalogFileFromField();
            if (catalogFile != null && catalogFile.exists()) {
                trdMode.catalogFile = catalogFile.getAbsolutePath();
            }
            
            if (selectedTargetDir != null) {
                trdMode.datDirectory = selectedTargetDir.toPath();
            }
            
            // Set output directory
            if (selectedOutputDir != null) {
                trdMode.outDirectory = selectedOutputDir.toPath();
            }
            
            var trdSolver = config.solver.computeIfAbsent("TRD", k -> 
                new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
            if (trdSolver instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                String iterNumStr = getParameterValue("iterNum");
                String distKmStr = getParameterValue("distKm");
                String dampFactStr = getParameterValue("dampFact");
                
                // If not available from parameter table, get directly from field
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
            }
        } else if ("MCMC".equals(selectedMode)) {
            var trdSolver = config.solver.computeIfAbsent("TRD", k -> 
                new com.fasterxml.jackson.databind.node.ObjectNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
            if (trdSolver instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                String iterNumStr = getParameterValue("iterNum");
                String distKmStr = getParameterValue("distKm");
                String dampFactStr = getParameterValue("dampFact");
                
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
            }
        } else if ("MCMC".equals(selectedMode)) {
            var mcmcSolver = config.solver.computeIfAbsent("MCMC", k -> 
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
            }
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
        final String currentMode = (String) modeCombo.getSelectedItem();
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
                        
                        // 中断チェック
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
                            // Check if this is an interruption
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
                        // Detailed error reporting for GUI
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
                        // Print stack trace to logger
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
                                if (isCancelled()) {
                                    return null;
                                }
                                String inputPath = finalDatFile.getAbsolutePath();
                                String outputPath = new File(outputDir, finalDatFile.getName()).getAbsolutePath();
                                
                                try {
                                    int current = processedCount.get() + errorCount.get() + 1;
                                    publish("Processing: " + finalDatFile.getName() + " (" + current + "/" + datFiles.size() + ")");
                                    
                                    String mode = (String) modeCombo.getSelectedItem();
                                    if ("GRD".equals(mode)) {
                                        HypoGridSearch solver = new HypoGridSearch(config);
                                        solver.start(inputPath, outputPath);
                                    } else if ("STD".equals(mode)) {
                                        HypoStationPairDiff solver = new HypoStationPairDiff(config);
                                        solver.start(inputPath, outputPath);
                                    } else if ("MCMC".equals(mode)) {
                                        com.treloc.xtreloc.solver.HypoMCMC solver = new com.treloc.xtreloc.solver.HypoMCMC(config);
                                        solver.start(inputPath, outputPath);
                                    } else if ("TRD".equals(mode)) {
                                        com.treloc.xtreloc.solver.HypoTripleDiff solver = 
                                            new com.treloc.xtreloc.solver.HypoTripleDiff(config);
                                        solver.start(inputPath, outputPath);
                                    } else {
                                        throw new IllegalArgumentException("Unknown mode: " + mode);
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
                                    errorCount.incrementAndGet();
                                    publish("Error: Skipping processing of " + finalDatFile.getName() + ": " + e.getMessage());
                                    logger.warning("File processing error: " + finalDatFile.getName() + " - " + e.getMessage());
                                }
                                return null;
                            });
                            futures.add(future);
                        }
                        
                        for (Future<Void> future : futures) {
                            if (isCancelled()) {
                                future.cancel(true);
                            } else {
                                try {
                                    future.get();
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
                                
                                String mode = (String) modeCombo.getSelectedItem();
                                if ("GRD".equals(mode)) {
                                    HypoGridSearch solver = new HypoGridSearch(config);
                                    solver.start(inputPath, outputPath);
                                } else if ("STD".equals(mode)) {
                                    HypoStationPairDiff solver = new HypoStationPairDiff(config);
                                    solver.start(inputPath, outputPath);
                                } else if ("TRD".equals(mode)) {
                                    com.treloc.xtreloc.solver.HypoTripleDiff solver = 
                                        new com.treloc.xtreloc.solver.HypoTripleDiff(config);
                                    solver.start(inputPath, outputPath);
                                } else {
                                    throw new IllegalArgumentException("Unknown mode: " + mode);
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
                        String mode = (String) modeCombo.getSelectedItem();
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
                    // Detailed error reporting for GUI
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
                        executor.shutdown();
                        try {
                            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                                executor.shutdownNow();
                            }
                        } catch (InterruptedException e) {
                            executor.shutdownNow();
                            Thread.currentThread().interrupt();
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
                    executeButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    try {
                        get();
                    } catch (java.util.concurrent.CancellationException e) {
                        appendLog("Processing was cancelled");
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
                        // Show dialog with simplified message
                        JOptionPane.showMessageDialog(HypocenterLocationPanel.this,
                            "Error: " + e.getMessage() + 
                            (e.getCause() != null ? "\nCaused by: " + e.getCause().getMessage() : ""),
                            "Error", JOptionPane.ERROR_MESSAGE);
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
                executeButton.setEnabled(true);
                cancelButton.setEnabled(false);
                try {
                    get();
                } catch (Exception e) {
                    // Error should already be logged via publish() in doInBackground
                    // Show dialog with detailed message
                    StringBuilder errorMsg = new StringBuilder("Error: " + e.getMessage());
                    if (e.getCause() != null) {
                        errorMsg.append("\nCaused by: ").append(e.getCause().getClass().getSimpleName())
                               .append(": ").append(e.getCause().getMessage());
                    }
                    JOptionPane.showMessageDialog(HypocenterLocationPanel.this,
                        errorMsg.toString(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
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
                // Invalid value, ignore
            }
        }
        if (!rmsThresholdStr.isEmpty()) {
            try {
                clsConfig.rmsThreshold = Double.parseDouble(rmsThresholdStr);
            } catch (NumberFormatException e) {
                // Invalid value, ignore
            }
        }
        if (!locErrThresholdStr.isEmpty()) {
            try {
                clsConfig.locErrThreshold = Double.parseDouble(locErrThresholdStr);
            } catch (NumberFormatException e) {
                // Invalid value, ignore
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
                    // Detailed error reporting for GUI
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
                executeButton.setEnabled(true);
                cancelButton.setEnabled(false);
                try {
                    get();
                } catch (Exception e) {
                    // Error should already be logged via publish() in doInBackground
                    // Show dialog with detailed message
                    StringBuilder errorMsg = new StringBuilder("Error: " + e.getMessage());
                    if (e.getCause() != null) {
                        errorMsg.append("\nCaused by: ").append(e.getCause().getClass().getSimpleName())
                               .append(": ").append(e.getCause().getMessage());
                    }
                    JOptionPane.showMessageDialog(HypocenterLocationPanel.this,
                        errorMsg.toString(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        currentWorker.execute();
    }
    
    /**
     * K-distanceグラフを表示
     */
    private void showKDistanceGraph(List<Double> kDistances, double estimatedEps) {
        JFrame graphFrame = new JFrame("k-Distance Graph");
        KDistancePlotPanel plotPanel = new KDistancePlotPanel();
        plotPanel.setKDistances(kDistances, estimatedEps);
        graphFrame.setContentPane(plotPanel);
        graphFrame.pack();
        graphFrame.setLocationRelativeTo(this);
        graphFrame.setVisible(true);
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
                appendLog("中断要求が送信されました...");
            }
        }
    }
    
    /**
     * datファイルから震源データを読み込む
     */
    private java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> loadHypocentersFromDatFile(File datFile) {
        java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = new java.util.ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(datFile))) {
            // 1行目: 緯度 経度 深度 タイプ
            String line1 = br.readLine();
            if (line1 != null) {
                String[] parts1 = line1.trim().split("\\s+");
                if (parts1.length >= 3) {
                    double lat = Double.parseDouble(parts1[0]);
                    double lon = Double.parseDouble(parts1[1]);
                    double depth = Double.parseDouble(parts1[2]);
                    String time = datFile.getName().replace(".dat", "");
                    String type = parts1.length > 3 ? parts1[3] : null;
                    
                    // 2行目: xerr in km, yerr in km, zerr in km, rms residual
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
            // 共通パラメータ
            thresholdField.setText(String.valueOf(config.threshold));
            hypBottomField.setText(String.valueOf(config.hypBottom));
            
            if (config.taupFile != null && !config.taupFile.isEmpty()) {
                if (taupModelCombo != null) {
                    taupModelCombo.setSelectedItem(config.taupFile);
                }
                com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setTaupFile(config.taupFile);
            }
            
            // モード固有パラメータ
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
                if (config.solver.containsKey("STD")) {
                    var stdSolver = config.solver.get("STD");
                    if (stdSolver.has("initialStepBoundFactor") && lmInitialStepBoundField != null) {
                        lmInitialStepBoundField.setText(String.valueOf(stdSolver.get("initialStepBoundFactor").asDouble()));
                    }
                    if (stdSolver.has("costRelativeTolerance") && lmCostRelativeToleranceField != null) {
                        lmCostRelativeToleranceField.setText(String.valueOf(stdSolver.get("costRelativeTolerance").asDouble()));
                    }
                    if (stdSolver.has("parRelativeTolerance") && lmParRelativeToleranceField != null) {
                        lmParRelativeToleranceField.setText(String.valueOf(stdSolver.get("parRelativeTolerance").asDouble()));
                    }
                    if (stdSolver.has("orthoTolerance") && lmOrthoToleranceField != null) {
                        lmOrthoToleranceField.setText(String.valueOf(stdSolver.get("orthoTolerance").asDouble()));
                    }
                    if (stdSolver.has("maxEvaluations") && lmMaxEvaluationsField != null) {
                        lmMaxEvaluationsField.setText(String.valueOf(stdSolver.get("maxEvaluations").asInt()));
                    }
                    if (stdSolver.has("maxIterations") && lmMaxIterationsField != null) {
                        lmMaxIterationsField.setText(String.valueOf(stdSolver.get("maxIterations").asInt()));
                    }
                }
            }
        }
    }
}

