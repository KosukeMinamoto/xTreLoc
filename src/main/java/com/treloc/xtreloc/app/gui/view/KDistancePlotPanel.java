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
import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings;

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
    private ChartPanel chartPanel;
    private JFreeChart chart;
    private List<Double> kDistances;
    private double elbowEps;
    
    public KDistancePlotPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("k-Distance Graph"));
        
        createEmptyChart();
        add(chartPanel, BorderLayout.CENTER);
    }
    
    /**
     * Creates an empty chart.
     */
    private void createEmptyChart() {
        XYSeries emptySeries = new XYSeries("No data");
        XYSeriesCollection dataset = new XYSeriesCollection(emptySeries);
        
        chart = ChartFactory.createXYLineChart(
            "k-Distance Graph",
            "Points",
            "Distance (km)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        
        applyChartAppearance(chart);
        
        XYPlot plot = chart.getXYPlot();
        if (plot != null) {
            NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
            if (xAxis != null) {
                ChartAppearanceSettings settings = AppSettings.load().getChartAppearance();
                xAxis.setLabelFont(settings.getAxisLabelFont());
                xAxis.setTickLabelFont(settings.getTickLabelFont());
            }
            
            NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
            if (yAxis != null) {
                ChartAppearanceSettings settings = AppSettings.load().getChartAppearance();
                yAxis.setLabelFont(settings.getAxisLabelFont());
                yAxis.setTickLabelFont(settings.getTickLabelFont());
            }
        }
        
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 600));
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
            "Points",
            "Distance (km)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        
        applyChartAppearance(chart);
        
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, Color.BLUE);
        renderer.setSeriesPaint(1, Color.RED);
        renderer.setSeriesShapesVisible(1, true);
        
        // Get line width from settings
        ChartAppearanceSettings settings = AppSettings.load().getChartAppearance();
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
        
        chart.getLegend().setPosition(org.jfree.ui.RectangleEdge.TOP);
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(settings.getLegendFont());
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
        ChartAppearanceSettings settings = AppSettings.load().getChartAppearance();
        
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(settings.getTitleFont());
        }
        
        XYPlot plot = chart.getXYPlot();
        if (plot != null) {
            plot.setBackgroundPaint(settings.getBackgroundColorAsColor());
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
    
    /**
     * Exports the chart as an image file.
     * This method is called from ResidualPlotPanel's export button.
     * 
     * @param outputFile the output file
     * @throws Exception if export fails
     */
    public void exportChartImageToFile(File outputFile) throws Exception {
        int width = chartPanel.getWidth();
        int height = chartPanel.getHeight();
        if (width <= 0 || height <= 0) {
            width = 800;
            height = 600;
        }
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        chartPanel.print(g2d);
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

