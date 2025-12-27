package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.solver.HypoGridSearch;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.logging.Logger;

/**
 * Control panel for mode selection and parameter configuration.
 * This panel provides UI components for selecting execution modes and setting parameters
 * for hypocenter location algorithms.
 */
public class ControlPanel extends JPanel {
    private static final Logger logger = Logger.getLogger(ControlPanel.class.getName());
    
    private final MapView mapView;
    private AppConfig config;
    
    private JComboBox<String> modeCombo;
    private JComboBox<String> taupModelCombo;
    private JTextField numGridField;
    private JTextField thresholdField;
    private JTextField hypBottomField;
    private JButton executeButton;
    private JButton exportCsvButton;
    private JFileChooser fileChooser;
    
    public ControlPanel(MapView mapView) {
        this.mapView = mapView;
        this.fileChooser = new JFileChooser();
        initComponents();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("Execution Settings"));
        
        // モード選択パネル
        JPanel modePanel = new JPanel(new GridBagLayout());
        GridBagConstraints modeGbc = new GridBagConstraints();
        modeGbc.insets = new Insets(5, 5, 5, 5);
        modeGbc.anchor = GridBagConstraints.WEST;
        
        modeGbc.gridx = 0; modeGbc.gridy = 0;
        modePanel.add(new JLabel("Mode:"), modeGbc);
        modeGbc.gridx = 1;
        modeCombo = new JComboBox<>(new String[]{"GRD", "STD", "TRD", "CLS", "SYN"});
        modeCombo.addActionListener(e -> updateParameterFields());
        modePanel.add(modeCombo, modeGbc);
        
        // TauPモデル選択
        modeGbc.gridx = 0; modeGbc.gridy = 1;
        modePanel.add(new JLabel("TauP Model:"), modeGbc);
        modeGbc.gridx = 1;
        // TauPの標準モデル
        taupModelCombo = new JComboBox<>(new String[]{"prem", "iasp91", "ak135", "ak135f"});
        taupModelCombo.setSelectedItem("prem"); // デフォルト
        modePanel.add(taupModelCombo, modeGbc);
        
        // パラメータパネル
        JPanel paramPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // numGrid
        gbc.gridx = 0; gbc.gridy = 0;
        paramPanel.add(new JLabel("Grid Count:"), gbc);
        gbc.gridx = 1;
        numGridField = new JTextField("100", 10);
        paramPanel.add(numGridField, gbc);
        
        // threshold
        gbc.gridx = 0; gbc.gridy = 1;
        paramPanel.add(new JLabel("Threshold:"), gbc);
        gbc.gridx = 1;
        thresholdField = new JTextField("0.0", 10);
        paramPanel.add(thresholdField, gbc);
        
        // hypBottom
        gbc.gridx = 0; gbc.gridy = 3;
        paramPanel.add(new JLabel("最大深度 (km):"), gbc);
        gbc.gridx = 1;
        hypBottomField = new JTextField("100.0", 10);
        paramPanel.add(hypBottomField, gbc);
        
        // ボタンパネル
        JPanel buttonPanel = new JPanel(new FlowLayout());
        executeButton = new JButton("Execute");
        executeButton.addActionListener(e -> executeSolver());
        buttonPanel.add(executeButton);
        
        exportCsvButton = new JButton("Export CSV");
        exportCsvButton.addActionListener(e -> exportToCsv());
        buttonPanel.add(exportCsvButton);
        
        // レイアウト
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(modePanel, BorderLayout.NORTH);
        topPanel.add(paramPanel, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        updateParameterFields();
    }
    
    private void updateParameterFields() {
        // モードに応じてパラメータフィールドを更新
        // 現在はGRDモードのみ対応
    }
    
    public void setConfig(AppConfig config) {
        this.config = config;
        if (config != null) {
            if (config.solver != null && config.solver.containsKey("GRD")) {
                var grdSolver = config.solver.get("GRD");
                if (grdSolver.has("numGrid")) {
                    numGridField.setText(String.valueOf(grdSolver.get("numGrid").asInt()));
                }
            }
            thresholdField.setText(String.valueOf(config.threshold));
            hypBottomField.setText(String.valueOf(config.hypBottom));
        }
    }
    
