package com.treloc.xtreloc.app.gui.view;

import javax.swing.*;
import java.awt.*;

/**
 * A circular spinner component that draws a rotating arc.
 * Call startAnimation() when shown and stopAnimation() when hidden.
 */
public class CircularSpinner extends JComponent {
    private static final int SIZE = 40;
    private static final Color SPINNER_COLOR = new Color(255, 255, 255, 220);
    private static final int ARC_EXTENT = 270;

    private int angleDegrees = 0;
    private javax.swing.Timer timer;

    public CircularSpinner() {
        setPreferredSize(new Dimension(SIZE, SIZE));
        setMinimumSize(new Dimension(SIZE, SIZE));
        setMaximumSize(new Dimension(SIZE, SIZE));
        setOpaque(false);
    }

    /**
     * Starts the rotation animation. Call when the spinner becomes visible.
     */
    public void startAnimation() {
        if (timer != null) return;
        angleDegrees = 0;
        timer = new javax.swing.Timer(50, e -> {
            angleDegrees = (angleDegrees + 12) % 360;
            repaint();
        });
        timer.start();
    }

    /**
     * Stops the rotation animation. Call when the spinner is hidden.
     */
    public void stopAnimation() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2;
        int y = (h - size) / 2;
        g2.setColor(SPINNER_COLOR);
        g2.setStroke(new BasicStroke(size / 8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(x + size / 8, y + size / 8, size - size / 4, size - size / 4, angleDegrees, ARC_EXTENT);
        g2.dispose();
    }
}
