package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.VersionInfo;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Settings panel for configuring application preferences.
 * Provides UI controls for font selection, symbol size, color palette, log level, and log history settings.
 * 
 * @author xTreLoc Development Team
 */
public class SettingsPanel extends JPanel {
    private JComboBox<String> fontCombo;
    private JSpinner symbolSizeSpinner;
    private JComboBox<String> defaultPaletteCombo;
    private JComboBox<String> logLevelCombo;
    private JSpinner logHistorySpinner;
    private JTextField homeDirectoryField;
    private JButton browseHomeDirButton;
    private JComboBox<String> themeCombo;
    private JSpinner zoomWindowSpinner;
    private JButton applyButton;
    private MapView mapView;
    private Window parentWindow;
    private AppSettings currentSettings;
    
    /**
     * Constructs a new SettingsPanel.
     * 
     * @param mapView the MapView instance to configure
     * @param parentWindow the parent window containing this panel
     */
    public SettingsPanel(MapView mapView, Window parentWindow) {
        this.mapView = mapView;
        this.parentWindow = parentWindow;
        this.currentSettings = AppSettings.load();
        
        initComponents();
        loadSettings();
    }
    
    /**
     * Initializes the UI components for the settings panel.
     * Creates controls for font, symbol size, default color palette, log level, and log history.
     */
    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Visualization Settings
        JLabel visualizationLabel = new JLabel("Visualization Settings");
        visualizationLabel.setFont(visualizationLabel.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(5, 5, 5, 5);
        add(visualizationLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 1;
        add(new JLabel("Font:"), gbc);
        gbc.gridx = 1;
        fontCombo = new JComboBox<>(new String[]{"default", "Sans Serif", "Serif", "Monospaced"});
        add(fontCombo, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2;
        add(new JLabel("Symbol Size:"), gbc);
        gbc.gridx = 1;
        symbolSizeSpinner = new JSpinner(new SpinnerNumberModel(10, 5, 50, 1));
        add(symbolSizeSpinner, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3;
        add(new JLabel("Default Color Palette:"), gbc);
        gbc.gridx = 1;
        defaultPaletteCombo = new JComboBox<>(new String[]{
            "Blue to Red", "Viridis", "Plasma", "Cool to Warm", "Rainbow", "Grayscale"
        });
        add(defaultPaletteCombo, gbc);
        
        // Separator for Logging settings
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(15, 5, 5, 5);
        JSeparator loggingSeparator = new JSeparator();
        add(loggingSeparator, gbc);
        
        // Logging Settings
        JLabel loggingLabel = new JLabel("Logging Settings");
        loggingLabel.setFont(loggingLabel.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(5, 5, 5, 5);
        add(loggingLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy = 6;
        gbc.gridwidth = 1;
        add(new JLabel("Log Level:"), gbc);
        gbc.gridx = 1;
        logLevelCombo = new JComboBox<>(new String[]{"INFO", "DEBUG", "WARNING", "SEVERE"});
        add(logLevelCombo, gbc);
        
        gbc.gridx = 0; gbc.gridy = 7;
        add(new JLabel("Log History Lines:"), gbc);
        gbc.gridx = 1;
        logHistorySpinner = new JSpinner(new SpinnerNumberModel(50, 10, 1000, 10));
        add(logHistorySpinner, gbc);
        
        // Separator for General settings
        gbc.gridx = 0; gbc.gridy = 8;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(15, 5, 5, 5);
        JSeparator generalSeparator = new JSeparator();
        add(generalSeparator, gbc);
        
        // General Settings
        JLabel generalLabel = new JLabel("General Settings");
        generalLabel.setFont(generalLabel.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0; gbc.gridy = 9;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(5, 5, 5, 5);
        add(generalLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy = 10;
        gbc.gridwidth = 1;
        add(new JLabel("Home Directory:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        homeDirectoryField = new JTextField(30);
        add(homeDirectoryField, gbc);
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        browseHomeDirButton = new JButton("Browse...");
        browseHomeDirButton.addActionListener(e -> browseHomeDirectory());
        add(browseHomeDirButton, gbc);
        
        gbc.gridx = 0; gbc.gridy = 11;
        gbc.gridwidth = 1;
        add(new JLabel("UI Theme:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        themeCombo = new JComboBox<>(getAvailableThemes());
        add(themeCombo, gbc);
        
        // Separator for Picking settings
        gbc.gridx = 0; gbc.gridy = 12;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(15, 5, 5, 5);
        JSeparator pickingSeparator = new JSeparator();
        add(pickingSeparator, gbc);
        
        JLabel pickingLabel = new JLabel("Picking Settings");
        pickingLabel.setFont(pickingLabel.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0; gbc.gridy = 13;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(5, 5, 5, 5);
        add(pickingLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy = 14;
        gbc.gridwidth = 1;
        add(new JLabel("Zoom Window (seconds):"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        zoomWindowSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.1, 60.0, 0.5));
        add(zoomWindowSpinner, gbc);
        
        gbc.gridx = 0; gbc.gridy = 15;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> applySettings());
        add(applyButton, gbc);
        
        // Separator for About section
        gbc.gridx = 0; gbc.gridy = 16;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(15, 5, 5, 5);
        JSeparator aboutSeparator = new JSeparator();
        add(aboutSeparator, gbc);
        
        // Version information
        gbc.gridx = 0; gbc.gridy = 17;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 5, 5, 5);
        JLabel versionLabel = new JLabel("Version: " + VersionInfo.getVersion());
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 10f));
        add(versionLabel, gbc);
        
        // About button
        gbc.gridx = 0; gbc.gridy = 18;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(5, 5, 5, 5);
        JButton aboutButton = new JButton("About");
        aboutButton.addActionListener(e -> {
            AboutDialog dialog = new AboutDialog(
                parentWindow instanceof JFrame ? (JFrame) parentWindow : null
            );
            dialog.setVisible(true);
        });
        add(aboutButton, gbc);
    }
    
    /**
     * Loads current settings from AppSettings and populates the UI controls.
     */
    private void loadSettings() {
        fontCombo.setSelectedItem(currentSettings.getFont());
        symbolSizeSpinner.setValue(currentSettings.getSymbolSize());
        defaultPaletteCombo.setSelectedItem(currentSettings.getDefaultPalette());
        logLevelCombo.setSelectedItem(currentSettings.getLogLevel());
        logHistorySpinner.setValue(currentSettings.getHistoryLines());
        homeDirectoryField.setText(currentSettings.getHomeDirectory());
        String theme = currentSettings.getTheme();
        if (theme != null) {
            themeCombo.setSelectedItem(theme);
        }
        zoomWindowSpinner.setValue(currentSettings.getZoomWindowSeconds());
    }
    
    /**
     * Opens a directory chooser to select the home directory.
     */
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
    
    /**
     * Applies the current settings to the application.
     * Updates AppSettings, saves to file, and refreshes the UI.
     */
    private void applySettings() {
        currentSettings.setFont((String) fontCombo.getSelectedItem());
        currentSettings.setSymbolSize((Integer) symbolSizeSpinner.getValue());
        currentSettings.setDefaultPalette((String) defaultPaletteCombo.getSelectedItem());
        currentSettings.setLogLevel((String) logLevelCombo.getSelectedItem());
        currentSettings.setHistoryLines((Integer) logHistorySpinner.getValue());
        currentSettings.setHomeDirectory(homeDirectoryField.getText());
        currentSettings.setTheme((String) themeCombo.getSelectedItem());
        currentSettings.setZoomWindowSeconds(((Number) zoomWindowSpinner.getValue()).doubleValue());
        
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
            "Settings applied. Please restart the application for theme changes to take full effect.",
            "Settings",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Recursively updates all components in the container tree.
     * 
     * @param container the root container to update
     */
    private void updateAllComponents(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof Container) {
                updateAllComponents((Container) comp);
            }
            comp.revalidate();
            comp.repaint();
        }
    }
    
    /**
     * Recursively finds and restores custom UI for all MicrosoftStyleTabbedPane instances.
     * This is necessary after updateComponentTreeUI() which resets custom UIs.
     * 
     * @param container the root container to search
     */
    private void restoreMicrosoftStyleTabbedPanes(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof com.treloc.xtreloc.app.gui.view.MicrosoftStyleTabbedPane) {
                ((com.treloc.xtreloc.app.gui.view.MicrosoftStyleTabbedPane) comp).restoreCustomUI();
            }
            if (comp instanceof Container) {
                restoreMicrosoftStyleTabbedPanes((Container) comp);
            }
        }
    }
    
    /**
     * Applies the selected font to all UI components.
     * 
     * @param font the font name to apply ("default", "Sans Serif", "Serif", or "Monospaced")
     */
    private void applyFont(String font) {
        Font selectedFont;
        switch (font) {
            case "Sans Serif":
                selectedFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
                break;
            case "Serif":
                selectedFont = new Font(Font.SERIF, Font.PLAIN, 12);
                break;
            case "Monospaced":
                selectedFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
                break;
            default:
                selectedFont = UIManager.getFont("Label.font");
        }
        UIManager.put("Label.font", selectedFont);
        UIManager.put("Button.font", selectedFont);
        UIManager.put("TextField.font", selectedFont);
        UIManager.put("ComboBox.font", selectedFont);
    }
    
    /**
     * Applies the symbol size to the map view.
     * 
     * @param size the symbol size (5-50 pixels)
     */
    private void applySymbolSize(int size) {
        if (mapView != null) {
            mapView.setSymbolSize(size);
        }
    }
    
    /**
     * Applies the default color palette to the map view.
     * Converts the palette display name to the corresponding enum value.
     * 
     * @param paletteName the palette display name ("Blue to Red", "Viridis", "Plasma", etc.)
     */
    private void applyDefaultPalette(String paletteName) {
        if (mapView != null) {
            MapView.ColorPalette palette = MapView.ColorPalette.BLUE_TO_RED;
            for (MapView.ColorPalette p : MapView.ColorPalette.values()) {
                if (p.toString().equals(paletteName)) {
                    palette = p;
                    break;
                }
            }
            mapView.setDefaultPalette(palette);
        }
    }
    
    /**
     * Applies the log level to the application logger.
     * 
     * @param logLevel the log level ("INFO", "DEBUG", "WARNING", or "SEVERE")
     */
    private void applyLogLevel(String logLevel) {
        java.util.logging.Level level;
        switch (logLevel) {
            case "DEBUG":
                level = java.util.logging.Level.FINE;
                break;
            case "WARNING":
                level = java.util.logging.Level.WARNING;
                break;
            case "SEVERE":
                level = java.util.logging.Level.SEVERE;
                break;
            case "INFO":
            default:
                level = java.util.logging.Level.INFO;
                break;
        }
        java.util.logging.Logger.getLogger("").setLevel(level);
    }
    
    /**
     * Gets the list of available UI themes.
     * 
     * @return array of available theme names
     */
    private String[] getAvailableThemes() {
        java.util.List<String> themes = new java.util.ArrayList<>();
        themes.add("System");
        
        // FlatLaf themes (modern, JavaFX-like design)
        themes.add("FlatLaf Light");
        themes.add("FlatLaf Dark");
        themes.add("FlatLaf IntelliJ");
        themes.add("FlatLaf Darcula");
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            themes.add("FlatLaf macOS Light");
            themes.add("FlatLaf macOS Dark");
        }
        
        // Standard Swing themes
        themes.add("Metal");
        themes.add("Nimbus");
        
        // OS-specific themes
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
    
    /**
     * Applies the selected UI theme to the application.
     * 
     * @param themeName the theme name to apply
     */
    private void applyTheme(String themeName) {
        try {
            // Standard Look and Feel themes
            String lafClassName = getLookAndFeelClassName(themeName);
            if (lafClassName != null) {
                UIManager.setLookAndFeel(lafClassName);
            }
        } catch (Exception e) {
            System.err.println("Failed to apply theme: " + themeName + " - " + e.getMessage());
            try {
                // Fallback to system default
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                System.err.println("Failed to apply system theme: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Gets the Look and Feel class name for the given theme name.
     * 
     * @param themeName the theme name
     * @return the Look and Feel class name, or null if not found
     */
    private String getLookAndFeelClassName(String themeName) {
        if (themeName == null) {
            return UIManager.getSystemLookAndFeelClassName();
        }
        
        switch (themeName) {
            case "System":
                return UIManager.getSystemLookAndFeelClassName();
            case "FlatLaf Light":
                return "com.formdev.flatlaf.FlatLightLaf";
            case "FlatLaf Dark":
                return "com.formdev.flatlaf.FlatDarkLaf";
            case "FlatLaf IntelliJ":
                return "com.formdev.flatlaf.FlatIntelliJLaf";
            case "FlatLaf Darcula":
                return "com.formdev.flatlaf.FlatDarculaLaf";
            case "FlatLaf macOS Light":
                return "com.formdev.flatlaf.themes.FlatMacLightLaf";
            case "FlatLaf macOS Dark":
                return "com.formdev.flatlaf.themes.FlatMacDarkLaf";
            case "Metal":
                return UIManager.getCrossPlatformLookAndFeelClassName();
            case "Nimbus":
                return "javax.swing.plaf.nimbus.NimbusLookAndFeel";
            case "Windows":
                return "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
            case "Windows Classic":
                return "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel";
            case "Mac OS X":
                return "com.apple.laf.AquaLookAndFeel";
            case "GTK+":
                return "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
            default:
                return UIManager.getSystemLookAndFeelClassName();
        }
    }
}

