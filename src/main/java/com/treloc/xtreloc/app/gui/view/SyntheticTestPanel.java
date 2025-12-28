package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.solver.SyntheticTest;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.logging.Logger;

/**
 * シンセティックテストデータ作成パネル
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
        setBorder(new TitledBorder("シンセティックテストデータ作成"));
        
        // 左パネル: パラメータ入力
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        
        // 1. 速度構造選択（TauPモデル）
        JPanel taupPanel = createTaupPanel();
        leftPanel.add(taupPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        
        // 2. カタログファイル選択
        JPanel catalogPanel = createCatalogPanel();
        leftPanel.add(catalogPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        
        // 3. 出力ディレクトリ選択
        JPanel outputPanel = createOutputPanel();
        leftPanel.add(outputPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        
        // 4. 実行ボタン
        JPanel buttonPanel = createButtonPanel();
        leftPanel.add(buttonPanel);
        
        // スクロール可能にする
        JScrollPane scrollPane = new JScrollPane(leftPanel);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        // 右パネル: ログ出力
        JPanel logPanel = createLogPanel();
        
        // 左右分割: 左: パラメータ、右: ログ
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, logPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(400);
        
        add(splitPane, BorderLayout.CENTER);
    }
    
    private JPanel createTaupPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("速度構造（TauPモデル）"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("TauPモデル:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> taupCombo = new JComboBox<>(new String[]{"prem", "iasp91", "ak135", "ak135f"});
        taupCombo.setSelectedItem("prem");
        taupCombo.addActionListener(e -> {
            String selected = (String) taupCombo.getSelectedItem();
            if (selected != null && config != null) {
                config.taupFile = selected;
                // 共有ファイルマネージャーに通知
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
        catalogFileField.setEditable(true); // 手動入力も可能にする
        
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
        fieldPanel.add(selectCatalogButton, BorderLayout.WEST); // 左側に配置
        fieldPanel.add(catalogFileField, BorderLayout.CENTER);
        
        // テキストフィールドの変更を監視
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
        panel.setBorder(BorderFactory.createTitledBorder("出力ディレクトリ"));
        
        outputDirField = new JTextField();
        outputDirField.setEditable(true); // 手動入力も可能にする
        
        // フォルダアイコンをボタンに設定（左側に配置）
        selectOutputDirButton = new JButton();
        try {
            Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
            if (folderIcon != null) {
                selectOutputDirButton.setIcon(folderIcon);
            } else {
                selectOutputDirButton.setText("選択");
            }
        } catch (Exception e) {
            selectOutputDirButton.setText("選択");
        }
        selectOutputDirButton.setToolTipText("出力ディレクトリを選択");
        selectOutputDirButton.addActionListener(e -> selectOutputDirectory());
        
        JPanel fieldPanel = new JPanel(new BorderLayout());
        fieldPanel.add(selectOutputDirButton, BorderLayout.WEST); // 左側に配置
        fieldPanel.add(outputDirField, BorderLayout.CENTER);
        
        // テキストフィールドの変更を監視
        outputDirField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateFromField(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateFromField(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateFromField(); }
        });
        
        panel.add(fieldPanel, BorderLayout.CENTER);
        return panel;
    }
    
    private void updateFromField() {
        // テキストフィールドからファイル/ディレクトリを更新
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
        executeButton = new JButton("実行");
        executeButton.setEnabled(false);
        executeButton.addActionListener(e -> executeSyntheticTest());
        panel.add(executeButton);
        
        return panel;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("実行ログ"));
        
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
        
        // 現在のテキストフィールドのパスを初期ディレクトリとして設定
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
        // 現在のテキストフィールドのパスを初期ディレクトリとして設定
        File currentDir = null;
        String currentPath = outputDirField.getText().trim();
        if (!currentPath.isEmpty()) {
            currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                // currentDirをそのまま使用
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
            appendLog("出力ディレクトリを選択: " + selectedOutputDir.getAbsolutePath());
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
                "エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        executeButton.setEnabled(false);
        appendLog("シンセティックテストデータの生成を開始...");
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    // AppConfigを更新（SYNモードの設定が存在しない場合は作成）
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
                    
                    // taupFileとstationFileが設定されていることを確認
                    if (config.taupFile == null || config.taupFile.isEmpty()) {
                        throw new IllegalArgumentException("速度構造ファイル（taupFile）が設定されていません");
                    }
                    if (config.stationFile == null || config.stationFile.isEmpty()) {
                        // 共有ファイルマネージャーから取得を試みる
                        File sharedStationFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
                        if (sharedStationFile != null) {
                            config.stationFile = sharedStationFile.getAbsolutePath();
                        } else {
                            throw new IllegalArgumentException("観測点ファイル（stationFile）が設定されていません");
                        }
                    }
                    
                    // SyntheticTestを実行
                    SyntheticTest syntheticTest = new SyntheticTest(config);
                    syntheticTest.generateDataFromCatalog();
                    
                    publish("シンセティックテストデータの生成が完了しました");
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
                    get(); // 例外があればスロー
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
        
        // 観測点ファイルが設定されていない場合、共有ファイルマネージャーから取得
        if (config != null && (config.stationFile == null || config.stationFile.isEmpty())) {
            File sharedStationFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
            if (sharedStationFile != null) {
                config.stationFile = sharedStationFile.getAbsolutePath();
            }
        }
        
        // 速度構造が設定されていない場合、デフォルト値を設定
        if (config != null && (config.taupFile == null || config.taupFile.isEmpty())) {
            config.taupFile = "prem"; // デフォルト
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setTaupFile("prem");
        }
        
        updateExecuteButtonState();
    }
}

