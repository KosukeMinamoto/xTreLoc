package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.solver.SyntheticTest;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.logging.Logger;

/**
 * Panel for creating synthetic test data
 */
public class SyntheticTestPanel extends JPanel {
    private static final Logger logger = Logger.getLogger(SyntheticTestPanel.class.getName());
    
    private AppConfig config;
    
    private JTextField catalogFileField;
    private JButton selectCatalogButton;
    private JTextField outputDirField;
    private JButton selectOutputDirButton;
    private JButton executeButton;
    private JTextArea logArea;
    private JFileChooser fileChooser;
    private File selectedCatalogFile;
    private File selectedOutputDir;
    
    public SyntheticTestPanel() {
        this.fileChooser = new JFileChooser();
        initComponents();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("Synthetic Test Data Generation"));
        
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        
        JPanel taupPanel = createTaupPanel();
        leftPanel.add(taupPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        
        JPanel catalogPanel = createCatalogPanel();
        leftPanel.add(catalogPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        
        JPanel outputPanel = createOutputPanel();
        leftPanel.add(outputPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        
        JPanel buttonPanel = createButtonPanel();
        leftPanel.add(buttonPanel);
        
        JScrollPane scrollPane = new JScrollPane(leftPanel);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        JPanel logPanel = createLogPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, logPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(400);
        
        add(splitPane, BorderLayout.CENTER);
    }
    
    private JPanel createTaupPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Velocity Structure (TauP Model)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("TauP Model:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> taupCombo = new JComboBox<>(new String[]{"prem", "iasp91", "ak135", "ak135f"});
        taupCombo.setSelectedItem("prem");
        taupCombo.addActionListener(e -> {
            String selected = (String) taupCombo.getSelectedItem();
            if (selected != null && config != null) {
                config.taupFile = selected;
                com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setTaupFile(selected);
            }
        });
        panel.add(taupCombo, gbc);
        
        return panel;
    }
    
    private JPanel createCatalogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Catalog File"));
        
        catalogFileField = new JTextField();
        catalogFileField.setEditable(true);
        
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
        selectCatalogButton.addActionListener(e -> selectCatalogFile());
        
        JPanel fieldPanel = new JPanel(new BorderLayout());
        fieldPanel.add(selectCatalogButton, BorderLayout.WEST);
        fieldPanel.add(catalogFileField, BorderLayout.CENTER);
        catalogFileField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateFromField(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateFromField(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateFromField(); }
        });
        
        panel.add(fieldPanel, BorderLayout.CENTER);
        return panel;
    }
    
    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Output Directory"));
        
        outputDirField = new JTextField();
        outputDirField.setEditable(true);
        
        selectOutputDirButton = new JButton();
        try {
            Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
            if (folderIcon != null) {
                selectOutputDirButton.setIcon(folderIcon);
            } else {
                selectOutputDirButton.setText("Select");
            }
        } catch (Exception e) {
            selectOutputDirButton.setText("Select");
        }
        selectOutputDirButton.setToolTipText("Select output directory");
        selectOutputDirButton.addActionListener(e -> selectOutputDirectory());
        
