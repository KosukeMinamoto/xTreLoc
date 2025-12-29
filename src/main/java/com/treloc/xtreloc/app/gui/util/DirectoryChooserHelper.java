package com.treloc.xtreloc.app.gui.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Helper class for directory selection with support for creating new directories.
 * 
 * @author K.Minamoto
 */
public class DirectoryChooserHelper {
    
    /**
     * Displays a directory selection dialog and prompts the user to create the directory
     * if it does not exist.
     * 
     * @param parent the parent component (can be null)
     * @param title the dialog title
     * @param currentDirectory the initial directory (can be null)
     * @return the selected directory, or null if cancelled
     */
    public static File selectDirectory(java.awt.Component parent, String title, File currentDirectory) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(title);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        try {
            System.setProperty("apple.awt.fileDialogForDirectories", "false");
        } catch (Exception e) {
            // Ignore on non-macOS systems
        }
        
        if (currentDirectory != null && currentDirectory.exists()) {
            fileChooser.setCurrentDirectory(currentDirectory);
        } else {
            com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
        }
        
        JButton newDirButton = new JButton("New Folder");
        newDirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createNewDirectory(fileChooser, parent);
            }
        });
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                addButtonToFileChooser(fileChooser, newDirButton);
            }
        });
        
        javax.swing.Timer timer = new javax.swing.Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addButtonToFileChooser(fileChooser, newDirButton);
            }
        });
        timer.setRepeats(true);
        timer.start();
        
        int result = fileChooser.showOpenDialog(parent);
        
        timer.stop();
        
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        
        File selectedDir = fileChooser.getSelectedFile();
        
        if (selectedDir == null) {
            return null;
        }
        
        if (!selectedDir.exists()) {
            int createResult = JOptionPane.showConfirmDialog(
                parent,
                "The selected directory does not exist.\n" +
                "Do you want to create it?\n\n" +
                "Path: " + selectedDir.getAbsolutePath(),
                "Create Directory",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (createResult == JOptionPane.YES_OPTION) {
                try {
                    if (selectedDir.mkdirs()) {
                        JOptionPane.showMessageDialog(
                            parent,
                            "Directory created successfully:\n" + selectedDir.getAbsolutePath(),
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                        return selectedDir;
                    } else {
                        JOptionPane.showMessageDialog(
                            parent,
                            "Failed to create directory:\n" + selectedDir.getAbsolutePath() +
                            "\n\nPlease check the path and permissions.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        );
                        return null;
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        parent,
                        "Error creating directory:\n" + selectedDir.getAbsolutePath() +
                        "\n\nError: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                    return null;
                }
            } else {
                return null;
            }
        }
        
        return selectedDir;
    }
    
    /**
     * Adds a button to the JFileChooser's button area.
     * 
     * @param fileChooser the file chooser to add the button to
     * @param button the button to add
     */
    private static void addButtonToFileChooser(JFileChooser fileChooser, JButton button) {
        java.awt.Container container = fileChooser;
        java.util.List<JPanel> buttonPanels = new java.util.ArrayList<>();
        findButtonPanels(container, buttonPanels);
        
        for (JPanel panel : buttonPanels) {
            boolean alreadyAdded = false;
            for (java.awt.Component comp : panel.getComponents()) {
                if (comp == button) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                int componentCount = panel.getComponentCount();
                if (componentCount > 0) {
                    panel.add(button, componentCount - 1);
                } else {
                    panel.add(button);
                }
                panel.revalidate();
                panel.repaint();
                break;
            }
        }
    }
    
    /**
     * Recursively searches for button panels within a container.
     * 
     * @param container the container to search
     * @param buttonPanels the list to add found button panels to
     */
    private static void findButtonPanels(java.awt.Container container, java.util.List<JPanel> buttonPanels) {
        for (java.awt.Component comp : container.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                java.awt.LayoutManager layout = panel.getLayout();
                if (layout instanceof FlowLayout || layout instanceof javax.swing.BoxLayout) {
                    for (java.awt.Component child : panel.getComponents()) {
                        if (child instanceof JButton) {
                            JButton btn = (JButton) child;
                            String text = btn.getText();
                            if (text != null && (text.equals("Cancel") || text.equals("Open") || 
                                text.equals("Choose") || text.equals("Save"))) {
                                buttonPanels.add(panel);
                                break;
                            }
                        }
                    }
                }
            }
            if (comp instanceof java.awt.Container) {
                findButtonPanels((java.awt.Container) comp, buttonPanels);
            }
        }
    }
    
    /**
     * Creates a new directory in the current directory of the file chooser.
     * 
     * @param fileChooser the file chooser
     * @param parent the parent component for dialogs
     */
    private static void createNewDirectory(JFileChooser fileChooser, java.awt.Component parent) {
        File currentDir = fileChooser.getCurrentDirectory();
        if (currentDir == null || !currentDir.exists()) {
            currentDir = new File(System.getProperty("user.dir"));
        }
        
        String dirName = JOptionPane.showInputDialog(
            parent,
            "Enter new directory name:",
            "New Folder",
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (dirName == null || dirName.trim().isEmpty()) {
            return;
        }
        
        dirName = dirName.trim();
        File newDir = new File(currentDir, dirName);
        
        if (newDir.exists()) {
            JOptionPane.showMessageDialog(
                parent,
                "Directory already exists:\n" + newDir.getAbsolutePath(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        
        try {
            if (newDir.mkdirs()) {
                fileChooser.setCurrentDirectory(currentDir);
                fileChooser.rescanCurrentDirectory();
                fileChooser.setSelectedFile(newDir);
                
                JOptionPane.showMessageDialog(
                    parent,
                    "Directory created:\n" + newDir.getAbsolutePath(),
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(
                    parent,
                    "Failed to create directory:\n" + newDir.getAbsolutePath() +
                    "\n\nPlease check the path and permissions.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                parent,
                "Error creating directory:\n" + newDir.getAbsolutePath() +
                "\n\nError: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * Displays a directory selection dialog (simplified version without initial directory).
     * 
     * @param parent the parent component (can be null)
     * @param title the dialog title
     * @return the selected directory, or null if cancelled
     */
    public static File selectDirectory(java.awt.Component parent, String title) {
        return selectDirectory(parent, title, null);
    }
}

