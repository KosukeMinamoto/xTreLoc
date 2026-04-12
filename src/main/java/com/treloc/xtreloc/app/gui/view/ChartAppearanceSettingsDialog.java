package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog for configuring JFreeChart appearance settings.
 * 
 * @author xTreLoc Development Team
 */
public class ChartAppearanceSettingsDialog extends JDialog {
    private JSpinner titleFontSizeSpinner;
    private JSpinner axisLabelFontSizeSpinner;
    private JSpinner tickLabelFontSizeSpinner;
    private JSpinner lineWidthSpinner;
    private AppSettings currentSettings;
    private boolean settingsChanged = false;
    
    /**
     * Constructs a new ChartAppearanceSettingsDialog.
     * 
     * @param parent the parent window
     */
    public ChartAppearanceSettingsDialog(Window parent) {
        super(parent, "Chart Appearance Settings", ModalityType.APPLICATION_MODAL);
        this.currentSettings = AppSettings.load();
        
        initComponents();
        loadSettings();
        
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parent);
        pack();
    }
    
    /**
     * Initializes the UI components.
     */
    private void initComponents() {
        setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        JLabel titleLabel = new JLabel("JFreeChart Appearance Settings");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 10, 10, 10);
        mainPanel.add(titleLabel, gbc);
        
        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 10, 5, 10);
        
        // Title font size
        gbc.gridx = 0; gbc.gridy = 1;
        mainPanel.add(new JLabel("Title Font Size:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        titleFontSizeSpinner = new JSpinner(new SpinnerNumberModel(14, 8, 24, 1));
        mainPanel.add(titleFontSizeSpinner, gbc);
        
        // Axis label font size
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        mainPanel.add(new JLabel("Axis Label Font Size:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        axisLabelFontSizeSpinner = new JSpinner(new SpinnerNumberModel(12, 8, 20, 1));
        mainPanel.add(axisLabelFontSizeSpinner, gbc);
        
        // Tick label font size
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        mainPanel.add(new JLabel("Tick Label Font Size:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        tickLabelFontSizeSpinner = new JSpinner(new SpinnerNumberModel(10, 8, 16, 1));
        mainPanel.add(tickLabelFontSizeSpinner, gbc);
        
        // Line width
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        mainPanel.add(new JLabel("Line Width:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        lineWidthSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.5, 5.0, 0.5));
        mainPanel.add(lineWidthSpinner, gbc);
        
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> applySettings());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);
        buttonPanel.add(applyButton);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Handle window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }
    
    /**
     * Loads current settings from AppSettings and populates the UI controls.
     */
    private void loadSettings() {
        ChartAppearanceSettings chartSettings = currentSettings.getChartAppearance();
        titleFontSizeSpinner.setValue(chartSettings.getTitleFontSize());
        axisLabelFontSizeSpinner.setValue(chartSettings.getAxisLabelFontSize());
        tickLabelFontSizeSpinner.setValue(chartSettings.getTickLabelFontSize());
        lineWidthSpinner.setValue((double) chartSettings.getLineWidth());
    }
    
    /**
     * Applies the current settings and saves them.
     */
    private void applySettings() {
        ChartAppearanceSettings chartSettings = currentSettings.getChartAppearance();
        chartSettings.setTitleFontSize(((Number) titleFontSizeSpinner.getValue()).intValue());
        chartSettings.setAxisLabelFontSize(((Number) axisLabelFontSizeSpinner.getValue()).intValue());
        chartSettings.setTickLabelFontSize(((Number) tickLabelFontSizeSpinner.getValue()).intValue());
        chartSettings.setLineWidth(((Number) lineWidthSpinner.getValue()).floatValue());
        
        currentSettings.save();
        settingsChanged = true;
        
        JOptionPane.showMessageDialog(this,
            "Chart appearance settings applied. Charts will be updated on next refresh.",
            "Settings Applied",
            JOptionPane.INFORMATION_MESSAGE);
        
        dispose();
    }
    
    /**
     * Checks if settings were changed.
     * 
     * @return true if settings were changed and applied
     */
    public boolean isSettingsChanged() {
        return settingsChanged;
    }
}