        JPanel fieldPanel = new JPanel(new BorderLayout());
        fieldPanel.add(selectOutputDirButton, BorderLayout.WEST);
        fieldPanel.add(outputDirField, BorderLayout.CENTER);
        outputDirField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateFromField(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateFromField(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateFromField(); }
        });
        
        panel.add(fieldPanel, BorderLayout.CENTER);
        return panel;
    }
    
    private void updateFromField() {
        String catalogPath = catalogFileField.getText().trim();
        if (!catalogPath.isEmpty()) {
            File file = new File(catalogPath);
            if (file.exists()) {
                selectedCatalogFile = file;
            }
        }
        
        String outputPath = outputDirField.getText().trim();
        if (!outputPath.isEmpty()) {
            File dir = new File(outputPath);
            if (dir.exists() && dir.isDirectory()) {
                selectedOutputDir = dir;
            }
        }
        
        updateExecuteButtonState();
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        executeButton = new JButton("Execute");
        executeButton.setEnabled(false);
        executeButton.addActionListener(e -> executeSyntheticTest());
        panel.add(executeButton);
        
        return panel;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Execution Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
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
                } else {
                    fileChooser.setCurrentDirectory(currentFile);
                }
            }
        }
        
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedCatalogFile = fileChooser.getSelectedFile();
            catalogFileField.setText(selectedCatalogFile.getAbsolutePath());
            updateExecuteButtonState();
            appendLog("Catalog file selected: " + selectedCatalogFile.getAbsolutePath());
        }
    }
    
    private void selectOutputDirectory() {
        File currentDir = null;
        String currentPath = outputDirField.getText().trim();
        if (!currentPath.isEmpty()) {
            currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
            } else if (currentDir.getParentFile() != null && currentDir.getParentFile().exists()) {
                currentDir = currentDir.getParentFile();
            } else {
                currentDir = null;
            }
        }
        
        File selectedDir = com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper.selectDirectory(
            this, "Select Output Directory", currentDir);
        
        if (selectedDir != null) {
            selectedOutputDir = selectedDir;
            outputDirField.setText(selectedOutputDir.getAbsolutePath());
            updateExecuteButtonState();
            appendLog("Output directory selected: " + selectedOutputDir.getAbsolutePath());
        }
    }
    
    private void updateExecuteButtonState() {
        boolean enabled = selectedCatalogFile != null && selectedOutputDir != null && config != null;
        executeButton.setEnabled(enabled);
    }
    
    private void executeSyntheticTest() {
        if (selectedCatalogFile == null || selectedOutputDir == null || config == null) {
            JOptionPane.showMessageDialog(this,
                "Please select catalog file and output directory",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        executeButton.setEnabled(false);
        appendLog("Starting synthetic test data generation...");
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    if (config.modes == null) {
                        config.modes = new java.util.HashMap<>();
                    }
                    AppConfig.ModeConfig synConfig = config.modes.get("SYN");
                    if (synConfig == null) {
                        synConfig = new AppConfig.ModeConfig();
                        config.modes.put("SYN", synConfig);
                    }
                    synConfig.catalogFile = selectedCatalogFile.getAbsolutePath();
                    synConfig.outDirectory = selectedOutputDir.toPath();
                    
                    if (config.taupFile == null || config.taupFile.isEmpty()) {
                        throw new IllegalArgumentException("Velocity structure file (taupFile) is not set");
                    }
                    if (config.stationFile == null || config.stationFile.isEmpty()) {
                        File sharedStationFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
                        if (sharedStationFile != null) {
                            config.stationFile = sharedStationFile.getAbsolutePath();
                        } else {
                            throw new IllegalArgumentException("Station file (stationFile) is not set");
                        }
                    }
                    
                    SyntheticTest syntheticTest = new SyntheticTest(config);
                    syntheticTest.generateDataFromCatalog();
                    
                    publish("Synthetic test data generation completed");
                } catch (Exception e) {
                    // Detailed error reporting for GUI
                    StringBuilder errorMsg = new StringBuilder("Synthetic test data generation error:\n");
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
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    appendLog(message);
                }
            }
            
            @Override
            protected void done() {
                executeButton.setEnabled(true);
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
                    JOptionPane.showMessageDialog(SyntheticTestPanel.this,
                        errorMsg.toString(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        worker.execute();
    }
    
    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public void setConfig(AppConfig config) {
        this.config = config;
        
        if (config != null && (config.stationFile == null || config.stationFile.isEmpty())) {
            File sharedStationFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
            if (sharedStationFile != null) {
                config.stationFile = sharedStationFile.getAbsolutePath();
            }
        }
        
        if (config != null && (config.taupFile == null || config.taupFile.isEmpty())) {
            config.taupFile = "prem";
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setTaupFile("prem");
        }
        
        updateExecuteButtonState();
    }
}

