package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.GuiExecutionLog;
import com.treloc.xtreloc.app.gui.util.MainWindowSizePresets;
import com.treloc.xtreloc.app.gui.util.UiFonts;
import com.treloc.xtreloc.app.gui.util.VersionInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Settings panel with left category list and right detail view.
 * Categories: Logging, General, Chart, Picking, About.
 */
public class SettingsPanel extends JPanel {
    private static final Logger logger = Logger.getLogger(SettingsPanel.class.getName());
    private static final String CARD_LOGGING = "Logging";
    private static final String CARD_GENERAL = "General";
    private static final String CARD_CHART = "Chart";
    private static final String CARD_PICKING = "Picking";
    private static final String CARD_SOLVER = "Solver";
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
    private JSpinner chartConvergenceRepaintThrottleSpinner;
    private JTextField backgroundColorField;
    private JTextField gridlineColorField;
    private JSpinner gridlineWidthSpinner;
    private JComboBox<String> gridlineStyleCombo;
    private JTextField defaultSymbolColorField;
    private JCheckBox confirmBeforeOverwriteCheckBox;
    private JSpinner recentFilesCountSpinner;
    private JComboBox<String> mainWindowPresetCombo;
    private JSpinner mainWindowWidthSpinner;
    private JSpinner mainWindowHeightSpinner;
    private JLabel mainWindowWidthLabel;
    private JLabel mainWindowHeightLabel;
    private JLabel mainWindowSizeSummaryLabel;
    private JButton mainWindowFitScreenButton;
    /** Avoid preset ↔ spinner feedback loops. */
    private boolean mainWindowSizeUiSync;
    private JCheckBox autoUpdateCheckBox;
    private JSpinner logLimitMbSpinner;
    private JSpinner logCountSpinner;
    private JComboBox<String> pickingMousePCombo;
    private JComboBox<String> pickingMouseSCombo;
    private JComboBox<String> pickingMouseContextCombo;
    private JComboBox<String> pickingKeyPCombo;
    private JComboBox<String> pickingKeySCombo;
    /** Index 0 = layered 1D, 1 = TauP — labels only; values saved via {@link AppSettings#setRaytraceMethod}. */
    private JComboBox<String> raytraceMethodCombo;
    private JButton applyButton;
    private MapView mapView;
    private AppSettings currentSettings;
    private CardLayout cardLayout;
    private JPanel detailCardsPanel;

    public SettingsPanel(MapView mapView) {
        this.mapView = mapView;
        this.currentSettings = AppSettings.load();
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        AppPanelStyle.setPanelBackground(this);
        initComponents();
        loadSettings();
    }

    private void initComponents() {
        String[] categories = {
            CARD_LOGGING,
            CARD_GENERAL,
            CARD_SOLVER,
            CARD_CHART,
            CARD_PICKING,
            CARD_ABOUT
        };

        JList<String> categoryList = new JList<>(categories);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setSelectedIndex(0);
        categoryList.setFont(UiFonts.uiPlain(13f));
        categoryList.setFixedCellHeight(28);
        AppPanelStyle.styleList(categoryList);
        JScrollPane leftScroll = new JScrollPane(categoryList);
        AppPanelStyle.styleScrollPane(leftScroll);
        leftScroll.setBorder(AppPanelStyle.createTitledSectionBorder("Category"));
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(160, 0));
        AppPanelStyle.setPanelBackground(leftPanel);
        leftPanel.add(leftScroll, BorderLayout.CENTER);

        cardLayout = new CardLayout();
        detailCardsPanel = new JPanel(cardLayout);
        AppPanelStyle.setPanelBackground(detailCardsPanel);

        detailCardsPanel.add(wrapCardTopLeft(buildLoggingPanel()), CARD_LOGGING);
        detailCardsPanel.add(wrapCardTopLeft(buildGeneralPanel()), CARD_GENERAL);
        detailCardsPanel.add(wrapCardTopLeft(buildSolverPanel()), CARD_SOLVER);
        detailCardsPanel.add(wrapCardTopLeft(buildChartPanel()), CARD_CHART);
        detailCardsPanel.add(wrapCardTopLeft(buildPickingPanel()), CARD_PICKING);
        detailCardsPanel.add(wrapCardTopLeft(buildAboutPanel()), CARD_ABOUT);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(AppPanelStyle.createTitledSectionBorder("Details"));
        AppPanelStyle.setPanelBackground(rightPanel);
        rightPanel.add(detailCardsPanel, BorderLayout.CENTER);

        applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> applySettings());
        AppPanelStyle.stylePrimaryButton(applyButton);
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppPanelStyle.setPanelBackground(bottomPanel);
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

        applyContentThemeTo(detailCardsPanel);
    }

    /** Recursively apply theme (content bg/fg) to panels, text fields, combos, spinners, lists, labels, checkboxes. */
    private void applyContentThemeTo(Container c) {
        if (c == null) return;
        if (c instanceof JPanel) {
            ((JPanel) c).setBackground(AppPanelStyle.getPanelBg());
        }
        if (c instanceof JLabel) {
            ((JLabel) c).setForeground(AppPanelStyle.getContentTextColor());
        }
        if (c instanceof JCheckBox) {
            ((JCheckBox) c).setForeground(AppPanelStyle.getContentTextColor());
        }
        if (c instanceof JTextField) {
            AppPanelStyle.styleTextField((JTextField) c);
        }
        if (c instanceof JComboBox) {
            AppPanelStyle.styleComboBox((JComboBox<?>) c);
        }
        if (c instanceof JSpinner) {
            c.setBackground(AppPanelStyle.getContentBg());
            c.setForeground(AppPanelStyle.getContentTextColor());
            JComponent editor = ((JSpinner) c).getEditor();
            if (editor instanceof JSpinner.DefaultEditor) {
                JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
                AppPanelStyle.styleTextField(tf);
            }
        }
        if (c instanceof JScrollPane) {
            AppPanelStyle.styleScrollPane((JScrollPane) c);
        }
        for (Component child : c.getComponents()) {
            if (child instanceof Container) {
                applyContentThemeTo((Container) child);
            } else if (child instanceof JLabel) {
                ((JLabel) child).setForeground(AppPanelStyle.getContentTextColor());
            } else if (child instanceof JCheckBox) {
                ((JCheckBox) child).setForeground(AppPanelStyle.getContentTextColor());
            }
        }
    }

    /**
     * Keeps settings form at the top-left of the card (avoids vertical centering when the details area is tall).
     */
    private static JPanel wrapCardTopLeft(JPanel content) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(wrapper);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.weightx = 1.0;
        c.weighty = 0.0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 0);
        wrapper.add(content, c);
        c.gridy = 1;
        c.weighty = 1.0;
        wrapper.add(Box.createVerticalGlue(), c);
        return wrapper;
    }

    private JPanel buildLoggingPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(p);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;

        gbc.gridx = 0; gbc.gridy = 0;
        p.add(new JLabel("Log Level:"), gbc);
        gbc.gridx = 1;
        logLevelCombo = new JComboBox<>(new String[]{"INFO", "FINE", "FINER", "DEBUG", "WARNING", "SEVERE"});
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
        AppPanelStyle.setPanelBackground(p);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;
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

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        p.add(new JLabel("Main window size:"), gbc);
        gbc.gridy = 3;
        mainWindowPresetCombo = new JComboBox<>(MainWindowSizePresets.comboLabels());
        mainWindowPresetCombo.addActionListener(e -> onMainWindowPresetSelected());
        p.add(mainWindowPresetCombo, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4;
        mainWindowSizeSummaryLabel = new JLabel(" ");
        mainWindowSizeSummaryLabel.setFont(UiFonts.uiPlain(12f));
        p.add(mainWindowSizeSummaryLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        mainWindowWidthLabel = new JLabel("Width (px):");
        p.add(mainWindowWidthLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        mainWindowWidthSpinner = new JSpinner(new SpinnerNumberModel(1800,
            MainWindowSizePresets.WIDTH_MIN, MainWindowSizePresets.WIDTH_MAX, 10));
        p.add(mainWindowWidthSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        mainWindowHeightLabel = new JLabel("Height (px):");
        p.add(mainWindowHeightLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        mainWindowHeightSpinner = new JSpinner(new SpinnerNumberModel(850,
            MainWindowSizePresets.HEIGHT_MIN, MainWindowSizePresets.HEIGHT_MAX, 10));
        p.add(mainWindowHeightSpinner, gbc);

        int spinH = Math.max(mainWindowWidthSpinner.getPreferredSize().height,
            mainWindowHeightSpinner.getPreferredSize().height);
        Dimension spinSize = new Dimension(96, spinH);
        mainWindowWidthSpinner.setPreferredSize(spinSize);
        mainWindowHeightSpinner.setPreferredSize(spinSize);

        ChangeListener mainWinDimListener = this::onMainWindowDimensionSpinnerChanged;
        mainWindowWidthSpinner.addChangeListener(mainWinDimListener);
        mainWindowHeightSpinner.addChangeListener(mainWinDimListener);

        gbc.gridx = 0; gbc.gridy = 7;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        mainWindowFitScreenButton = new JButton("Use primary screen work area");
        mainWindowFitScreenButton.addActionListener(e -> applyPrimaryScreenWorkAreaToSpinners());
        p.add(mainWindowFitScreenButton, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 8;
        gbc.gridwidth = 4;
        p.add(new JLabel("<html><i>Choose a preset or <b>Custom</b> to edit width/height. \"Use primary screen work area\" switches to Custom. Takes effect after restart.</i></html>"), gbc);

        gbc.gridx = 0; gbc.gridy = 9;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        p.add(new JLabel("Confirm before overwrite:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        confirmBeforeOverwriteCheckBox = new JCheckBox("Ask before overwriting files", true);
        p.add(confirmBeforeOverwriteCheckBox, gbc);

        gbc.gridx = 0; gbc.gridy = 10;
        gbc.weightx = 0;
        p.add(new JLabel("Recent files count:"), gbc);
        gbc.gridx = 1;
        recentFilesCountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 50, 1));
        p.add(recentFilesCountSpinner, gbc);

        refreshMainWindowSizeUi();

        return p;
    }

    private JPanel buildSolverPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(p);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        p.add(new JLabel("Travel-time engine:"), gbc);
        gbc.gridx = 1;
        raytraceMethodCombo = new JComboBox<>(new String[] {
            "Layered 1D — fastest S (default)",
            "TauP — spherical, fastest S (tts,S)"
        });
        AppPanelStyle.styleComboBox(raytraceMethodCombo);
        p.add(raytraceMethodCombo, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        p.add(new JLabel(
            "<html><i>Same velocity file as the main window (e.g. prem.nd). "
                + "TauP mode uses finite differences for location derivatives (slower).</i></html>"), gbc);
        return p;
    }

    private void onMainWindowPresetSelected() {
        if (mainWindowSizeUiSync || mainWindowPresetCombo == null) {
            return;
        }
        int idx = mainWindowPresetCombo.getSelectedIndex();
        if (MainWindowSizePresets.isCustomComboIndex(idx)) {
            refreshMainWindowSizeUi();
            return;
        }
        mainWindowSizeUiSync = true;
        try {
            mainWindowWidthSpinner.setValue(MainWindowSizePresets.presetWidth(idx));
            mainWindowHeightSpinner.setValue(MainWindowSizePresets.presetHeight(idx));
        } finally {
            mainWindowSizeUiSync = false;
        }
        refreshMainWindowSizeUi();
    }

    private void onMainWindowDimensionSpinnerChanged(ChangeEvent e) {
        if (mainWindowSizeUiSync || mainWindowPresetCombo == null) {
            return;
        }
        mainWindowSizeUiSync = true;
        try {
            int w = ((Number) mainWindowWidthSpinner.getValue()).intValue();
            int h = ((Number) mainWindowHeightSpinner.getValue()).intValue();
            mainWindowPresetCombo.setSelectedIndex(MainWindowSizePresets.presetIndexForDimensions(w, h));
        } finally {
            mainWindowSizeUiSync = false;
        }
        refreshMainWindowSizeUi();
    }

    private void refreshMainWindowSizeUi() {
        if (mainWindowPresetCombo == null) {
            return;
        }
        int idx = mainWindowPresetCombo.getSelectedIndex();
        boolean custom = MainWindowSizePresets.isCustomComboIndex(idx);
        if (mainWindowWidthSpinner != null) {
            mainWindowWidthSpinner.setEnabled(custom);
            mainWindowHeightSpinner.setEnabled(custom);
        }
        if (mainWindowSizeSummaryLabel == null) {
            return;
        }
        if (custom) {
            int w = ((Number) mainWindowWidthSpinner.getValue()).intValue();
            int h = ((Number) mainWindowHeightSpinner.getValue()).intValue();
            mainWindowSizeSummaryLabel.setText(
                "Selected: Custom — " + w + " × " + h + " px (width/height below are editable).");
        } else {
            mainWindowSizeSummaryLabel.setText("Selected: " + MainWindowSizePresets.presetSummary(idx));
        }
    }

    private void applyPrimaryScreenWorkAreaToSpinners() {
        Rectangle r = MainWindowSizePresets.primaryScreenWorkArea();
        if (r.width < MainWindowSizePresets.WIDTH_MIN || r.height < MainWindowSizePresets.HEIGHT_MIN) {
            return;
        }
        mainWindowSizeUiSync = true;
        try {
            mainWindowWidthSpinner.setValue(MainWindowSizePresets.clampWidth(r.width));
            mainWindowHeightSpinner.setValue(MainWindowSizePresets.clampHeight(r.height));
            mainWindowPresetCombo.setSelectedIndex(MainWindowSizePresets.CUSTOM_COMBO_INDEX);
        } finally {
            mainWindowSizeUiSync = false;
        }
        refreshMainWindowSizeUi();
    }

    private JPanel buildChartPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(p);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;

        gbc.gridx = 0; gbc.gridy = 0;
        p.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1;
        fontCombo = new JComboBox<>(new String[]{"default", "Sans Serif", "Serif", "Monospaced"});
        p.add(fontCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        p.add(new JLabel("Symbol size (map):"), gbc);
        gbc.gridx = 1;
        symbolSizeSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 50, 1));
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
        gbc.gridx = 0; gbc.gridy = 13;
        p.add(new JLabel("Convergence plot repaint min interval (ms, 0=off):"), gbc);
        gbc.gridx = 1;
        chartConvergenceRepaintThrottleSpinner = new JSpinner(new SpinnerNumberModel(350, 0, 10000, 50));
        chartConvergenceRepaintThrottleSpinner.setToolTipText(
            "Solver residual / likelihood chart: minimum milliseconds between repaints. Higher = lighter GUI.");
        p.add(chartConvergenceRepaintThrottleSpinner, gbc);
        return p;
    }

    private static final String[] MOUSE_BUTTONS = { "Left", "Right", "Middle" };
    private static final String[] PICKING_KEYS = { "None", "P", "S", "1", "2" };

    private JPanel buildPickingPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(p);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;

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
        AppPanelStyle.setPanelBackground(p);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel titleLabel = new JLabel(VersionInfo.getApplicationName());
        titleLabel.setFont(UiFonts.uiBold(14f));
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
        infoArea.setFont(UiFonts.uiPlain(11f));
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
        if (mainWindowWidthSpinner != null && mainWindowHeightSpinner != null && mainWindowPresetCombo != null) {
            mainWindowSizeUiSync = true;
            try {
                mainWindowWidthSpinner.setValue(currentSettings.getMainWindowWidth());
                mainWindowHeightSpinner.setValue(currentSettings.getMainWindowHeight());
                mainWindowPresetCombo.setSelectedIndex(MainWindowSizePresets.presetIndexForDimensions(
                    currentSettings.getMainWindowWidth(), currentSettings.getMainWindowHeight()));
            } finally {
                mainWindowSizeUiSync = false;
            }
            refreshMainWindowSizeUi();
        }
        if (autoUpdateCheckBox != null) autoUpdateCheckBox.setSelected(currentSettings.isAutoUpdateEnabled());
        if (logLimitMbSpinner != null) logLimitMbSpinner.setValue(currentSettings.getLogLimitBytes() / (1024 * 1024));
        if (logCountSpinner != null) logCountSpinner.setValue(currentSettings.getLogCount());
        if (pickingMousePCombo != null) pickingMousePCombo.setSelectedItem(currentSettings.getPickingMouseP());
        if (pickingMouseSCombo != null) pickingMouseSCombo.setSelectedItem(currentSettings.getPickingMouseS());
        if (pickingMouseContextCombo != null) pickingMouseContextCombo.setSelectedItem(currentSettings.getPickingMouseContext());
        if (pickingKeyPCombo != null) pickingKeyPCombo.setSelectedItem(currentSettings.getPickingKeyP());
        if (pickingKeySCombo != null) pickingKeySCombo.setSelectedItem(currentSettings.getPickingKeyS());
        if (raytraceMethodCombo != null) {
            raytraceMethodCombo.setSelectedIndex("taup".equalsIgnoreCase(currentSettings.getRaytraceMethod()) ? 1 : 0);
        }
        com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings chart = currentSettings.getChartAppearance();
        if (chartTitleFontSizeSpinner != null) chartTitleFontSizeSpinner.setValue(chart.getTitleFontSize());
        if (chartAxisLabelFontSizeSpinner != null) chartAxisLabelFontSizeSpinner.setValue(chart.getAxisLabelFontSize());
        if (chartTickLabelFontSizeSpinner != null) chartTickLabelFontSizeSpinner.setValue(chart.getTickLabelFontSize());
        if (chartLineWidthSpinner != null) chartLineWidthSpinner.setValue((double) chart.getLineWidth());
        if (chartConvergenceRepaintThrottleSpinner != null) {
            chartConvergenceRepaintThrottleSpinner.setValue(chart.getConvergenceRepaintThrottleMs());
        }
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
        if (mainWindowWidthSpinner != null && mainWindowHeightSpinner != null) {
            currentSettings.setMainWindowWidth(((Number) mainWindowWidthSpinner.getValue()).intValue());
            currentSettings.setMainWindowHeight(((Number) mainWindowHeightSpinner.getValue()).intValue());
        }
        currentSettings.setAutoUpdateEnabled(autoUpdateCheckBox.isSelected());
        currentSettings.setLogLimitBytes((Integer) logLimitMbSpinner.getValue() * 1024 * 1024);
        currentSettings.setLogCount((Integer) logCountSpinner.getValue());
        currentSettings.setPickingMouseP((String) pickingMousePCombo.getSelectedItem());
        currentSettings.setPickingMouseS((String) pickingMouseSCombo.getSelectedItem());
        currentSettings.setPickingMouseContext((String) pickingMouseContextCombo.getSelectedItem());
        currentSettings.setPickingKeyP((String) pickingKeyPCombo.getSelectedItem());
        currentSettings.setPickingKeyS((String) pickingKeySCombo.getSelectedItem());
        if (raytraceMethodCombo != null) {
            currentSettings.setRaytraceMethod(raytraceMethodCombo.getSelectedIndex() == 1 ? "taup" : "layered");
        }
        com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings chart = currentSettings.getChartAppearance();
        chart.setTitleFontSize(((Number) chartTitleFontSizeSpinner.getValue()).intValue());
        chart.setAxisLabelFontSize(((Number) chartAxisLabelFontSizeSpinner.getValue()).intValue());
        chart.setTickLabelFontSize(((Number) chartTickLabelFontSizeSpinner.getValue()).intValue());
        chart.setLineWidth(((Number) chartLineWidthSpinner.getValue()).floatValue());
        if (chartConvergenceRepaintThrottleSpinner != null) {
            chart.setConvergenceRepaintThrottleMs(((Number) chartConvergenceRepaintThrottleSpinner.getValue()).intValue());
        }
        chart.setBackgroundColor(backgroundColorField.getText().trim().isEmpty() ? "#FFFFFF" : backgroundColorField.getText().trim());
        chart.setGridlineColor(gridlineColorField.getText().trim().isEmpty() ? "#E0E0E0" : gridlineColorField.getText().trim());
        chart.setGridlineWidth(((Number) gridlineWidthSpinner.getValue()).floatValue());
        chart.setGridlineStyle((String) gridlineStyleCombo.getSelectedItem());
        currentSettings.setDefaultSymbolColor(defaultSymbolColorField.getText().trim().isEmpty() ? "#000000" : defaultSymbolColorField.getText().trim());

        currentSettings.save();
        GuiExecutionLog.info("Settings: values saved to disk.");

        applyFont(currentSettings.getFont());
        applySymbolSize(currentSettings.getSymbolSize());
        applyDefaultPalette(currentSettings.getDefaultPalette());
        applyLogLevel(currentSettings.getLogLevel());

        GuiExecutionLog.info("Settings saved. If you changed theme, main window size, or similar, restart the application manually for those to apply.");
        logger.info("Settings saved to disk (restart manually if theme or window size changed).");
    }

    private void applyFont(String font) {
        UiFonts.applyToUIManager(font);
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
        switch (logLevel != null ? logLevel : "") {
            case "FINE":  level = java.util.logging.Level.FINE; break;
            case "FINER": level = java.util.logging.Level.FINER; break;
            case "DEBUG": level = java.util.logging.Level.FINE; break;  // legacy: DEBUG = FINE
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
            logger.log(Level.FINE, "Theme failed, falling back to system L&F: " + e.getMessage());
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                logger.log(Level.WARNING, "System LookAndFeel also failed: " + ex.getMessage());
            }
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
