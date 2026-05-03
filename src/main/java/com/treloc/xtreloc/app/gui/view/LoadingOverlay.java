package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.UiFonts;

import javax.swing.*;
import java.awt.*;

/**
 * Full-window overlay shown during catalog loading.
 * Dims the screen with a semi-transparent layer and blocks all input.
 * Shows a loading message and circular spinner in the center.
 */
public class LoadingOverlay extends JPanel {
    private static final Color DIM_COLOR = new Color(0, 0, 0, 140);

    private final JLabel messageLabel;
    private final CircularSpinner spinner;

    public LoadingOverlay() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        JPanel centerPanel = new JPanel(new BorderLayout(0, 12));
        centerPanel.setOpaque(false);
        messageLabel = new JLabel("Loading...");
        messageLabel.setFont(UiFonts.uiPlain(14f));
        messageLabel.setForeground(Color.WHITE);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        centerPanel.add(messageLabel, BorderLayout.NORTH);

        spinner = new CircularSpinner();
        JPanel spinnerWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        spinnerWrap.setOpaque(false);
        spinnerWrap.add(spinner);
        centerPanel.add(spinnerWrap, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(20, 20, 20, 20);
        add(centerPanel, gbc);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            spinner.startAnimation();
        } else {
            spinner.stopAnimation();
        }
    }

    /**
     * Updates the loading message text.
     */
    public void setMessage(String message) {
        messageLabel.setText(message != null ? message : "Loading...");
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(DIM_COLOR);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
