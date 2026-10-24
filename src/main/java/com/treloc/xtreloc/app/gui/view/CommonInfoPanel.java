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
        
        taupFileLabel = new JLabel("Travel time: Not Set");
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
        
        SharedFileManager.getInstance().addTaupFileListener(file -> applyTaupLabel(file));
        
        applyTaupLabel(SharedFileManager.getInstance().getTaupFile());
    }
    
    /**
     * Sets the velocity structure file name and updates the display.
     * 
     * @param taupFile the velocity structure file name
     */
    public void setTaupFile(String taupFile) {
        applyTaupLabel(taupFile);
        if (taupFile != null && !taupFile.isEmpty()) {
            SharedFileManager.getInstance().setTaupFile(taupFile);
        }
    }

    private void applyTaupLabel(String file) {
        if (file == null || file.isEmpty()) {
            taupFileLabel.setText("Travel time: Not Set");
            taupFileLabel.setForeground(Color.GRAY);
            return;
        }
        boolean table = com.treloc.xtreloc.solver.table.TravelTimeTables.isTableSpec(file);
        taupFileLabel.setText((table ? "Travel-time table: " : "Velocity model: ") + file);
        taupFileLabel.setForeground(Color.BLACK);
    }
}

