package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.VersionInfo;

import javax.swing.*;
import java.awt.*;

/**
 * About dialog for displaying application version and information.
 * 
 * @author K.Minamoto
 */
public class AboutDialog extends JDialog {
    
    public AboutDialog(JFrame parent) {
        super(parent, "About xTreLoc", true);
        initComponents();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setSize(400, 250);
        setLocationRelativeTo(getParent());
        setResizable(false);
        
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Application name and version
        JLabel titleLabel = new JLabel(VersionInfo.getApplicationName());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel versionLabel = new JLabel("Version " + VersionInfo.getVersion());
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 12f));
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JPanel titlePanel = new JPanel(new BorderLayout(5, 5));
        titlePanel.add(titleLabel, BorderLayout.NORTH);
        titlePanel.add(versionLabel, BorderLayout.CENTER);
        
        contentPanel.add(titlePanel, BorderLayout.NORTH);
        
        // Additional information
        JTextArea infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setOpaque(false);
        infoArea.setFont(infoArea.getFont().deriveFont(Font.PLAIN, 11f));
        infoArea.setText(
            "Earthquake Hypocenter Location Tool\n\n" +
            "A tool for locating earthquake hypocenters using\n" +
            "various methods including grid search, double difference,\n" +
            "triple difference, and MCMC.\n\n" +
            "© 2024 K.Minamoto"
        );
        infoArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        infoPanel.add(infoArea);
        contentPanel.add(infoPanel, BorderLayout.CENTER);
        
        // Close button
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(closeButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(contentPanel, BorderLayout.CENTER);
    }
}

