package com.treloc.xtreloc.app.gui.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import edu.sc.seis.seisFile.sac.SacTimeSeries;

import com.treloc.xtreloc.app.gui.service.ArrivalTimeManager;
import com.treloc.xtreloc.app.gui.service.WaveProcessor;
import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper;

import java.awt.BasicStroke;

/**
 * Panel for waveform picking functionality.
 * Allows users to load SAC files, display waveforms, and pick P/S arrival times.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class WaveformPickingPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    
    private File selectedDir;
    private ArrivalTimeManager arrivalTimeManager;
    private JTextField lowFreqField;
    private JTextField highFreqField;
    private float lowFreq = 1.0f;
    private float highFreq = 16.0f;
    private String outputDir = ".";
    
    private JList<String> fileList;
    private JPanel chartPanel;
    private JPanel zoomChartPanel; // Panel for zoomed waveform display
    private JLabel mousePositionLabel;
    private JLabel statusLabel;
    private SacTimeSeries[] currentWaveforms; // Store current waveforms for zoom display
    private String[] currentFileNames; // Store current file names for zoom display
    private JTree directoryTree;
    private File rootDirectory;
    private File[] currentFilesInDir;
    private File treeRootLimit; // Limit tree exploration to this directory and below
    private JTextField outputDirField;
    private JButton selectOutputDirButton;
    private JTextField inputDirField;
    private JButton selectInputDirButton;
    private JTextField stationFileField;
    private JButton selectStationFileButton;
    private String stationFilePath; // Path to station file
    private HashMap<String, String> channelMap; // Maps "station_component" to channel number
    
    /** Key state for key+click picking (Settings > Picking). */
    private volatile boolean pickingKeyPDown;
    private volatile boolean pickingKeySDown;
    
    // Panels for left and right sides
    private JPanel leftPanel;
    private JPanel rightPanel;
    
    public WaveformPickingPanel() {
        this.arrivalTimeManager = new ArrivalTimeManager();
        this.channelMap = new HashMap<>();
        this.stationFilePath = null;
        
        // Load output directory from settings
        AppSettings settings = AppSettings.load();
        String homeDir = settings.getHomeDirectory();
        if (homeDir != null && !homeDir.isEmpty()) {
            File homeDirFile = new File(homeDir);
            if (homeDirFile.exists() && homeDirFile.isDirectory()) {
                this.outputDir = homeDir;
            }
        }
        
        initComponents();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Left panel: controls, directory tree, and file list
        leftPanel = createLeftPanel();
        
        // Right panel: chart panel (waveform display)
        rightPanel = createRightPanel();
        
        // Bottom: status and mouse position
        JPanel bottomPanel = new JPanel(new BorderLayout());
        mousePositionLabel = new JLabel("Mouse Position: (0, 0)");
        statusLabel = new JLabel("No directory selected");
        bottomPanel.add(mousePositionLabel, BorderLayout.WEST);
        bottomPanel.add(statusLabel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Initialize with empty chart
        currentWaveforms = new SacTimeSeries[0];
        currentFileNames = new String[0];
        updateCharts(new SacTimeSeries[0]);
    }
    
    /**
     * Gets the left panel (controls, tree, file list).
     * 
     * @return the left panel
     */
    public JPanel getLeftPanel() {
        return leftPanel;
    }
    
    /**
     * Gets the right panel (waveform display).
     * 
     * @return the right panel
     */
    public JPanel getRightPanel() {
        return rightPanel;
    }
    
    private static int mouseButtonFromSetting(String s) {
        if (s == null) return MouseEvent.BUTTON1;
        switch (s.trim().toLowerCase()) {
            case "right": return MouseEvent.BUTTON3;
            case "middle": return MouseEvent.BUTTON2;
            default: return MouseEvent.BUTTON1;
        }
    }
    
    private static int keyCodeFromSetting(String s) {
        if (s == null || "none".equalsIgnoreCase(s.trim())) return 0;
        switch (s.trim().toUpperCase()) {
            case "P": return KeyEvent.VK_P;
            case "S": return KeyEvent.VK_S;
            case "1": return KeyEvent.VK_1;
            case "2": return KeyEvent.VK_2;
            default: return 0;
        }
    }
    
    /**
     * Creates the right panel with waveform display and zoom window.
     * 
     * @return the right panel
     */
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Main chart panel (waveform display)
        chartPanel = new JPanel(new GridLayout(1, 1));
        chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        
        // Zoom chart panel (zoomed waveform display)
        zoomChartPanel = new JPanel(new GridLayout(1, 1));
        zoomChartPanel.setBorder(BorderFactory.createTitledBorder("Zoom View"));
        zoomChartPanel.setPreferredSize(new java.awt.Dimension(0, 150)); // Initial height (reduced from 200)
        
        // Use JSplitPane to divide main view and zoom view vertically
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanel, zoomChartPanel);
        splitPane.setResizeWeight(0.7); // Main view takes 70% of space
        splitPane.setDividerLocation(0.7); // Set initial divider position
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        // Add mouse click guide at the bottom of the right panel
        JPanel guidePanel = new JPanel(new BorderLayout());
        guidePanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel guideLabel = new JLabel("<html><small>Guide: P/S by mouse or hold key then click. Configure in Settings → Picking.</small></html>");
        guideLabel.setForeground(new Color(100, 100, 100));
        guidePanel.add(guideLabel, BorderLayout.CENTER);
        panel.add(guidePanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Top: Controls
        JPanel topPanel = createTopPanel();
        panel.add(topPanel, BorderLayout.NORTH);
        
        // Center: Split between directory tree and file list
        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        centerSplit.setResizeWeight(0.4);
        centerSplit.setOneTouchExpandable(true);
        centerSplit.setContinuousLayout(true);
        
        // Directory tree
        JPanel treePanel = createDirectoryTreePanel();
        centerSplit.setTopComponent(treePanel);
        
        // File list
        JPanel filePanel = createFileListPanel();
        centerSplit.setBottomComponent(filePanel);
        
        panel.add(centerSplit, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        Color bgColor = UIManager.getColor("Panel.background");
        panel.setBackground(bgColor != null ? bgColor : Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 220), 1),
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                "⚙️ Controls",
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
        
        // Input directory (SAC files root directory)
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Input Directory (SAC Root):"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        inputDirField = new JTextField();
        inputDirField.setEditable(false);
        inputDirField.setHorizontalAlignment(JTextField.LEFT);
        inputDirField.setBackground(new Color(255, 255, 255));
        selectInputDirButton = new JButton();
        try {
            javax.swing.Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
            if (folderIcon != null) {
                selectInputDirButton.setIcon(folderIcon);
            } else {
                selectInputDirButton.setText("📁");
            }
        } catch (Exception e) {
            selectInputDirButton.setText("📁");
        }
        selectInputDirButton.setToolTipText("Select input directory for SAC files");
        selectInputDirButton.addActionListener(e -> selectInputDirectory());
        selectInputDirButton.setBackground(new Color(70, 130, 180));
        selectInputDirButton.setForeground(Color.WHITE);
        selectInputDirButton.setFocusPainted(false);
        selectInputDirButton.setBorderPainted(false);
        selectInputDirButton.setOpaque(true);
        JPanel inputDirPanel = new JPanel(new BorderLayout());
        inputDirPanel.add(selectInputDirButton, BorderLayout.WEST);
        inputDirPanel.add(inputDirField, BorderLayout.CENTER);
        panel.add(inputDirPanel, gbc);
        gbc.weightx = 0.0;
        
        // Output directory (picking results output)
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Output Directory (Picking Results):"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        outputDirField = new JTextField();
        outputDirField.setEditable(false);
        outputDirField.setHorizontalAlignment(JTextField.LEFT);
        outputDirField.setBackground(new Color(255, 255, 255));
        if (outputDir != null && !outputDir.isEmpty() && !outputDir.equals(".")) {
            outputDirField.setText(outputDir);
        }
        selectOutputDirButton = new JButton();
        try {
            javax.swing.Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
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
        
        // Frequency filter controls (vertical layout)
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Freq Min:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        lowFreqField = new JTextField("1", 15);
        lowFreqField.setPreferredSize(new java.awt.Dimension(100, lowFreqField.getPreferredSize().height));
        panel.add(lowFreqField, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Freq Max:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        highFreqField = new JTextField("45", 15);
        highFreqField.setPreferredSize(new java.awt.Dimension(100, highFreqField.getPreferredSize().height));
        panel.add(highFreqField, gbc);
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton updateButton = new JButton("Update Filter");
        updateButton.addActionListener(e -> updateFilter());
        panel.add(updateButton, gbc);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        
        // Save button (always saves in NONLINLOC .obs format)
        // Note: WIN pickfile format conversion is available via separate conversion tool
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.gridwidth = 4;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton saveButton = new JButton("Save (.obs format)");
        saveButton.addActionListener(e -> savePicks());
        saveButton.setBackground(new Color(50, 150, 50));
        saveButton.setForeground(Color.WHITE);
        saveButton.setFocusPainted(false);
        saveButton.setBorderPainted(false);
        saveButton.setOpaque(true);
        panel.add(saveButton, gbc);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        
        return panel;
    }
    
    private JPanel createDirectoryTreePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Directory Tree"));
        
        // Initial root will be set when input directory is selected
        // For now, use a placeholder
        File initialRoot = new File(System.getProperty("user.dir"));
        rootDirectory = initialRoot;
        treeRootLimit = initialRoot; // Set limit for tree exploration
        
        // Create root node with lazy loading
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(initialRoot);
        // Add a dummy child to show expand icon if directory has subdirectories
        if (hasSubdirectories(initialRoot)) {
            rootNode.add(new DefaultMutableTreeNode("Loading..."));
        }
        
        DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
        directoryTree = new JTree(treeModel);
        directoryTree.setRootVisible(true);
        directoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        directoryTree.setShowsRootHandles(true);
        
        // Lazy loading: load children only when node is expanded
        directoryTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                TreePath path = event.getPath();
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                File dir = (File) node.getUserObject();
                
                // If node has dummy child, replace it with real children
                if (node.getChildCount() == 1 && node.getChildAt(0).toString().equals("Loading...")) {
                    node.removeAllChildren();
                    loadChildrenForNode(node, dir);
                    ((DefaultTreeModel) directoryTree.getModel()).nodeStructureChanged(node);
                }
            }
            
            @Override
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
                // Do nothing on collapse
            }
        });
        
        // Tree selection listener
        directoryTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                TreePath path = e.getNewLeadSelectionPath();
                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    Object userObject = node.getUserObject();
                    if (userObject instanceof File) {
                        File selectedFile = (File) userObject;
                        if (selectedFile != null && selectedFile.isDirectory()) {
                            loadDirectory(selectedFile);
                        }
                    }
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(directoryTree);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Updates the directory tree based on the selected input directory.
     * 
     * @param inputDir the input directory to set as root
     */
    private void updateDirectoryTree(File inputDir) {
        if (inputDir == null || !inputDir.exists() || !inputDir.isDirectory()) {
            return;
        }
        
        rootDirectory = inputDir;
        treeRootLimit = inputDir; // Set limit for tree exploration
        
        DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode(inputDir);
        // Add dummy child to show expand icon if directory has subdirectories
        if (hasSubdirectories(inputDir)) {
            newRoot.add(new DefaultMutableTreeNode("Loading..."));
        }
        DefaultTreeModel treeModel = new DefaultTreeModel(newRoot);
        directoryTree.setModel(treeModel);
        directoryTree.expandRow(0);
    }
    
    /**
     * Selects the input directory for SAC files and updates the tree.
     */
    private void selectInputDirectory() {
        JFileChooser dirChooser = new JFileChooser();
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        // Set current directory if input directory is already set
        if (selectedDir != null && selectedDir.exists()) {
            dirChooser.setCurrentDirectory(selectedDir);
        } else {
            // Use settings home directory or current directory
            AppSettings settings = AppSettings.load();
            String homeDirStr = settings.getHomeDirectory();
            if (homeDirStr != null && !homeDirStr.isEmpty()) {
                File homeDir = new File(homeDirStr);
                if (homeDir.exists() && homeDir.isDirectory()) {
                    dirChooser.setCurrentDirectory(homeDir);
                }
            }
        }
        
        int returnValue = dirChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedInputDir = dirChooser.getSelectedFile();
            inputDirField.setText(selectedInputDir.getAbsolutePath());
            updateDirectoryTree(selectedInputDir);
            loadDirectory(selectedInputDir);
        }
    }
    
    private JPanel createFileListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("SAC Files"));
        
        // File list
        fileList = new JList<>();
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane = new JScrollPane(fileList);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Loads children directories for a node (lazy loading).
     * Only loads direct children, not recursively.
     * Only explores directories within the tree root limit.
     * 
     * @param node the tree node to add children to
     * @param dir the directory to load children from
     */
    private void loadChildrenForNode(DefaultMutableTreeNode node, File dir) {
        if (!dir.isDirectory() || !dir.canRead()) {
            return;
        }
        
        // Check if directory is within the tree root limit
        if (treeRootLimit != null) {
            try {
                String dirPath = dir.getCanonicalPath();
                String rootPath = treeRootLimit.getCanonicalPath();
                // Only explore if directory is within or equal to root limit
                if (!dirPath.startsWith(rootPath) && !dirPath.equals(rootPath)) {
                    return; // Don't explore directories outside the root limit
                }
            } catch (Exception e) {
                // If path comparison fails, skip this directory
                return;
            }
        }
        
        try {
            File[] children = dir.listFiles();
            if (children != null) {
                // Only add directories to tree (not files)
                for (File child : children) {
                    if (child.isDirectory() && !child.isHidden() && child.canRead()) {
                        // Check if child is within the tree root limit
                        if (treeRootLimit != null) {
                            try {
                                String childPath = child.getCanonicalPath();
                                String rootPath = treeRootLimit.getCanonicalPath();
                                // Only add if child is within or equal to root limit
                                if (!childPath.startsWith(rootPath) && !childPath.equals(rootPath)) {
                                    continue; // Skip directories outside the root limit
                                }
                            } catch (Exception e) {
                                continue; // Skip if path comparison fails
                            }
                        }
                        
                        DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
                        // Add dummy child to show expand icon if directory has subdirectories
                        if (hasSubdirectories(child)) {
                            childNode.add(new DefaultMutableTreeNode("Loading..."));
                        }
                        node.add(childNode);
                    }
                }
            }
        } catch (SecurityException e) {
            // Ignore permission errors silently
        } catch (Exception e) {
            // Ignore other errors silently
        }
    }
    
    /**
     * Checks if a directory has subdirectories (without loading them all).
     * Only checks directories within the tree root limit.
     * 
     * @param dir the directory to check
     * @return true if directory has at least one subdirectory within the root limit
     */
    private boolean hasSubdirectories(File dir) {
        if (!dir.isDirectory() || !dir.canRead()) {
            return false;
        }
        
        // Check if directory is within the tree root limit
        if (treeRootLimit != null) {
            try {
                String dirPath = dir.getCanonicalPath();
                String rootPath = treeRootLimit.getCanonicalPath();
                // Only check if directory is within or equal to root limit
                if (!dirPath.startsWith(rootPath) && !dirPath.equals(rootPath)) {
                    return false; // Don't check directories outside the root limit
                }
            } catch (Exception e) {
                return false; // If path comparison fails, assume no subdirectories
            }
        }
        
        try {
            File[] children = dir.listFiles();
            if (children != null) {
                // Just check if there's at least one subdirectory within the root limit
                for (File child : children) {
                    if (child.isDirectory() && !child.isHidden()) {
                        // Check if child is within the tree root limit
                        if (treeRootLimit != null) {
                            try {
                                String childPath = child.getCanonicalPath();
                                String rootPath = treeRootLimit.getCanonicalPath();
                                // Only count if child is within or equal to root limit
                                if (!childPath.startsWith(rootPath) && !childPath.equals(rootPath)) {
                                    continue; // Skip directories outside the root limit
                                }
                            } catch (Exception e) {
                                continue; // Skip if path comparison fails
                            }
                        }
                        return true;
                    }
                }
            }
        } catch (SecurityException e) {
            // Ignore permission errors
        } catch (Exception e) {
            // Ignore other errors
        }
        
        return false;
    }
    
    
    private void loadDirectory(File dir) {
        try {
            selectedDir = dir;
            File[] filesInDir = dir.listFiles((d, name) -> 
                name.endsWith(".sac") || name.endsWith(".SAC"));
            
            if (filesInDir == null || filesInDir.length == 0) {
                statusLabel.setText("No SAC files in " + dir.getName());
                fileList.setListData(new String[0]);
                currentFilesInDir = new File[0];
                updateCharts(new SacTimeSeries[0]);
                return;
            }
            
            currentFilesInDir = filesInDir;
            
            arrivalTimeManager.clearArrivalTimes();
            String obsFilePath = outputDir + "/" + selectedDir.getName() + ".obs";
            File obsFile = new File(obsFilePath);
            if (obsFile.exists()) {
                statusLabel.setText("Loading existing picks from " + obsFile.getName());
                arrivalTimeManager = arrivalTimeManager.readFromObs(obsFilePath);
            } else {
                statusLabel.setText("No existing picks file found");
            }
            
            updateFileList(filesInDir);
            addFileSelectionListener(filesInDir);
            statusLabel.setText("Loaded " + filesInDir.length + " SAC files from " + dir.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Error loading directory: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
    
    
    private void updateFileList(File[] filesInDir) {
        Arrays.sort(filesInDir, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        
        String[] fileNames = new String[filesInDir.length];
        for (int i = 0; i < filesInDir.length; i++) {
            fileNames[i] = filesInDir[i].getName();
        }
        fileList.setListData(fileNames);
    }
    
    private void addFileSelectionListener(File[] filesInDir) {
        for (ListSelectionListener listener : fileList.getListSelectionListeners()) {
            fileList.removeListSelectionListener(listener);
        }
        
        fileList.addListSelectionListener((ListSelectionEvent event) -> {
            if (!event.getValueIsAdjusting()) {
                try {
                    int[] selectedIndices = fileList.getSelectedIndices();
                    if (selectedIndices.length == 0) {
                        updateCharts(new SacTimeSeries[0]);
                        return;
                    }
                    
                    statusLabel.setText("Loading...");
                    
                    SacTimeSeries[] waveforms = new SacTimeSeries[selectedIndices.length];
                    for (int i = 0; i < selectedIndices.length; i++) {
                        // Read original waveform
                        waveforms[i] = WaveProcessor.read(filesInDir[selectedIndices[i]].getAbsolutePath());
                        // Apply band-pass filter
                        waveforms[i] = WaveProcessor.bandPassFilter(waveforms[i], lowFreq, highFreq);
                    }
                    updateCharts(waveforms);
                    statusLabel.setText("Loaded " + waveforms.length + " waveform(s) with filter: " + lowFreq + " - " + highFreq + " Hz");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                        "Error loading waveforms: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                    statusLabel.setText("Error loading waveforms");
                }
            }
        });
    }
    
    private void updateFilter() {
        try {
            float newLowFreq = Float.parseFloat(lowFreqField.getText());
            float newHighFreq = Float.parseFloat(highFreqField.getText());
            
            // Validate frequency range
            if (newLowFreq < 0 || newHighFreq < 0 || newLowFreq >= newHighFreq) {
                JOptionPane.showMessageDialog(this,
                    "Invalid frequency range: Min must be < Max and both must be positive",
                    "Error", JOptionPane.ERROR_MESSAGE);
                // Restore previous values
                lowFreqField.setText(String.valueOf(lowFreq));
                highFreqField.setText(String.valueOf(highFreq));
                return;
            }
            
            lowFreq = newLowFreq;
            highFreq = newHighFreq;
            
            if (selectedDir != null && fileList.getSelectedIndices().length > 0 && currentFilesInDir != null) {
                int[] selectedIndices = fileList.getSelectedIndices();
                File[] filesInDir = currentFilesInDir;
                
                statusLabel.setText("Loading...");
                
                SacTimeSeries[] waveforms = new SacTimeSeries[selectedIndices.length];
                for (int i = 0; i < selectedIndices.length; i++) {
                    // Read original waveform
                    waveforms[i] = WaveProcessor.read(filesInDir[selectedIndices[i]].getAbsolutePath());
                    // Apply band-pass filter
                    waveforms[i] = WaveProcessor.bandPassFilter(waveforms[i], lowFreq, highFreq);
                }
                updateCharts(waveforms);
                statusLabel.setText("Filter applied: " + lowFreq + " - " + highFreq + " Hz to " + waveforms.length + " waveforms");
            } else {
                statusLabel.setText("Filter settings updated: " + lowFreq + " - " + highFreq + " Hz (no waveforms selected)");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "Invalid frequency values: Please enter valid numbers",
                "Error", JOptionPane.ERROR_MESSAGE);
            // Restore previous values
            lowFreqField.setText(String.valueOf(lowFreq));
            highFreqField.setText(String.valueOf(highFreq));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Error updating filter: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
    
    private void updateCharts(SacTimeSeries[] waveforms) {
        chartPanel.removeAll();
        chartPanel.setLayout(new GridLayout(1, 1));
        
        // Store current waveforms for zoom display
        currentWaveforms = waveforms;
        
        if (waveforms.length == 0) {
            currentFileNames = new String[0];
            updateZoomChart(null, -1, -1); // Clear zoom view
            chartPanel.revalidate();
            chartPanel.repaint();
            return;
        }
        
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        double offset = 0.0;
        double offsetIncrement = 1.0;
        
        Color[] seriesColor = new Color[waveforms.length];
        String[] fileNames = new String[waveforms.length];
        
        for (int i = 0; i < waveforms.length; i++) {
            TimeSeries series = WaveProcessor.getTimeSeries(waveforms[i]);
            TimeSeries normalizedSeries = normalizeTimeSeries(series);
            
            TimeSeries offsetSeries = new TimeSeries("offset_" + i);
            for (int j = 0; j < normalizedSeries.getItemCount(); j++) {
                double valueWithOffset = normalizedSeries.getValue(j).doubleValue() + offset;
                offsetSeries.add(normalizedSeries.getTimePeriod(j), valueWithOffset);
            }
            dataset.addSeries(offsetSeries);
            
            String station = waveforms[i].getHeader().getKstnm().trim();
            String component = waveforms[i].getHeader().getKcmpnm().trim();
            fileNames[i] = (station + "__" + component).replace(" ", "");
            offset += offsetIncrement;
            
            // Color coding based on component
            if (component.contains("Z") || component.contains("U") || component.contains("V")) {
                seriesColor[i] = Color.BLUE;
            } else if (component.contains("X") || component.contains("Y") || component.contains("H")) {
                seriesColor[i] = Color.BLACK;
            } else {
                seriesColor[i] = Color.RED;
            }
        }
        
        // Store current file names for zoom display
        currentFileNames = fileNames;
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            null,
            "Time",
            "Amplitude",
            dataset,
            false,
            false,
            false);
        
        XYPlot plot = (XYPlot) chart.getPlot();
        // Disable panning to prevent waveform movement when using middle button for S-wave picking
        plot.setDomainPannable(false);
        plot.setRangePannable(false);
        plot.clearAnnotations();
        
        for (int i = 0; i < waveforms.length; i++) {
            plot.getRenderer().setSeriesPaint(i, seriesColor[i]);
            updateAnnotations(plot, waveforms[i].getHeader().getKstnm().trim(), i);
        }
        
        // Custom Y-axis with station names
        org.jfree.chart.axis.SymbolAxis yAxis = new org.jfree.chart.axis.SymbolAxis(null, fileNames);
        yAxis.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(1));
        yAxis.setTickLabelsVisible(true);
        yAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 10));
        yAxis.setTickLabelPaint(Color.BLACK);
        yAxis.setVerticalTickLabels(true);
        plot.setRangeAxis(yAxis);
        
        ChartPanel chartPanelComponent = new ChartPanel(chart);
        chartPanelComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chartPanelComponent.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        
        // Get the default popup menu before disabling it
        JPopupMenu contextMenu = chartPanelComponent.getPopupMenu();
        
        // Disable default popup menu (right-click context menu)
        chartPanelComponent.setPopupMenu(null);
        
        chartPanelComponent.setFocusable(true);
        chartPanelComponent.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == keyCodeFromSetting(AppSettings.load().getPickingKeyP())) pickingKeyPDown = true;
                if (code == keyCodeFromSetting(AppSettings.load().getPickingKeyS())) pickingKeySDown = true;
            }
            @Override
            public void keyReleased(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == keyCodeFromSetting(AppSettings.load().getPickingKeyP())) pickingKeyPDown = false;
                if (code == keyCodeFromSetting(AppSettings.load().getPickingKeyS())) pickingKeySDown = false;
            }
            @Override
            public void keyTyped(KeyEvent e) {}
        });
        
        // Add mouse listener to show context menu on configured button only
        chartPanelComponent.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int contextButton = mouseButtonFromSetting(AppSettings.load().getPickingMouseContext());
                if (e.getButton() == contextButton) {
                    if (contextMenu != null) {
                        contextMenu.show(chartPanelComponent, e.getX(), e.getY());
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    e.consume();
                }
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    e.consume();
                }
            }
        });
        
        chartPanel.add(chartPanelComponent);
        
        // Mouse listener for picking (uses Settings > Picking for button/key mapping)
        chartPanelComponent.addChartMouseListener(new ChartMouseListener() {
            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
                ChartEntity entity = event.getEntity();
                if (entity instanceof XYItemEntity) {
                    XYItemEntity itemEntity = (XYItemEntity) entity;
                    int seriesIndex = itemEntity.getSeriesIndex();
                    int itemIndex = itemEntity.getItem();
                    TimeSeries series = dataset.getSeries(seriesIndex);
                    RegularTimePeriod xTime = series.getTimePeriod(itemIndex);
                    
                    String stationName = fileNames[seriesIndex].split("__")[0];
                    String component = fileNames[seriesIndex].split("__")[1];
                    
                    int button = event.getTrigger().getButton();
                    Date xDate = new Date(xTime.getFirstMillisecond());
                    
                    AppSettings settings = AppSettings.load();
                    int buttonP = mouseButtonFromSetting(settings.getPickingMouseP());
                    int buttonS = mouseButtonFromSetting(settings.getPickingMouseS());
                    int buttonContext = mouseButtonFromSetting(settings.getPickingMouseContext());
                    int keyP = keyCodeFromSetting(settings.getPickingKeyP());
                    int keyS = keyCodeFromSetting(settings.getPickingKeyS());
                    
                    String phase = null;
                    if (keyP != 0 && pickingKeyPDown) {
                        phase = "P";
                    } else if (keyS != 0 && pickingKeySDown) {
                        phase = "S";
                    } else {
                        if (button == buttonContext) return;
                        if (button == buttonP) phase = "P";
                        else if (button == buttonS) phase = "S";
                    }
                    if (phase != null) {
                        arrivalTimeManager.updateArrivalTime(stationName, component, phase, xDate);
                    } else {
                        return;
                    }
                    
                    // Refresh annotations
                    plot.clearAnnotations();
                    for (int i = 0; i < waveforms.length; i++) {
                        updateAnnotations(plot, waveforms[i].getHeader().getKstnm().trim(), i);
                    }
                    
                    // Update zoom view if it's currently showing this series
                    ChartEntity currentEntity = event.getEntity();
                    if (currentEntity instanceof XYItemEntity) {
                        XYItemEntity currentItemEntity = (XYItemEntity) currentEntity;
                        int currentSeriesIndex = currentItemEntity.getSeriesIndex();
                        int currentItemIndex = currentItemEntity.getItem();
                        updateZoomChart(dataset, currentSeriesIndex, currentItemIndex);
                    }
                }
            }
            
            @Override
            public void chartMouseMoved(ChartMouseEvent event) {
                int x = event.getTrigger().getX();
                int y = event.getTrigger().getY();
                mousePositionLabel.setText("Mouse Position: (" + x + ", " + y + ")");
                
                // Update zoom view when mouse moves over waveform
                ChartEntity entity = event.getEntity();
                if (entity instanceof XYItemEntity) {
                    XYItemEntity itemEntity = (XYItemEntity) entity;
                    int seriesIndex = itemEntity.getSeriesIndex();
                    int itemIndex = itemEntity.getItem();
                    updateZoomChart(dataset, seriesIndex, itemIndex);
                } else {
                    // Clear zoom view when mouse is not over a data point
                    updateZoomChart(null, -1, -1);
                }
            }
        });
        
        chartPanel.revalidate();
        chartPanel.repaint();
    }
    
    /**
     * Updates the zoom chart display with a zoomed view around the selected point.
     * 
     * @param dataset the dataset (can be null to clear zoom view)
     * @param seriesIndex the series index of the point under mouse cursor
     * @param itemIndex the item index of the point under mouse cursor
     */
    private void updateZoomChart(TimeSeriesCollection dataset, int seriesIndex, int itemIndex) {
        zoomChartPanel.removeAll();
        zoomChartPanel.setLayout(new GridLayout(1, 1));
        
        if (dataset == null || seriesIndex < 0 || itemIndex < 0 || 
            currentWaveforms == null || currentWaveforms.length == 0 ||
            seriesIndex >= currentWaveforms.length) {
            zoomChartPanel.revalidate();
            zoomChartPanel.repaint();
            return;
        }
        
        try {
            // Get the waveform for the selected series
            SacTimeSeries waveform = currentWaveforms[seriesIndex];
            TimeSeries series = WaveProcessor.getTimeSeries(waveform);
            TimeSeries normalizedSeries = normalizeTimeSeries(series);
            
            // Calculate zoom window: show ±N seconds around the selected point (from settings)
            // Get sampling rate from SAC header
            float delta = waveform.getHeader().getDelta(); // Sampling interval in seconds
            AppSettings settings = AppSettings.load();
            double windowSeconds = settings.getZoomWindowSeconds(); // Get from settings (default: 10.0)
            int pointsPerSecond = (int) Math.round(1.0 / delta);
            int halfWindow = (int) Math.round(windowSeconds * pointsPerSecond);
            int windowSize = halfWindow * 2; // Total points to show
            
            int startIndex = Math.max(0, itemIndex - halfWindow);
            int endIndex = Math.min(normalizedSeries.getItemCount() - 1, itemIndex + halfWindow);
            
            // Create zoomed time series
            TimeSeries zoomSeries = new TimeSeries("zoom");
            for (int i = startIndex; i <= endIndex; i++) {
                zoomSeries.add(normalizedSeries.getTimePeriod(i), normalizedSeries.getValue(i).doubleValue());
            }
            
            TimeSeriesCollection zoomDataset = new TimeSeriesCollection();
            zoomDataset.addSeries(zoomSeries);
            
            // Create zoom chart
            JFreeChart zoomChart = ChartFactory.createTimeSeriesChart(
                null,
                "Time (Zoomed)",
                "Amplitude",
                zoomDataset,
                false,
                false,
                false);
            
            XYPlot zoomPlot = (XYPlot) zoomChart.getPlot();
            zoomPlot.setDomainPannable(false);
            zoomPlot.setRangePannable(false);
            
            // Color coding based on component
            String component = waveform.getHeader().getKcmpnm().trim();
            Color seriesColor;
            if (component.contains("Z") || component.contains("U") || component.contains("V")) {
                seriesColor = Color.BLUE;
            } else if (component.contains("X") || component.contains("Y") || component.contains("H")) {
                seriesColor = Color.BLACK;
            } else {
                seriesColor = Color.RED;
            }
            zoomPlot.getRenderer().setSeriesPaint(0, seriesColor);
            
            // Add annotations for P and S picks
            String stationName = waveform.getHeader().getKstnm().trim();
            
            // Get time range of zoom window
            RegularTimePeriod startTime = normalizedSeries.getTimePeriod(startIndex);
            RegularTimePeriod endTime = normalizedSeries.getTimePeriod(endIndex);
            long startMillis = startTime.getFirstMillisecond();
            long endMillis = endTime.getFirstMillisecond();
            
            // Add P pick annotation if it's within the zoom window
            String pKey = stationName + "_P";
            if (arrivalTimeManager.containsArrivalTime(pKey)) {
                com.treloc.xtreloc.app.gui.service.ArrivalTime arrivalTime = 
                    arrivalTimeManager.getArrivalTimeMap().get(pKey);
                long pickMillis = arrivalTime.getArrivalTime().getTime();
                if (pickMillis >= startMillis && pickMillis <= endMillis) {
                    zoomPlot.addAnnotation(new XYLineAnnotation(
                        pickMillis, zoomPlot.getRangeAxis().getRange().getLowerBound(),
                        pickMillis, zoomPlot.getRangeAxis().getRange().getUpperBound(),
                        new BasicStroke(2.0f), Color.MAGENTA), true);
                }
            }
            
            // Add S pick annotation if it's within the zoom window
            String sKey = stationName + "_S";
            if (arrivalTimeManager.containsArrivalTime(sKey)) {
                com.treloc.xtreloc.app.gui.service.ArrivalTime arrivalTime = 
                    arrivalTimeManager.getArrivalTimeMap().get(sKey);
                long pickMillis = arrivalTime.getArrivalTime().getTime();
                if (pickMillis >= startMillis && pickMillis <= endMillis) {
                    zoomPlot.addAnnotation(new XYLineAnnotation(
                        pickMillis, zoomPlot.getRangeAxis().getRange().getLowerBound(),
                        pickMillis, zoomPlot.getRangeAxis().getRange().getUpperBound(),
                        new BasicStroke(2.0f), Color.CYAN), true);
                }
            }
            
            // Add vertical line at the selected point
            RegularTimePeriod selectedTime = normalizedSeries.getTimePeriod(itemIndex);
            long selectedMillis = selectedTime.getFirstMillisecond();
            zoomPlot.addAnnotation(new XYLineAnnotation(
                selectedMillis, zoomPlot.getRangeAxis().getRange().getLowerBound(),
                selectedMillis, zoomPlot.getRangeAxis().getRange().getUpperBound(),
                new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f),
                Color.GRAY), true);
            
            ChartPanel zoomChartPanelComponent = new ChartPanel(zoomChart);
            zoomChartPanelComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            zoomChartPanel.add(zoomChartPanelComponent);
            
        } catch (Exception ex) {
            // If there's an error, just clear the zoom view
            ex.printStackTrace();
        }
        
        zoomChartPanel.revalidate();
        zoomChartPanel.repaint();
    }
    
    private void updateAnnotations(XYPlot plot, String station, int index) {
        double yMin = index - 0.5;
        double yMax = index + 0.5;
        station = station.replace(" ", "");
        
                    if (arrivalTimeManager.containsArrivalTime(station + "_P")) {
            com.treloc.xtreloc.app.gui.service.ArrivalTime arrivalTime = 
                arrivalTimeManager.getArrivalTimeMap().get(station + "_P");
            double xValue = arrivalTime.getArrivalTime().getTime();
            Color annotationColor = Color.MAGENTA;
            plot.addAnnotation(new XYLineAnnotation(
                xValue, yMin, xValue, yMax,
                new BasicStroke(2.0f), annotationColor), true);
        }
        
        if (arrivalTimeManager.containsArrivalTime(station + "_S")) {
            com.treloc.xtreloc.app.gui.service.ArrivalTime arrivalTime = 
                arrivalTimeManager.getArrivalTimeMap().get(station + "_S");
            double xValue = arrivalTime.getArrivalTime().getTime();
            Color annotationColor = Color.CYAN;
            plot.addAnnotation(new XYLineAnnotation(
                xValue, yMin, xValue, yMax,
                new BasicStroke(2.0f), annotationColor), true);
        }
    }
    
    private TimeSeries normalizeTimeSeries(TimeSeries series) {
        TimeSeries normalizedSeries = new TimeSeries("normalized");
        double maxVal = 0;
        for (int i = 0; i < series.getItemCount(); i++) {
            double val = Math.abs(series.getValue(i).doubleValue());
            if (val > maxVal) {
                maxVal = val;
            }
        }
        if (maxVal == 0) {
            maxVal = 1.0;
        }
        for (int i = 0; i < series.getItemCount(); i++) {
            normalizedSeries.add(series.getTimePeriod(i), series.getValue(i).doubleValue() / (2 * maxVal));
        }
        return normalizedSeries;
    }
    
    private void selectOutputDirectory() {
        File currentDir = null;
        if (outputDir != null && !outputDir.isEmpty() && !outputDir.equals(".")) {
            File currentOutputDir = new File(outputDir);
            if (currentOutputDir.exists() && currentOutputDir.isDirectory()) {
                currentDir = currentOutputDir;
            }
        }
        
        File selectedDir = DirectoryChooserHelper.selectDirectory(this, "Select Output Directory", currentDir);
        if (selectedDir != null) {
            outputDir = selectedDir.getAbsolutePath();
            outputDirField.setText(outputDir);
            statusLabel.setText("Output directory: " + outputDir);
        }
    }
    
    private void savePicks() {
        if (outputDir == null || outputDir.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please select an output directory first",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (selectedDir == null) {
            JOptionPane.showMessageDialog(this,
                "No directory selected",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Date earliestPTime = null;
        for (com.treloc.xtreloc.app.gui.service.ArrivalTime arrival : arrivalTimeManager.getArrivalTimeMap().values()) {
            if (arrival.getPhaseDescriptor().equals("P")) {
                if (earliestPTime == null || arrival.getArrivalTime().before(earliestPTime)) {
                    earliestPTime = arrival.getArrivalTime();
                }
            }
        }
        
        if (earliestPTime == null) {
            JOptionPane.showMessageDialog(this,
                "No P-wave picks found. Cannot create obs file.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        java.time.Instant instant = earliestPTime.toInstant();
        java.time.ZonedDateTime zonedDateTime = instant.atZone(java.time.ZoneId.systemDefault());
        java.time.LocalDateTime referenceTime = zonedDateTime.toLocalDateTime();
        referenceTime = referenceTime.withSecond(0).withNano(0);
        
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyMMdd.HHmmss");
        String fileNameBase = dateFormat.format(java.util.Date.from(referenceTime.atZone(java.time.ZoneId.systemDefault()).toInstant()));
        String fileName = outputDir + "/" + fileNameBase + ".obs";
        
        arrivalTimeManager.outputToObs(fileName);
        statusLabel.setText("Picks saved to " + fileName);
        JOptionPane.showMessageDialog(this,
            "Picks saved to " + fileName,
            "Success", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void selectStationFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        if (stationFilePath != null && !stationFilePath.isEmpty()) {
            File currentFile = new File(stationFilePath);
            if (currentFile.exists() && currentFile.isFile()) {
                fileChooser.setCurrentDirectory(currentFile.getParentFile());
            }
        }
        
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            stationFilePath = selectedFile.getAbsolutePath();
            stationFileField.setText(selectedFile.getName());
            loadStationFile(stationFilePath);
            statusLabel.setText("Station file loaded: " + selectedFile.getName());
        }
    }
    
    private void loadStationFile(String filePath) {
        channelMap.clear();
        File stationFile = new File(filePath);
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(stationFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }
                
                // Parse format: station component channel_number
                // Example: ASO Z 0200
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    String station = parts[0].trim();
                    String component = parts[1].trim();
                    String channelNumber = parts[2].trim();
                    
                    // Create key: station_component
                    String key = station + "_" + component;
                    channelMap.put(key, channelNumber);
                }
            }
            System.out.println("Loaded " + channelMap.size() + " channel mappings from station file");
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this,
                "Error reading station file: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private void savePickfile() {
        if (outputDir == null || outputDir.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please select an output directory first",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (selectedDir == null) {
            JOptionPane.showMessageDialog(this,
                "No directory selected",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (stationFilePath == null || stationFilePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please select a station file first for channel mapping",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (currentFilesInDir == null || currentFilesInDir.length == 0) {
            JOptionPane.showMessageDialog(this,
                "No SAC files loaded",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        Date earliestPTime = null;
        for (com.treloc.xtreloc.app.gui.service.ArrivalTime arrival : arrivalTimeManager.getArrivalTimeMap().values()) {
            if (arrival.getPhaseDescriptor().equals("P")) {
                if (earliestPTime == null || arrival.getArrivalTime().before(earliestPTime)) {
                    earliestPTime = arrival.getArrivalTime();
                }
            }
        }
        
        if (earliestPTime == null) {
            JOptionPane.showMessageDialog(this,
                "No P-wave picks found. Cannot create pickfile.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        java.time.Instant instant = earliestPTime.toInstant();
        java.time.ZonedDateTime zonedDateTime = instant.atZone(java.time.ZoneId.systemDefault());
        java.time.LocalDateTime fileNameTime = zonedDateTime.toLocalDateTime();
        fileNameTime = fileNameTime.withSecond(0).withNano(0);
        
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyMMdd.HHmmss");
        String fileNameBase = dateFormat.format(java.util.Date.from(fileNameTime.atZone(java.time.ZoneId.systemDefault()).toInstant()));
        String fileName = outputDir + "/" + fileNameBase + ".pick";
        
        java.time.LocalDateTime referenceTime;
        try {
            java.text.SimpleDateFormat parseFormat = new java.text.SimpleDateFormat("yyMMdd.HHmmss");
            Date parsedDate = parseFormat.parse(fileNameBase);
            java.time.Instant parsedInstant = parsedDate.toInstant();
            java.time.ZonedDateTime parsedZonedDateTime = parsedInstant.atZone(java.time.ZoneId.systemDefault());
            referenceTime = parsedZonedDateTime.toLocalDateTime();
            referenceTime = referenceTime.withSecond(0).withNano(0);
        } catch (java.text.ParseException e) {
            JOptionPane.showMessageDialog(this,
                "Error parsing file name: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        SacTimeSeries firstWaveform = null;
        float delta = 0.01f;
        try {
            firstWaveform = WaveProcessor.read(currentFilesInDir[0].getAbsolutePath());
            edu.sc.seis.seisFile.sac.SacHeader hdr = firstWaveform.getHeader();
            delta = hdr.getDelta();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error reading SAC file: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        double dt = delta;
        
        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(fileName), 
                    java.nio.charset.StandardCharsets.UTF_8))) {
            
            writer.println("#p " + fileNameBase + " . EqT");
            
            writer.println(String.format("#p %02d %02d %02d %02d %02d %02d",
                referenceTime.getYear() % 100,
                referenceTime.getMonthValue(),
                referenceTime.getDayOfMonth(),
                referenceTime.getHour(),
                referenceTime.getMinute(),
                referenceTime.getSecond()));
            
            for (com.treloc.xtreloc.app.gui.service.ArrivalTime arrival : arrivalTimeManager.getArrivalTimeMap().values()) {
                String station = arrival.getStationName();
                String component = arrival.getComponent();
                String phase = arrival.getPhaseDescriptor();
                Date arrivalTime = arrival.getArrivalTime();
                
                String key = station + "_" + component;
                String channelNumber = channelMap.get(key);
                if (channelNumber == null) {
                    System.out.println("Warning: No channel mapping found for " + key);
                    continue;
                }
                
                int type = phase.equals("P") ? 0 : 1;
                
                long referenceMillis = referenceTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                long arrivalMillis = arrivalTime.getTime();
                long timeDiffMillis = arrivalMillis - referenceMillis;
                
                if (timeDiffMillis < 0) {
                    System.out.println("Warning: Arrival time is before reference time for " + station + " " + component);
                    continue;
                }
                
                double dSec = timeDiffMillis / 1000.0;
                
                int secInt = (int) Math.floor(dSec);
                double secFraction = dSec - secInt;
                int secMic = (int) Math.round(secFraction * 1000.0);
                
                double dSec2 = dSec + dt;
                int secInt2 = (int) Math.floor(dSec2);
                double secFraction2 = dSec2 - secInt2;
                int secMic2 = (int) Math.round(secFraction2 * 1000.0);
                
                int polarity = 0;
                
                writer.println(String.format("#p %s %d %d %03d %d %03d +%d",
                    channelNumber.toUpperCase(), type, secInt, secMic, secInt2, secMic2, polarity));
            }
            
            statusLabel.setText("Pickfile saved to " + fileName);
            JOptionPane.showMessageDialog(this,
                "Pickfile saved to " + fileName,
                "Success", JOptionPane.INFORMATION_MESSAGE);
                
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this,
                "Error writing pickfile: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    /**
     * Shows picking options dialog when middle button is clicked.
     * 
     * @param stationName the station name
     * @param component the component
     * @param time the time at the click position
     */
    private void showPickingOptions(String stationName, String component, Date time) {
        // Create options menu
        JPopupMenu optionsMenu = new JPopupMenu();
        
        JMenuItem pickPItem = new JMenuItem("Pick P-wave");
        pickPItem.addActionListener(e -> {
            arrivalTimeManager.updateArrivalTime(stationName, component, "P", time);
            refreshChartAnnotations();
        });
        
        JMenuItem pickSItem = new JMenuItem("Pick S-wave");
        pickSItem.addActionListener(e -> {
            arrivalTimeManager.updateArrivalTime(stationName, component, "S", time);
            refreshChartAnnotations();
        });
        
        JMenuItem removePItem = new JMenuItem("Remove P-wave");
        removePItem.addActionListener(e -> {
            arrivalTimeManager.removeArrivalTime(stationName, component, "P");
            refreshChartAnnotations();
        });
        
        JMenuItem removeSItem = new JMenuItem("Remove S-wave");
        removeSItem.addActionListener(e -> {
            arrivalTimeManager.removeArrivalTime(stationName, component, "S");
            refreshChartAnnotations();
        });
        
        optionsMenu.add(pickPItem);
        optionsMenu.add(pickSItem);
        optionsMenu.addSeparator();
        optionsMenu.add(removePItem);
        optionsMenu.add(removeSItem);
        
        // Show menu at mouse position (approximate, since we don't have exact mouse coordinates here)
        // We'll show it at the center of the chart panel
        java.awt.Point location = chartPanel.getLocationOnScreen();
        location.x += chartPanel.getWidth() / 2;
        location.y += chartPanel.getHeight() / 2;
        optionsMenu.setLocation(location);
        optionsMenu.setVisible(true);
    }
    
    /**
     * Refreshes chart annotations after picking changes.
     */
    private void refreshChartAnnotations() {
        if (currentWaveforms != null && currentWaveforms.length > 0) {
            // Trigger chart update to refresh annotations
            int[] selectedIndices = fileList.getSelectedIndices();
            if (selectedIndices.length > 0 && currentFilesInDir != null) {
                try {
                    SacTimeSeries[] waveforms = new SacTimeSeries[selectedIndices.length];
                    for (int i = 0; i < selectedIndices.length; i++) {
                        waveforms[i] = WaveProcessor.read(currentFilesInDir[selectedIndices[i]].getAbsolutePath());
                        waveforms[i] = WaveProcessor.bandPassFilter(waveforms[i], lowFreq, highFreq);
                    }
                    updateCharts(waveforms);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}
