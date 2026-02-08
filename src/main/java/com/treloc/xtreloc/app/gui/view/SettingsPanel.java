package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.VersionInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Settings panel with left category list and right detail view.
 * Categories: Logging, General, Chart, Picking, About.
 */
public class SettingsPanel extends JPanel {
    private static final String CARD_LOGGING = "Logging";
    private static final String CARD_GENERAL = "General";
    private static final String CARD_CHART = "Chart";
    private static final String CARD_PICKING = "Picking";
    private static final String CARD_ABOUT = "About";

    private JComboBox<String> fontCombo;
    private JSpinner symbolSizeSpinner;
    private JComboBox<String> defaultPaletteCombo;
    private JComboBox<String> logLevelCombo;
    private JSpinner logHistorySpinner;
    private JTextField homeDirectoryField;
    private JButton browseHomeDirButton;
    private JComboBox<String> themeCombo;
    private JSpinner zoomWindowSpinner;
    private JSpinner chartTitleFontSizeSpinner;
    private JSpinner chartAxisLabelFontSizeSpinner;
    private JSpinner chartTickLabelFontSizeSpinner;
    private JSpinner chartLineWidthSpinner;
    private JTextField backgroundColorField;
    private JTextField gridlineColorField;
    private JSpinner gridlineWidthSpinner;
    private JComboBox<String> gridlineStyleCombo;
    private JTextField defaultSymbolColorField;
    private JCheckBox confirmBeforeOverwriteCheckBox;
    private JSpinner recentFilesCountSpinner;
    private JCheckBox autoUpdateCheckBox;
    private JSpinner logLimitMbSpinner;
    private JSpinner logCountSpinner;
    private JComboBox<String> pickingMousePCombo;
    private JComboBox<String> pickingMouseSCombo;
    private JComboBox<String> pickingMouseContextCombo;
    private JComboBox<String> pickingKeyPCombo;
    private JComboBox<String> pickingKeySCombo;
    private JButton applyButton;
    private MapView mapView;
    private Window parentWindow;
    private AppSettings currentSettings;
    private CardLayout cardLayout;
    private JPanel detailCardsPanel;

    public SettingsPanel(MapView mapView, Window parentWindow) {
        this.mapView = mapView;
        this.parentWindow = parentWindow;
        this.currentSettings = AppSettings.load();
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        initComponents();
        loadSettings();
    }

    private void initComponents() {
        String[] categories = {
            CARD_LOGGING,
            CARD_GENERAL,
            CARD_CHART,
            CARD_PICKING,
            CARD_ABOUT
        };

        JList<String> categoryList = new JList<>(categories);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setSelectedIndex(0);
        categoryList.setFont(categoryList.getFont().deriveFont(Font.PLAIN, 13f));
        categoryList.setFixedCellHeight(28);
        JScrollPane leftScroll = new JScrollPane(categoryList);
        leftScroll.setBorder(BorderFactory.createTitledBorder("Category"));
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(160, 0));
        leftPanel.add(leftScroll, BorderLayout.CENTER);

        cardLayout = new CardLayout();
        detailCardsPanel = new JPanel(cardLayout);

        detailCardsPanel.add(wrapCardTopLeft(buildLoggingPanel()), CARD_LOGGING);
        detailCardsPanel.add(wrapCardTopLeft(buildGeneralPanel()), CARD_GENERAL);
        detailCardsPanel.add(wrapCardTopLeft(buildChartPanel()), CARD_CHART);
        detailCardsPanel.add(wrapCardTopLeft(buildPickingPanel()), CARD_PICKING);
        detailCardsPanel.add(wrapCardTopLeft(buildAboutPanel()), CARD_ABOUT);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Details"));
        rightPanel.add(detailCardsPanel, BorderLayout.CENTER);

        applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> applySettings());
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(applyButton);
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);

        categoryList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int i = categoryList.getSelectedIndex();
            if (i >= 0 && i < categories.length) {
                cardLayout.show(detailCardsPanel, categories[i]);
            }
        });

        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);
    }

    private static JPanel wrapCardTopLeft(JPanel content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(content, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel buildLoggingPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        p.add(new JLabel("Log Level:"), gbc);
        gbc.gridx = 1;
        logLevelCombo = new JComboBox<>(new String[]{"INFO", "DEBUG", "WARNING", "SEVERE"});
        p.add(logLevelCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        p.add(new JLabel("Log History Lines:"), gbc);
        gbc.gridx = 1;
        logHistorySpinner = new JSpinner(new SpinnerNumberModel(50, 10, 1000, 10));
        p.add(logHistorySpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        p.add(new JLabel("Log file max size (MB):"), gbc);
        gbc.gridx = 1;
        logLimitMbSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 500, 1));
        p.add(logLimitMbSpinner, gbc);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        p.add(new JLabel("<html><i>Rotate log file when it reaches this size. Reduces disk usage.</i></html>"), gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 4;
        p.add(new JLabel("Rotated log files to keep:"), gbc);
        gbc.gridx = 1;
        logCountSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
        p.add(logCountSpinner, gbc);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        p.add(new JLabel("<html><i>Number of old log files to keep after rotation. Takes effect after restart.</i></html>"), gbc);

        return p;
    }

    private JPanel buildGeneralPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        p.add(new JLabel("Home Directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        homeDirectoryField = new JTextField(28);
        p.add(homeDirectoryField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        browseHomeDirButton = new JButton("Browse...");
        browseHomeDirButton.addActionListener(e -> browseHomeDirectory());
        p.add(browseHomeDirButton, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        p.add(new JLabel("UI Theme:"), gbc);
        gbc.gridx = 1;
        themeCombo = new JComboBox<>(getAvailableThemes());
        p.add(themeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        p.add(new JLabel("Confirm before overwrite:"), gbc);
        gbc.gridx = 1;
        confirmBeforeOverwriteCheckBox = new JCheckBox("Ask before overwriting files", true);
        p.add(confirmBeforeOverwriteCheckBox, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        p.add(new JLabel("Recent files count:"), gbc);
        gbc.gridx = 1;
        recentFilesCountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 50, 1));
        p.add(recentFilesCountSpinner, gbc);

        return p;
    }

    private JPanel buildChartPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        p.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1;
        fontCombo = new JComboBox<>(new String[]{"default", "Sans Serif", "Serif", "Monospaced"});
        p.add(fontCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        p.add(new JLabel("Symbol size (map):"), gbc);
        gbc.gridx = 1;
        symbolSizeSpinner = new JSpinner(new SpinnerNumberModel(10, 5, 50, 1));
        p.add(symbolSizeSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        p.add(new JLabel("Default color palette (map):"), gbc);
        gbc.gridx = 1;
        defaultPaletteCombo = new JComboBox<>(new String[]{
            "Blue to Red", "Viridis", "Plasma", "Cool to Warm", "Rainbow", "Grayscale"
        });
        p.add(defaultPaletteCombo, gbc);
        gbc.gridx = 0; gbc.gridy = 3;
        p.add(new JLabel("Default symbol color (map, hex):"), gbc);
        gbc.gridx = 1;
        defaultSymbolColorField = new JTextField(10);
        defaultSymbolColorField.setToolTipText("e.g. #000000");
        p.add(defaultSymbolColorField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        p.add(new JLabel("<html><i>— Chart / Hist / Scatter</i></html>"), gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 5;
        p.add(new JLabel("Background color (hex):"), gbc);
        gbc.gridx = 1;
        backgroundColorField = new JTextField(10);
        backgroundColorField.setToolTipText("e.g. #FFFFFF");
        p.add(backgroundColorField, gbc);
        gbc.gridx = 0; gbc.gridy = 6;
        p.add(new JLabel("Grid line color (hex):"), gbc);
        gbc.gridx = 1;
        gridlineColorField = new JTextField(10);
        p.add(gridlineColorField, gbc);
        gbc.gridx = 0; gbc.gridy = 7;
        p.add(new JLabel("Grid line width:"), gbc);
        gbc.gridx = 1;
        gridlineWidthSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.25, 5.0, 0.25));
        p.add(gridlineWidthSpinner, gbc);
        gbc.gridx = 0; gbc.gridy = 8;
        p.add(new JLabel("Grid line style:"), gbc);
        gbc.gridx = 1;
        gridlineStyleCombo = new JComboBox<>(new String[]{"solid", "dash", "dot"});
        p.add(gridlineStyleCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 9;
        p.add(new JLabel("Title font size:"), gbc);
        gbc.gridx = 1;
        chartTitleFontSizeSpinner = new JSpinner(new SpinnerNumberModel(14, 8, 24, 1));
        p.add(chartTitleFontSizeSpinner, gbc);
        gbc.gridx = 0; gbc.gridy = 10;
        p.add(new JLabel("Axis label font size:"), gbc);
        gbc.gridx = 1;
        chartAxisLabelFontSizeSpinner = new JSpinner(new SpinnerNumberModel(12, 8, 20, 1));
        p.add(chartAxisLabelFontSizeSpinner, gbc);
        gbc.gridx = 0; gbc.gridy = 11;
        p.add(new JLabel("Tick label font size:"), gbc);
        gbc.gridx = 1;
        chartTickLabelFontSizeSpinner = new JSpinner(new SpinnerNumberModel(10, 8, 16, 1));
        p.add(chartTickLabelFontSizeSpinner, gbc);
        gbc.gridx = 0; gbc.gridy = 12;
        p.add(new JLabel("Line width:"), gbc);
        gbc.gridx = 1;
        chartLineWidthSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.5, 5.0, 0.5));
        p.add(chartLineWidthSpinner, gbc);
        return p;
    }

    private static final String[] MOUSE_BUTTONS = { "Left", "Right", "Middle" };
    private static final String[] PICKING_KEYS = { "None", "P", "S", "1", "2" };

    private JPanel buildPickingPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        p.add(new JLabel("Zoom Window (seconds):"), gbc);
        gbc.gridx = 1;
        zoomWindowSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.1, 60.0, 0.5));
        p.add(zoomWindowSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        p.add(new JLabel("<html><i>Half-width of zoom window in waveform picking (±seconds).</i></html>"), gbc);

        gbc.gridy = 2; gbc.gridwidth = 1;
        p.add(new JLabel("P wave — mouse button:"), gbc);
        gbc.gridx = 1;
        pickingMousePCombo = new JComboBox<>(MOUSE_BUTTONS);
        p.add(pickingMousePCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        p.add(new JLabel("S wave — mouse button:"), gbc);
        gbc.gridx = 1;
        pickingMouseSCombo = new JComboBox<>(MOUSE_BUTTONS);
        p.add(pickingMouseSCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        p.add(new JLabel("Context menu — mouse button:"), gbc);
        gbc.gridx = 1;
        pickingMouseContextCombo = new JComboBox<>(MOUSE_BUTTONS);
        p.add(pickingMouseContextCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        p.add(new JLabel("P wave — key (hold + click):"), gbc);
        gbc.gridx = 1;
        pickingKeyPCombo = new JComboBox<>(PICKING_KEYS);
        p.add(pickingKeyPCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        p.add(new JLabel("S wave — key (hold + click):"), gbc);
        gbc.gridx = 1;
        pickingKeySCombo = new JComboBox<>(PICKING_KEYS);
        p.add(pickingKeySCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        p.add(new JLabel("<html><i>Mouse: which button picks P/S or opens context menu. Key: hold key then click to pick P or S.</i></html>"), gbc);
        return p;
    }

    private JPanel buildAboutPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel titleLabel = new JLabel(VersionInfo.getApplicationName());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        p.add(titleLabel, gbc);
        gbc.gridy = 1;
        p.add(new JLabel("Version " + VersionInfo.getVersion()), gbc);
        gbc.gridy = 2;
        JTextArea infoArea = new JTextArea(
            "Earthquake Hypocenter Location Tool.\n" +
            "Grid search, double difference, triple difference, MCMC.\n\n" +
            "© 2024 K.Minamoto"
        );
        infoArea.setEditable(false);
        infoArea.setOpaque(false);
        infoArea.setFont(infoArea.getFont().deriveFont(Font.PLAIN, 11f));
        infoArea.setRows(5);
        p.add(infoArea, gbc);
        gbc.gridy = 3; gbc.gridwidth = 1;
        autoUpdateCheckBox = new JCheckBox("Check for updates on startup", true);
        p.add(autoUpdateCheckBox, gbc);
        return p;
    }

    private void loadSettings() {
        if (fontCombo != null) fontCombo.setSelectedItem(currentSettings.getFont());
        if (symbolSizeSpinner != null) symbolSizeSpinner.setValue(currentSettings.getSymbolSize());
        if (defaultPaletteCombo != null) defaultPaletteCombo.setSelectedItem(currentSettings.getDefaultPalette());
        if (logLevelCombo != null) logLevelCombo.setSelectedItem(currentSettings.getLogLevel());
        if (logHistorySpinner != null) logHistorySpinner.setValue(currentSettings.getHistoryLines());
        if (homeDirectoryField != null) homeDirectoryField.setText(currentSettings.getHomeDirectory());
        if (themeCombo != null) {
            String theme = currentSettings.getTheme();
            if (theme != null) themeCombo.setSelectedItem(theme);
        }
        if (zoomWindowSpinner != null) zoomWindowSpinner.setValue(currentSettings.getZoomWindowSeconds());
        if (confirmBeforeOverwriteCheckBox != null) confirmBeforeOverwriteCheckBox.setSelected(currentSettings.isConfirmBeforeOverwrite());
        if (recentFilesCountSpinner != null) recentFilesCountSpinner.setValue(currentSettings.getRecentFilesCount());
        if (autoUpdateCheckBox != null) autoUpdateCheckBox.setSelected(currentSettings.isAutoUpdateEnabled());
        if (logLimitMbSpinner != null) logLimitMbSpinner.setValue(currentSettings.getLogLimitBytes() / (1024 * 1024));
        if (logCountSpinner != null) logCountSpinner.setValue(currentSettings.getLogCount());
        if (pickingMousePCombo != null) pickingMousePCombo.setSelectedItem(currentSettings.getPickingMouseP());
        if (pickingMouseSCombo != null) pickingMouseSCombo.setSelectedItem(currentSettings.getPickingMouseS());
        if (pickingMouseContextCombo != null) pickingMouseContextCombo.setSelectedItem(currentSettings.getPickingMouseContext());
        if (pickingKeyPCombo != null) pickingKeyPCombo.setSelectedItem(currentSettings.getPickingKeyP());
        if (pickingKeySCombo != null) pickingKeySCombo.setSelectedItem(currentSettings.getPickingKeyS());
        com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings chart = currentSettings.getChartAppearance();
        if (chartTitleFontSizeSpinner != null) chartTitleFontSizeSpinner.setValue(chart.getTitleFontSize());
        if (chartAxisLabelFontSizeSpinner != null) chartAxisLabelFontSizeSpinner.setValue(chart.getAxisLabelFontSize());
        if (chartTickLabelFontSizeSpinner != null) chartTickLabelFontSizeSpinner.setValue(chart.getTickLabelFontSize());
        if (chartLineWidthSpinner != null) chartLineWidthSpinner.setValue((double) chart.getLineWidth());
        if (backgroundColorField != null) backgroundColorField.setText(chart.getBackgroundColor());
        if (gridlineColorField != null) gridlineColorField.setText(chart.getGridlineColor());
        if (gridlineWidthSpinner != null) gridlineWidthSpinner.setValue((double) chart.getGridlineWidth());
        if (gridlineStyleCombo != null) gridlineStyleCombo.setSelectedItem(chart.getGridlineStyle());
        if (defaultSymbolColorField != null) defaultSymbolColorField.setText(currentSettings.getDefaultSymbolColor());
    }

    private void browseHomeDirectory() {
        File currentDir = new File(homeDirectoryField.getText());
        if (!currentDir.exists() || !currentDir.isDirectory()) {
            currentDir = new File(System.getProperty("user.home"));
        }
        File selectedDir = com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper.selectDirectory(
            this, "Select Home Directory", currentDir);
        if (selectedDir != null) {
            homeDirectoryField.setText(selectedDir.getAbsolutePath());
        }
    }

    private void applySettings() {
        currentSettings.setFont((String) fontCombo.getSelectedItem());
        currentSettings.setSymbolSize((Integer) symbolSizeSpinner.getValue());
        currentSettings.setDefaultPalette((String) defaultPaletteCombo.getSelectedItem());
        currentSettings.setLogLevel((String) logLevelCombo.getSelectedItem());
        currentSettings.setHistoryLines((Integer) logHistorySpinner.getValue());
        currentSettings.setHomeDirectory(homeDirectoryField.getText());
        currentSettings.setTheme((String) themeCombo.getSelectedItem());
        currentSettings.setZoomWindowSeconds(((Number) zoomWindowSpinner.getValue()).doubleValue());
        currentSettings.setConfirmBeforeOverwrite(confirmBeforeOverwriteCheckBox.isSelected());
        currentSettings.setRecentFilesCount((Integer) recentFilesCountSpinner.getValue());
        currentSettings.setAutoUpdateEnabled(autoUpdateCheckBox.isSelected());
        currentSettings.setLogLimitBytes((Integer) logLimitMbSpinner.getValue() * 1024 * 1024);
        currentSettings.setLogCount((Integer) logCountSpinner.getValue());
        currentSettings.setPickingMouseP((String) pickingMousePCombo.getSelectedItem());
        currentSettings.setPickingMouseS((String) pickingMouseSCombo.getSelectedItem());
        currentSettings.setPickingMouseContext((String) pickingMouseContextCombo.getSelectedItem());
        currentSettings.setPickingKeyP((String) pickingKeyPCombo.getSelectedItem());
        currentSettings.setPickingKeyS((String) pickingKeySCombo.getSelectedItem());
        com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings chart = currentSettings.getChartAppearance();
        chart.setTitleFontSize(((Number) chartTitleFontSizeSpinner.getValue()).intValue());
        chart.setAxisLabelFontSize(((Number) chartAxisLabelFontSizeSpinner.getValue()).intValue());
        chart.setTickLabelFontSize(((Number) chartTickLabelFontSizeSpinner.getValue()).intValue());
        chart.setLineWidth(((Number) chartLineWidthSpinner.getValue()).floatValue());
        chart.setBackgroundColor(backgroundColorField.getText().trim().isEmpty() ? "#FFFFFF" : backgroundColorField.getText().trim());
        chart.setGridlineColor(gridlineColorField.getText().trim().isEmpty() ? "#E0E0E0" : gridlineColorField.getText().trim());
        chart.setGridlineWidth(((Number) gridlineWidthSpinner.getValue()).floatValue());
        chart.setGridlineStyle((String) gridlineStyleCombo.getSelectedItem());
        currentSettings.setDefaultSymbolColor(defaultSymbolColorField.getText().trim().isEmpty() ? "#000000" : defaultSymbolColorField.getText().trim());

        currentSettings.save();

        applyFont(currentSettings.getFont());
        applySymbolSize(currentSettings.getSymbolSize());
        applyDefaultPalette(currentSettings.getDefaultPalette());
        applyLogLevel(currentSettings.getLogLevel());
        applyTheme(currentSettings.getTheme());

        if (parentWindow != null) {
            SwingUtilities.invokeLater(() -> {
                SwingUtilities.updateComponentTreeUI(parentWindow);
                restoreMicrosoftStyleTabbedPanes(parentWindow);
                parentWindow.revalidate();
                parentWindow.repaint();
                updateAllComponents(parentWindow);
            });
        }

        JOptionPane.showMessageDialog(this,
            "Settings applied. Restart the application for theme changes to take full effect.",
            "Settings", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateAllComponents(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof Container) updateAllComponents((Container) comp);
            comp.revalidate();
            comp.repaint();
        }
    }

    private void restoreMicrosoftStyleTabbedPanes(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof MicrosoftStyleTabbedPane) {
                ((MicrosoftStyleTabbedPane) comp).restoreCustomUI();
            }
            if (comp instanceof Container) restoreMicrosoftStyleTabbedPanes((Container) comp);
        }
    }

    private void applyFont(String font) {
        Font selectedFont;
        switch (font) {
            case "Sans Serif": selectedFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12); break;
            case "Serif": selectedFont = new Font(Font.SERIF, Font.PLAIN, 12); break;
            case "Monospaced": selectedFont = new Font(Font.MONOSPACED, Font.PLAIN, 12); break;
            default: selectedFont = UIManager.getFont("Label.font");
        }
        UIManager.put("Label.font", selectedFont);
        UIManager.put("Button.font", selectedFont);
        UIManager.put("TextField.font", selectedFont);
        UIManager.put("ComboBox.font", selectedFont);
    }

    private void applySymbolSize(int size) {
        if (mapView != null) mapView.setSymbolSize(size);
    }

    private void applyDefaultPalette(String paletteName) {
        if (mapView != null) {
            MapView.ColorPalette palette = MapView.ColorPalette.BLUE_TO_RED;
            for (MapView.ColorPalette p : MapView.ColorPalette.values()) {
                if (p.toString().equals(paletteName)) { palette = p; break; }
            }
            mapView.setDefaultPalette(palette);
        }
    }

    private void applyLogLevel(String logLevel) {
        java.util.logging.Level level;
        switch (logLevel) {
            case "DEBUG": level = java.util.logging.Level.FINE; break;
            case "WARNING": level = java.util.logging.Level.WARNING; break;
            case "SEVERE": level = java.util.logging.Level.SEVERE; break;
            case "INFO":
            default: level = java.util.logging.Level.INFO; break;
        }
        java.util.logging.Logger.getLogger("").setLevel(level);
    }

    private String[] getAvailableThemes() {
        java.util.List<String> themes = new java.util.ArrayList<>();
        themes.add("System");
        themes.add("Metal");
        themes.add("Nimbus");
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("windows")) {
            themes.add("Windows");
            themes.add("Windows Classic");
        } else if (osName.contains("mac")) {
            themes.add("Mac OS X");
        } else if (osName.contains("linux") || osName.contains("unix")) {
            themes.add("GTK+");
        }
        return themes.toArray(new String[0]);
    }

    private void applyTheme(String themeName) {
        try {
            String lafClassName = getLookAndFeelClassName(themeName);
            if (lafClassName != null) UIManager.setLookAndFeel(lafClassName);
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ex) { }
        }
    }

    private String getLookAndFeelClassName(String themeName) {
        if (themeName == null) return UIManager.getSystemLookAndFeelClassName();
        switch (themeName) {
            case "System": return UIManager.getSystemLookAndFeelClassName();
            case "Metal": return UIManager.getCrossPlatformLookAndFeelClassName();
            case "Nimbus": return "javax.swing.plaf.nimbus.NimbusLookAndFeel";
            case "Windows": return "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
            case "Windows Classic": return "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel";
            case "Mac OS X": return "com.apple.laf.AquaLookAndFeel";
            case "GTK+": return "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
            default: return UIManager.getSystemLookAndFeelClassName();
        }
    }
}
