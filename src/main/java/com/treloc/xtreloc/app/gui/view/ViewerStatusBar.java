package com.treloc.xtreloc.app.gui.view;

import javax.swing.*;
import java.awt.*;

/**
 * Status for the Viewer tab: when a root frame is set, shows a full-window loading overlay
 * (dimmed screen, input blocked). Otherwise falls back to a small status bar in the map corner.
 * Use startLoading(message) / stopLoading() to show or hide.
 */
public class ViewerStatusBar extends JPanel {
    private static ViewerStatusBar instance;
    private static JFrame rootFrame;
    private static LoadingOverlay loadingOverlay;

    private final JLabel messageLabel;
    private final JProgressBar progressBar;

    public ViewerStatusBar() {
        setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        messageLabel = new JLabel(" ");
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 11f));
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(120, 14));
        progressBar.setVisible(false);
        add(messageLabel);
        add(progressBar);
        setVisible(false);
    }

    /**
     * Sets the root frame for the full-window loading overlay.
     * When set, loading is shown as a dimmed overlay over the whole window instead of in the map.
     * Call from XTreLocGUI after creating the main frame.
     */
    public static void setRootFrame(JFrame frame) {
        rootFrame = frame;
        if (frame != null && loadingOverlay == null) {
            loadingOverlay = new LoadingOverlay();
            frame.getRootPane().setGlassPane(loadingOverlay);
            loadingOverlay.setVisible(false);
        }
    }

    /**
     * Sets the shared instance (called from MapView when building the Viewer).
     */
    public static void setInstance(ViewerStatusBar bar) {
        instance = bar;
    }

    /**
     * Returns the shared instance, or null if not set.
     */
    public static ViewerStatusBar getInstance() {
        return instance;
    }

    /**
     * Shows loading state with the given message. Safe to call from any thread.
     * If a root frame was set, shows a full-window dimmed overlay; otherwise shows the small status bar in the map.
     */
    public static void startLoading(String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            doStartLoadingOnEdt(message);
        } else {
            SwingUtilities.invokeLater(() -> doStartLoadingOnEdt(message));
        }
    }

    /**
     * Hides loading state. Safe to call from any thread.
     */
    public static void stopLoading() {
        if (SwingUtilities.isEventDispatchThread()) {
            doStopLoadingOnEdt();
        } else {
            SwingUtilities.invokeLater(ViewerStatusBar::doStopLoadingOnEdt);
        }
    }

    private static void doStartLoadingOnEdt(String message) {
        if (rootFrame != null && loadingOverlay != null) {
            loadingOverlay.setMessage(message);
            rootFrame.getRootPane().getGlassPane().setVisible(true);
        } else {
            ViewerStatusBar bar = getInstance();
            if (bar != null) bar.doStartLoading(message);
        }
    }

    private static void doStopLoadingOnEdt() {
        if (rootFrame != null && loadingOverlay != null) {
            rootFrame.getRootPane().getGlassPane().setVisible(false);
        } else {
            ViewerStatusBar bar = getInstance();
            if (bar != null) bar.doStopLoading();
        }
    }

    private void doStartLoading(String message) {
        messageLabel.setText(message != null ? message : "Loading...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        setVisible(true);
        revalidate();
        repaint();
    }

    private void doStopLoading() {
        progressBar.setVisible(false);
        messageLabel.setText(" ");
        setVisible(false);
        revalidate();
        repaint();
    }
}
