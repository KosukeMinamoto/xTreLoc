package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppSettingsCache;
import com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings;
import com.treloc.xtreloc.app.gui.util.UiFonts;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * k-distance graph for DBSCAN parameter estimation (custom canvas, no JFreeChart).
 */
public class KDistancePlotPanel extends JPanel {
    private static final Color K_DISTANCE_LEGEND_COLOR = Color.BLUE;
    private static final Color ELBOW_LEGEND_COLOR = Color.RED;

    private final JPanel externalLegendStrip = createKDistanceExternalLegend();
    private final PlotCanvas canvas = new PlotCanvas();
    private List<Double> kDistances = Collections.emptyList();
    private double elbowEps;

    public KDistancePlotPanel() {
        setLayout(new BorderLayout());
        add(externalLegendStrip, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
    }

    private static JPanel lineSwatch(Color color, boolean dashed) {
        JPanel swatch = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    if (dashed) {
                        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            1f, new float[] { 5f, 5f }, 0f));
                    } else {
                        g2.setStroke(new BasicStroke(2.5f));
                    }
                    int midY = getHeight() / 2;
                    g2.drawLine(0, midY, getWidth(), midY);
                } finally {
                    g2.dispose();
                }
            }
        };
        swatch.setPreferredSize(new Dimension(24, 12));
        swatch.setOpaque(false);
        return swatch;
    }

    private static JPanel createKDistanceExternalLegend() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        panel.setOpaque(false);
        Font labelFont = UiFonts.getLabelFont();
        panel.add(lineSwatch(K_DISTANCE_LEGEND_COLOR, false));
        JLabel kLabel = new JLabel("k-Distance");
        kLabel.setForeground(K_DISTANCE_LEGEND_COLOR);
        kLabel.setFont(labelFont);
        panel.add(kLabel);
        panel.add(lineSwatch(ELBOW_LEGEND_COLOR, false));
        JLabel eLabel = new JLabel("Elbow");
        eLabel.setForeground(ELBOW_LEGEND_COLOR);
        eLabel.setFont(labelFont);
        panel.add(eLabel);
        return panel;
    }

    public void setKDistances(List<Double> distances, double eps) {
        this.kDistances = distances == null ? Collections.emptyList() : new ArrayList<>(distances);
        this.elbowEps = eps;
        canvas.repaint();
    }

    public void exportChartImageToFile(File outputFile) throws Exception {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            width = 800;
            height = 600;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(getBackground() != null ? getBackground() : Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        printAll(g2d);
        g2d.dispose();

        String fileName = outputFile.getName().toLowerCase();
        if (fileName.endsWith(".png")) {
            javax.imageio.ImageIO.write(image, "PNG", outputFile);
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            javax.imageio.ImageIO.write(image, "JPEG", outputFile);
        } else {
            File pngFile = new File(outputFile.getParent(), outputFile.getName() + ".png");
            javax.imageio.ImageIO.write(image, "PNG", pngFile);
        }
    }

    private final class PlotCanvas extends JPanel {
        private PlotCanvas() {
            setOpaque(true);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(400, 220));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);

            ChartAppearanceSettings s = AppSettingsCache.chartAppearance();
            int left = 56;
            int right = 16;
            int top = 28;
            int bottom = 36;
            int plotW = Math.max(1, w - left - right);
            int plotH = Math.max(1, h - top - bottom);

            g.setFont(s.getTitleFont());
            g.setColor(Color.DARK_GRAY);
            String title = "k-Distance Graph";
            g.drawString(title, left, 18);

            g.setColor(new Color(220, 220, 224));
            g.drawRect(left, top, plotW, plotH);

            if (kDistances.isEmpty()) {
                g.setFont(s.getTickLabelFont());
                g.setColor(new Color(140, 140, 140));
                g.drawString("No data", left + 8, top + plotH / 2);
                g.dispose();
                return;
            }

            double yMax = 0;
            for (Double v : kDistances) {
                if (v != null && v > yMax) {
                    yMax = v;
                }
            }
            yMax = Math.max(yMax, elbowEps) * 1.05;
            if (yMax <= 0) {
                yMax = 1;
            }
            int n = kDistances.size();

            Stroke grid = s.getGridlineStroke();
            g.setStroke(grid);
            g.setColor(s.getGridlineColorAsColor());
            for (int i = 0; i <= 4; i++) {
                int y = top + plotH - (int) Math.round(i / 4.0 * plotH);
                g.drawLine(left, y, left + plotW, y);
            }

            double[] percentages = { 0.7, 0.8, 0.9, 0.95 };
            Color[] colors = { Color.GREEN, Color.ORANGE, Color.MAGENTA, Color.CYAN };
            g.setFont(s.getTickLabelFont());
            FontMetrics fm = g.getFontMetrics();
            Stroke dashed = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
                new float[] { 5f, 5f }, 0);
            for (int i = 0; i < percentages.length; i++) {
                int index = (int) (percentages[i] * n) - 1;
                if (index < 0 || index >= n) {
                    continue;
                }
                int x = xOf(index + 1, n, left, plotW);
                g.setColor(colors[i]);
                g.setStroke(dashed);
                g.drawLine(x, top, x, top + plotH);
                String lab = String.format("%.0f%%", percentages[i] * 100);
                g.drawString(lab, x + 3, top + 12 + i * (fm.getHeight() - 2));
            }

            int elbowIndex = -1;
            for (int i = 0; i < n; i++) {
                Double v = kDistances.get(i);
                if (v != null && Math.abs(v - elbowEps) < 1e-6) {
                    elbowIndex = i;
                    break;
                }
            }
            if (elbowIndex >= 0) {
                int x = xOf(elbowIndex + 1, n, left, plotW);
                g.setColor(ELBOW_LEGEND_COLOR);
                g.setStroke(new BasicStroke(2f));
                g.drawLine(x, top, x, top + plotH);
                g.drawString("Elbow Epsilon", Math.min(left + plotW - fm.stringWidth("Elbow Epsilon"), x + 4),
                    top + 12);
            }

            g.setColor(K_DISTANCE_LEGEND_COLOR);
            g.setStroke(new BasicStroke(s.getLineWidth()));
            int prevX = -1;
            int prevY = 0;
            int step = Math.max(1, n / Math.max(1, plotW * 2));
            for (int i = 0; i < n; i += step) {
                Double v = kDistances.get(i);
                if (v == null) {
                    continue;
                }
                int x = xOf(i + 1, n, left, plotW);
                int y = top + plotH - (int) Math.round(v / yMax * plotH);
                if (prevX >= 0) {
                    g.drawLine(prevX, prevY, x, y);
                }
                prevX = x;
                prevY = y;
            }
            if (elbowIndex >= 0) {
                Double v = kDistances.get(elbowIndex);
                if (v != null) {
                    int x = xOf(elbowIndex + 1, n, left, plotW);
                    int y = top + plotH - (int) Math.round(v / yMax * plotH);
                    g.setColor(ELBOW_LEGEND_COLOR);
                    g.fillOval(x - 3, y - 3, 6, 6);
                }
            }

            g.setColor(new Color(70, 70, 74));
            g.setFont(s.getAxisLabelFont());
            g.drawString("Point index (sorted)", left, top + plotH + 28);
            g.setFont(s.getTickLabelFont());
            g.drawString("1", left, top + plotH + 14);
            g.drawString(String.valueOf(n), left + plotW - fm.stringWidth(String.valueOf(n)), top + plotH + 14);
            g.drawString("0", 8, top + plotH);
            String ymaxLab = String.format("%.2f", yMax);
            g.drawString(ymaxLab, 4, top + fm.getAscent());
            g.dispose();
        }

        private int xOf(int index1, int n, int left, int plotW) {
            if (n <= 1) {
                return left;
            }
            return left + (int) Math.round((index1 - 1) / (double) (n - 1) * plotW);
        }
    }
}
