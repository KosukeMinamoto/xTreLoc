package com.treloc.xtreloc.app.gui.view;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.TextAnchor;
import com.treloc.xtreloc.app.gui.util.AppSettingsCache;
import com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings;
import com.treloc.xtreloc.app.gui.util.UiFonts;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Panel for displaying k-distance graph.
 * Used for DBSCAN clustering parameter estimation.
 * 
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 */
public class KDistancePlotPanel extends JPanel {
    private static final Color K_DISTANCE_LEGEND_COLOR = Color.BLUE;
    private static final Color ELBOW_LEGEND_COLOR = Color.RED;

    private ChartPanel chartPanel;
    private JFreeChart chart;
    private List<Double> kDistances;
    private double elbowEps;

    /** Strip above the chart (same layout as residual modes: no in-chart JFreeChart legend). */
    private final JPanel externalLegendStrip = createKDistanceExternalLegend();
    
    public KDistancePlotPanel() {
        setLayout(new BorderLayout());
        add(externalLegendStrip, BorderLayout.NORTH);
        createEmptyChart();
        add(chartPanel, BorderLayout.CENTER);
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
                            1f, new float[]{5f, 5f}, 0f));
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
    
    /**
     * Creates an empty chart.
     */
    private void createEmptyChart() {
        XYSeries emptySeries = new XYSeries("No data");
        XYSeriesCollection dataset = new XYSeriesCollection(emptySeries);
        
        chart = ChartFactory.createXYLineChart(
            "k-Distance Graph",
            "Point index (sorted)",
            "k-distance (km)",
            dataset,
            PlotOrientation.VERTICAL,
            false,
            true,
            false
        );
        
        applyChartAppearance(chart);
        hideChartLegend(chart);
        
        XYPlot plot = chart.getXYPlot();
        if (plot != null) {
            ChartAppearanceSettings axisSettings = AppSettingsCache.chartAppearance();
            NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
            if (xAxis != null) {
                xAxis.setLabelFont(axisSettings.getAxisLabelFont());
                xAxis.setTickLabelFont(axisSettings.getTickLabelFont());
            }
            
            NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
            if (yAxis != null) {
                yAxis.setLabelFont(axisSettings.getAxisLabelFont());
                yAxis.setTickLabelFont(axisSettings.getTickLabelFont());
            }
        }
        
        chartPanel = new ChartPanel(chart);
        ChartAppearanceSettings.applyResponsiveChartPanelSizing(chartPanel);
        chartPanel.setMinimumSize(new Dimension(120, 80));
        chartPanel.setMouseWheelEnabled(true);
    }
    
    /** Current chart (for PNG export from the parent convergence panel). */
    public JFreeChart getChart() {
        return chart;
    }
    
    /**
     * Sets k-distances and elbow epsilon value, then updates the chart.
     * 
     * @param distances list of k-distances
     * @param eps elbow epsilon value
     */
    public void setKDistances(List<Double> distances, double eps) {
        this.kDistances = distances;
        this.elbowEps = eps;
        updateChart();
    }
    
    /**
     * Updates the chart with current k-distances.
     */
    private void updateChart() {
        if (kDistances == null || kDistances.isEmpty()) {
            return;
        }
        
        XYSeries series = new XYSeries("k-Distance");
        for (int i = 0; i < kDistances.size(); i++) {
            series.add(i + 1, kDistances.get(i));
        }
        
        XYSeries elbowSeries = new XYSeries("Elbow");
        for (int i = 0; i < kDistances.size(); i++) {
            if (Math.abs(kDistances.get(i) - elbowEps) < 1e-6) {
                elbowSeries.add(i + 1, kDistances.get(i));
                break;
            }
        }
        
        XYSeriesCollection dataset = new XYSeriesCollection(series);
        dataset.addSeries(elbowSeries);
        
        chart = ChartFactory.createXYLineChart(
            "k-Distance Graph",
            "Point index (sorted)",
            "k-distance (km)",
            dataset,
            PlotOrientation.VERTICAL,
            false,
            true,
            false
        );
        
        applyChartAppearance(chart);
        hideChartLegend(chart);
        
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, K_DISTANCE_LEGEND_COLOR);
        renderer.setSeriesPaint(1, ELBOW_LEGEND_COLOR);
        renderer.setSeriesShapesVisible(1, true);
        
        // Get line width from settings
        ChartAppearanceSettings settings = AppSettingsCache.chartAppearance();
        renderer.setSeriesStroke(0, new BasicStroke(settings.getLineWidth()));
        renderer.setSeriesStroke(1, new BasicStroke(settings.getLineWidth()));
        plot.setRenderer(renderer);
        
        // Configure axes
        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        if (xAxis != null) {
            xAxis.setLabelFont(settings.getAxisLabelFont());
            xAxis.setTickLabelFont(settings.getTickLabelFont());
        }
        
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        if (yAxis != null) {
            yAxis.setLabelFont(settings.getAxisLabelFont());
            yAxis.setTickLabelFont(settings.getTickLabelFont());
        }
        
        double[] percentages = {0.7, 0.8, 0.9, 0.95};
        Color[] colors = {Color.GREEN, Color.ORANGE, Color.MAGENTA, Color.CYAN};
        Font labelFont = settings.getTickLabelFont();
        for (int i = 0; i < percentages.length; i++) {
            int index = (int) (percentages[i] * kDistances.size()) - 1;
            if (index >= 0 && index < kDistances.size()) {
                ValueMarker marker = new ValueMarker(index + 1);
                marker.setPaint(colors[i]);
                marker.setStroke(new java.awt.BasicStroke(4.0f, java.awt.BasicStroke.CAP_BUTT, 
                    java.awt.BasicStroke.JOIN_BEVEL, 0, new float[]{5.0f}, 0));
                plot.addDomainMarker(marker);
                
                marker.setLabel(String.format("%.0f%%", percentages[i] * 100));
                marker.setLabelFont(labelFont);
            }
        }
        
        int elbowIndex = -1;
        for (int i = 0; i < kDistances.size(); i++) {
            if (Math.abs(kDistances.get(i) - elbowEps) < 1e-6) {
                elbowIndex = i;
                break;
            }
        }
        if (elbowIndex != -1) {
            ValueMarker elbowMarker = new ValueMarker(elbowIndex + 1);
            elbowMarker.setPaint(Color.RED);
            elbowMarker.setStroke(new java.awt.BasicStroke(2.0f));
            elbowMarker.setLabel("Elbow Epsilon");
            elbowMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
            elbowMarker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
            plot.addDomainMarker(elbowMarker);
        }
        
        chartPanel.setChart(chart);
        chartPanel.repaint();
    }
    
    /**
     * Applies chart appearance settings to a JFreeChart.
     * 
     * @param chart the chart to apply settings to
     */
    private void applyChartAppearance(JFreeChart chart) {
        ChartAppearanceSettings settings = AppSettingsCache.chartAppearance();
        
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(settings.getTitleFont());
        }
        chart.setBackgroundPaint(ChartAppearanceSettings.TRANSPARENT_CHART_BACKGROUND);
        
        XYPlot plot = chart.getXYPlot();
        if (plot != null) {
            plot.setBackgroundPaint(ChartAppearanceSettings.TRANSPARENT_CHART_BACKGROUND);
            plot.setDomainGridlinePaint(settings.getGridlineColorAsColor());
            plot.setRangeGridlinePaint(settings.getGridlineColorAsColor());
            plot.setDomainGridlineStroke(settings.getGridlineStroke());
            plot.setRangeGridlineStroke(settings.getGridlineStroke());
            plot.setDomainGridlinesVisible(true);
            plot.setRangeGridlinesVisible(true);
        }
        
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(settings.getLegendFont());
        }
    }

    private static void hideChartLegend(JFreeChart chart) {
        if (chart.getLegend() != null) {
            chart.getLegend().setVisible(false);
        }
    }
    
    /**
     * Exports the chart as an image file.
     * This method is called from ResidualPlotPanel's export button.
     * 
     * @param outputFile the output file
     * @throws Exception if export fails
     */
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
            File pngFile = new File(outputFile.getParent(), 
                outputFile.getName() + ".png");
            javax.imageio.ImageIO.write(image, "PNG", pngFile);
        }
    }
}

