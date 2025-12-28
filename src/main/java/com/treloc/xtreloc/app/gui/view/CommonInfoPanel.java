package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.SharedFileManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Panel for displaying common information (station file and velocity structure).
 * 
 * @author K.Minamoto
 */
public class CommonInfoPanel extends JPanel {
    private JLabel stationFileLabel;
    private JLabel taupFileLabel;
    
    /**
     * Constructs a new CommonInfoPanel and initializes the UI components.
     */
    public CommonInfoPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setBorder(BorderFactory.createTitledBorder("Common Settings"));
        
        stationFileLabel = new JLabel("Station File: Not Selected");
        stationFileLabel.setForeground(Color.GRAY);
        add(stationFileLabel);
        
        add(Box.createHorizontalStrut(20));
        
        taupFileLabel = new JLabel("Velocity Structure: Not Set");
        taupFileLabel.setForeground(Color.GRAY);
        add(taupFileLabel);
        
        SharedFileManager.getInstance().addStationFileListener(file -> {
            if (file != null) {
                stationFileLabel.setText("Station File: " + file.getName());
                stationFileLabel.setForeground(Color.BLACK);
            } else {
                stationFileLabel.setText("Station File: Not Selected");
                stationFileLabel.setForeground(Color.GRAY);
            }
        });
        
        File existingFile = SharedFileManager.getInstance().getStationFile();
        if (existingFile != null && existingFile.exists()) {
            stationFileLabel.setText("Station File: " + existingFile.getName());
            stationFileLabel.setForeground(Color.BLACK);
        }
        
        SharedFileManager.getInstance().addTaupFileListener(file -> {
            if (file != null && !file.isEmpty()) {
                taupFileLabel.setText("Velocity Structure: " + file);
                taupFileLabel.setForeground(Color.BLACK);
            } else {
                taupFileLabel.setText("Velocity Structure: Not Set");
                taupFileLabel.setForeground(Color.GRAY);
            }
        });
        
        String existingTaupFile = SharedFileManager.getInstance().getTaupFile();
        if (existingTaupFile != null && !existingTaupFile.isEmpty()) {
            taupFileLabel.setText("Velocity Structure: " + existingTaupFile);
            taupFileLabel.setForeground(Color.BLACK);
        }
    }
    
    /**
     * Sets the velocity structure file name and updates the display.
     * 
     * @param taupFile the velocity structure file name
     */
    public void setTaupFile(String taupFile) {
        if (taupFile != null && !taupFile.isEmpty()) {
            taupFileLabel.setText("Velocity Structure: " + taupFile);
            taupFileLabel.setForeground(Color.BLACK);
            SharedFileManager.getInstance().setTaupFile(taupFile);
        } else {
            taupFileLabel.setText("Velocity Structure: Not Set");
            taupFileLabel.setForeground(Color.GRAY);
        }
    }
}

