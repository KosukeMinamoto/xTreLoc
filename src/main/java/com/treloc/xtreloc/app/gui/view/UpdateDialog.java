package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.service.UpdateChecker;
import com.treloc.xtreloc.app.gui.util.UpdateInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.logging.Logger;

public class UpdateDialog extends JDialog {
    private static final Logger logger = Logger.getLogger(UpdateDialog.class.getName());
    
    private UpdateInfo updateInfo;
    private JProgressBar progressBar;
    private JButton downloadButton;
    private JButton laterButton;
    private JTextArea releaseNotesArea;
    private JLabel statusLabel;
    private boolean downloadCompleted = false;
    
    public UpdateDialog(JFrame parent, UpdateInfo updateInfo) {
        super(parent, "Update Available", true);
        this.updateInfo = updateInfo;
        initComponents();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setSize(500, 400);
        setLocationRelativeTo(getParent());
        
        JPanel messagePanel = new JPanel(new BorderLayout(10, 10));
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel messageLabel = new JLabel(
            "<html><b>New version " + updateInfo.getVersion() + " is available.</b></html>"
        );
        messagePanel.add(messageLabel, BorderLayout.NORTH);
        
        if (updateInfo.getReleaseNotes() != null && !updateInfo.getReleaseNotes().isEmpty()) {
            JLabel notesLabel = new JLabel("Release Notes:");
            messagePanel.add(notesLabel, BorderLayout.CENTER);
            
            releaseNotesArea = new JTextArea(updateInfo.getReleaseNotes());
            releaseNotesArea.setEditable(false);
            releaseNotesArea.setLineWrap(true);
            releaseNotesArea.setWrapStyleWord(true);
            releaseNotesArea.setBackground(getBackground());
            JScrollPane scrollPane = new JScrollPane(releaseNotesArea);
            scrollPane.setPreferredSize(new Dimension(0, 150));
            messagePanel.add(scrollPane, BorderLayout.SOUTH);
        }
        
        add(messagePanel, BorderLayout.CENTER);
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        add(progressBar, BorderLayout.NORTH);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        laterButton = new JButton("Later");
        laterButton.addActionListener(e -> {
            dispose();
        });
        buttonPanel.add(laterButton);
        
        downloadButton = new JButton("Download Now");
        downloadButton.addActionListener(new DownloadActionListener());
        buttonPanel.add(downloadButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
        
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        add(statusLabel, BorderLayout.PAGE_END);
    }
    
    private class DownloadActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            downloadButton.setEnabled(false);
            laterButton.setEnabled(false);
            progressBar.setVisible(true);
            statusLabel.setText("Downloading...");
            
            new Thread(() -> {
                try {
                    String downloadUrl = updateInfo.getDownloadUrl();
                    if (downloadUrl == null || downloadUrl.isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Error: Download URL not available");
                            downloadButton.setEnabled(true);
                            laterButton.setEnabled(true);
                            progressBar.setVisible(false);
                            JOptionPane.showMessageDialog(
                                UpdateDialog.this,
                                "Download URL is not available for this release.\n" +
                                "Please download manually from GitHub.",
                                "Download Unavailable",
                                JOptionPane.WARNING_MESSAGE
                            );
                        });
                        return;
                    }
                    
                    File appDir = com.treloc.xtreloc.app.gui.util.AppDirectoryManager.getAppDirectory();
                    String fileExtension = downloadUrl.endsWith(".dmg") ? ".dmg" : 
                                         downloadUrl.endsWith(".app") ? ".app" : ".jar";
                    File downloadFile = new File(appDir, "xTreLoc-update-" + updateInfo.getVersion() + fileExtension);
                    
                    boolean success = UpdateChecker.downloadUpdate(
                        downloadUrl,
                        downloadFile,
                        progress -> SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(progress);
                            statusLabel.setText("Downloading... " + progress + "%");
                        })
                    );
                    
                    SwingUtilities.invokeLater(() -> {
                        if (success) {
                            progressBar.setValue(100);
                            statusLabel.setText("Download Complete!");
                            downloadCompleted = true;
                            
                            int option = JOptionPane.showConfirmDialog(
                                UpdateDialog.this,
                                "Download completed.\n" +
                                "Exit the application and start installation?",
                                "Installation Confirmation",
                                JOptionPane.YES_NO_OPTION
                            );
                            
                            if (option == JOptionPane.YES_OPTION) {
                                installUpdate(downloadFile);
                                System.exit(0);
                            } else {
                                downloadButton.setText("Downloaded");
                                laterButton.setEnabled(true);
                            }
                        } else {
                            statusLabel.setText("Download Failed");
                            downloadButton.setEnabled(true);
                            laterButton.setEnabled(true);
                        }
                        progressBar.setVisible(false);
                    });
                } catch (Exception ex) {
                    logger.severe("Error during download: " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error: " + ex.getMessage());
                        downloadButton.setEnabled(true);
                        laterButton.setEnabled(true);
                        progressBar.setVisible(false);
                    });
                }
            }).start();
        }
    }
    
    private void installUpdate(File downloadFile) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("mac")) {
                if (downloadFile.getName().endsWith(".dmg")) {
                    ProcessBuilder pb = new ProcessBuilder(
                        "open", downloadFile.getAbsolutePath()
                    );
                    pb.start();
                } else if (downloadFile.getName().endsWith(".app")) {
                    File applicationsDir = new File("/Applications");
                    File targetApp = new File(applicationsDir, "xTreLoc.app");
                    
                    if (targetApp.exists()) {
                        deleteDirectory(targetApp);
                    }
                    
                    copyDirectory(downloadFile, targetApp);
                }
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Downloaded file: " + downloadFile.getAbsolutePath() + "\n" +
                    "Please install manually.",
                    "Installation",
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
        } catch (Exception e) {
            logger.severe("Error during installation: " + e.getMessage());
            JOptionPane.showMessageDialog(
                this,
                "Error during installation: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
    
    private void copyDirectory(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }
            String[] children = source.list();
            if (children != null) {
                for (String child : children) {
                    copyDirectory(
                        new File(source, child),
                        new File(target, child)
                    );
                }
            }
        } else {
            java.nio.file.Files.copy(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
}