    private void executeSolver() {
        String mode = (String) modeCombo.getSelectedItem();
        
        // TauPモデルを選択してconfigを更新
        String selectedModel = (String) taupModelCombo.getSelectedItem();
        if (config != null && selectedModel != null) {
            // 選択されたモデル名でtaupFileを更新（拡張子なしでモデル名のみ）
            config.taupFile = selectedModel;
        }
        
        // 入力ディレクトリ選択（走時差データ）
        fileChooser.setDialogTitle("Select Directory for Travel Time Difference Data");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = fileChooser.showOpenDialog(this);
        
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File inputDir = fileChooser.getSelectedFile();
        
        // ディレクトリ内の全ての.datファイルを取得
        java.util.List<File> datFiles = findDatFiles(inputDir);
        if (datFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No .dat files found in the selected directory.",
                "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 出力ディレクトリ選択
        fileChooser.setDialogTitle("Select Output Directory");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        result = fileChooser.showOpenDialog(this);
        
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File outputDir = fileChooser.getSelectedFile();
        
        // 実行
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                executeButton.setEnabled(false);
                publish("Executing...");
                
                try {
                    if ("GRD".equals(mode)) {
                        // 全ての.datファイルを処理
                        int processedCount = 0;
                        java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> allHypocenters = 
                            new java.util.ArrayList<>();
                        
                        for (File datFile : datFiles) {
                            String inputPath = datFile.getAbsolutePath();
                            String outputPath = new File(outputDir, datFile.getName()).getAbsolutePath();
                            
                            // 各ファイルごとに新しいsolverインスタンスを作成
                            HypoGridSearch solver = new HypoGridSearch(config);
                            solver.start(inputPath, outputPath);
                            processedCount++;
                            publish(String.format("Processing: %d/%d - %s", processedCount, datFiles.size(), datFile.getName()));
                            
                            // 実行結果を読み込んで可視化用リストに追加
                            try {
                                java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = 
                                    loadHypocentersFromDatFile(new File(outputPath));
                                allHypocenters.addAll(hypocenters);
                            } catch (Exception e) {
                                logger.warning("Failed to load results: " + e.getMessage());
                            }
                        }
                        
                        publish(String.format("Execution completed: %d files processed", processedCount));
                        
                        // 実行結果を可視化
                        SwingUtilities.invokeLater(() -> {
                            try {
                                if (!allHypocenters.isEmpty()) {
                                    displayResults(allHypocenters);
                                }
                            } catch (Exception e) {
                                logger.warning("Failed to visualize results: " + e.getMessage());
                            }
                        });
                    } else {
                        publish("Unimplemented mode: " + mode);
                    }
                } catch (edu.sc.seis.TauP.TauModelException e) {
                    logger.severe("TauP model loading error: " + e.getMessage());
                    String errorMsg = "Failed to load TauP model.\n" +
                        "Selected model: " + selectedModel + "\n" +
                        "Error: " + e.getMessage() + "\n\n" +
                        "Available models: prem, iasp91, ak135, ak135f";
                    publish(errorMsg);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(ControlPanel.this, errorMsg,
                            "TauP Model Error", JOptionPane.ERROR_MESSAGE);
                    });
                } catch (Exception e) {
                    logger.severe("Execution error: " + e.getMessage());
                    String errorMsg = "Execution error: " + e.getMessage();
                    publish(errorMsg);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(ControlPanel.this, errorMsg,
                            "Execution Error", JOptionPane.ERROR_MESSAGE);
                    });
                } finally {
                    executeButton.setEnabled(true);
                }
                
                return null;
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    JOptionPane.showMessageDialog(ControlPanel.this, message, "Execution Result", 
                        JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };
        
        worker.execute();
    }
    
    private java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> lastResults = new java.util.ArrayList<>();
    
    private void exportToCsv() {
        fileChooser.setDialogTitle("Save CSV File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "CSV files (*.csv)", "csv"));
        int result = fileChooser.showSaveDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File csvFile = fileChooser.getSelectedFile();
            try {
                if (lastResults.isEmpty()) {
                    JOptionPane.showMessageDialog(this, 
                        "No hypocenter data to export.\nPlease execute first.",
                        "Information", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                com.treloc.xtreloc.app.gui.service.CsvExporter.exportHypocenters(lastResults, csvFile);
                JOptionPane.showMessageDialog(this, 
                    "CSV file exported: " + csvFile.getName(),
                    "Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "CSV export error: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Load hypocenter data from a .dat file.
     * 
     * @param datFile the .dat file to read
     * @return list of hypocenter data loaded from the file
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
                    // ファイル名から時刻を取得（例: 071201.000030.dat → 071201.000030）
                    // parts1[3]はタイプ（STD, INI, ERRなど）なので、常にファイル名から取得
                    String time = datFile.getName().replace(".dat", "");
                    
                    // 2行目: xerr in km, yerr in km, zerr in km, rms residual
                    double xerr = 0.0;
                    double yerr = 0.0;
                    double zerr = 0.0;
                    double rms = 0.0;
                    
                    String line2 = br.readLine();
                    if (line2 != null && !line2.trim().isEmpty()) {
                        String[] parts2 = line2.trim().split("\\s+");
                        // 2行目が数値のみ（エラー情報）か、観測点コードを含むかで判定
                        try {
                            // 最初の要素が数値かどうかで判定
                            Double.parseDouble(parts2[0]);
                            // 数値のみの場合（エラー情報行）
                            if (parts2.length >= 4) {
                                xerr = Double.parseDouble(parts2[0]);
                                yerr = Double.parseDouble(parts2[1]);
                                zerr = Double.parseDouble(parts2[2]);
                                rms = Double.parseDouble(parts2[3]);
                            }
                        } catch (NumberFormatException e) {
                            // 2行目が観測点ペアの場合（エラー情報行がない形式）
                            // エラー情報はデフォルト値（0.0）のまま
                        }
                    }
                    
                    hypocenters.add(new com.treloc.xtreloc.app.gui.model.Hypocenter(time, lat, lon, depth, xerr, yerr, zerr, rms));
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load .dat file: " + e.getMessage());
        }
        return hypocenters;
    }
    
    /**
     * Display hypocenter data results on the map.
     * 
     * @param hypocenters list of hypocenter data to display
     */
    public void displayResults(java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters) {
        try {
            // 結果を保持
            lastResults = hypocenters;
            mapView.showHypocenters(hypocenters);
        } catch (Exception e) {
            logger.severe("Hypocenter display error: " + e.getMessage());
            JOptionPane.showMessageDialog(this, 
                "Hypocenter display error: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Recursively search for all .dat files in a directory.
     * 
     * @param directory the directory to search
     * @return list of .dat files found
     */
    private java.util.List<File> findDatFiles(File directory) {
        java.util.List<File> datFiles = new java.util.ArrayList<>();
        if (!directory.isDirectory()) {
            return datFiles;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 再帰的に検索
                    datFiles.addAll(findDatFiles(file));
                } else if (file.getName().toLowerCase().endsWith(".dat")) {
                    datFiles.add(file);
                }
            }
        }
        return datFiles;
    }
}

