package com.treloc.xtreloc.app.gui.view;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Panel for displaying residual convergence plots in real-time.
 * Supports STD, MCMC, and TRD modes.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class ResidualPlotPanel extends JPanel {
    private ChartPanel chartPanel;
    private JFreeChart chart;
    private XYSeries residualSeries;
    private XYSeriesCollection dataset;
    private String mode;
    private int maxDataPoints = 1000; // Maximum number of data points to keep
    private JButton clearButton;
    private JButton exportButton;
    private JCheckBox autoScaleCheckBox;
    private boolean autoScale = true;
    
    // For different modes
    private XYSeries mcmcLikelihoodSeries; // For MCMC mode
    private XYSeries trdClusterSeries; // For TRD mode (per cluster)
    
    // For parallel processing: multiple events
    private Map<String, EventSeriesInfo> eventSeriesMap;
    private String activeEventName;
    private int maxHistoryEvents = 3; // Maximum number of completed events to show
    private JLabel activeEventLabel;
    
    /**
     * Information about an event's series
     */
    private static class EventSeriesInfo {
        XYSeries series;
        XYSeries likelihoodSeries; // For MCMC mode
        boolean isActive;
        long lastUpdateTime;
        
        EventSeriesInfo(XYSeries series) {
            this.series = series;
            this.likelihoodSeries = null;
            this.isActive = true;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    public ResidualPlotPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("📈 Residual Convergence Plot"));
        
        eventSeriesMap = new HashMap<>();
        
        // Create empty chart initially
        createEmptyChart();
        
        // Control panel
        JPanel controlPanel = new JPanel(new BorderLayout());
        
        // Left side: Active event label
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        activeEventLabel = new JLabel("Active: None");
        activeEventLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        leftPanel.add(activeEventLabel);
        controlPanel.add(leftPanel, BorderLayout.WEST);
        
        // Right side: Controls
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        autoScaleCheckBox = new JCheckBox("Auto Scale", true);
        autoScaleCheckBox.addActionListener(e -> {
            autoScale = autoScaleCheckBox.isSelected();
            updateChart();
        });
        rightPanel.add(autoScaleCheckBox);
        
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearData());
        rightPanel.add(clearButton);
        
        exportButton = new JButton("Export Image");
        exportButton.addActionListener(e -> exportChartImage());
        rightPanel.add(exportButton);
        
        controlPanel.add(rightPanel, BorderLayout.EAST);
        
        add(controlPanel, BorderLayout.NORTH);
        add(chartPanel, BorderLayout.CENTER);
        
        // Ensure panel is visible and has proper size
        setVisible(true);
        setOpaque(true);
        
        // Force initial chart update
        SwingUtilities.invokeLater(() -> {
            updateChart();
            if (chartPanel != null) {
                chartPanel.repaint();
            }
        });
    }
    
    /**
     * Creates an empty chart.
     */
    private void createEmptyChart() {
        residualSeries = new XYSeries("Residual");
        // Add initial dummy point to ensure chart is visible
        residualSeries.add(0, 0);
        dataset = new XYSeriesCollection(residualSeries);
        
        chart = ChartFactory.createXYLineChart(
            "Residual Convergence",
            "Iteration",
            "Residual (s)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        
        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 14));
        
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, new Color(50, 150, 200));
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(renderer);
        
        // Configure axes
        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        xAxis.setAutoRange(true);
        xAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setAutoRange(autoScale);
        yAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));
        chartPanel.setMinimumSize(new Dimension(300, 200));
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setMouseZoomable(true);
        
        // Ensure chart panel is visible
        chartPanel.setVisible(true);
    }
    
    /**
     * Sets the mode and initializes appropriate series.
     * 
     * @param mode the solver mode (STD, MCMC, or TRD)
     */
    public void setMode(String mode) {
        this.mode = mode;
        
        dataset.removeAllSeries();
        eventSeriesMap.clear();
        activeEventName = null;
        
        residualSeries = new XYSeries("Residual");
        // Add initial dummy point to ensure chart is visible
        residualSeries.add(0, 0);
        dataset.addSeries(residualSeries);
        
        if ("MCMC".equals(mode)) {
            mcmcLikelihoodSeries = new XYSeries("Log-Likelihood");
            dataset.addSeries(mcmcLikelihoodSeries);
            
            XYPlot plot = chart.getXYPlot();
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesPaint(1, new Color(200, 100, 50));
            renderer.setSeriesStroke(1, new BasicStroke(1.5f));
            
            // Add secondary axis for likelihood
            NumberAxis likelihoodAxis = new NumberAxis("Log-Likelihood");
            likelihoodAxis.setAutoRange(true);
            plot.setRangeAxis(1, likelihoodAxis);
            plot.mapDatasetToRangeAxis(1, 1);
        } else if ("TRD".equals(mode)) {
            trdClusterSeries = new XYSeries("Per Cluster");
            dataset.addSeries(trdClusterSeries);
            
            XYPlot plot = chart.getXYPlot();
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesPaint(1, new Color(150, 200, 100));
            renderer.setSeriesStroke(1, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, 
                BasicStroke.JOIN_ROUND, 1.0f, new float[]{5.0f, 5.0f}, 0.0f));
        }
        
        updateChartTitle();
        updateChart();
    }
    
    /**
     * Updates the chart title based on current mode.
     */
    private void updateChartTitle() {
        String title;
        switch (mode) {
            case "STD":
                title = "STD Mode - Residual Convergence";
                break;
            case "MCMC":
                title = "MCMC Mode - Residual & Log-Likelihood";
                break;
            case "TRD":
                title = "TRD Mode - Residual Convergence (Per Cluster)";
                break;
            default:
                title = "Residual Convergence";
        }
        chart.setTitle(title);
    }
    
    /**
     * Adds a residual data point.
     * 
     * @param iteration iteration number
     * @param residual residual value
     */
    public void addResidualPoint(int iteration, double residual) {
        if (activeEventName != null) {
            addResidualPoint(activeEventName, iteration, residual);
        } else {
            // Fallback to single series mode
            SwingUtilities.invokeLater(() -> {
                // Remove initial dummy point if exists
                if (residualSeries.getItemCount() == 1 && residualSeries.getX(0).doubleValue() == 0 && 
                    residualSeries.getY(0).doubleValue() == 0) {
                    residualSeries.clear();
                }
                
                residualSeries.add(iteration, residual);
                
                // Limit data points to prevent memory issues
                if (residualSeries.getItemCount() > maxDataPoints) {
                    residualSeries.remove(0);
                }
                
                updateChart();
            });
        }
    }
    
    /**
     * Adds a residual data point for a specific event (for parallel processing).
     * 
     * @param eventName name of the event (e.g., filename)
     * @param iteration iteration number
     * @param residual residual value
     */
    public void addResidualPoint(String eventName, int iteration, double residual) {
        SwingUtilities.invokeLater(() -> {
            EventSeriesInfo info = eventSeriesMap.get(eventName);
            if (info == null) {
                // Register new event if not exists
                registerEvent(eventName);
                info = eventSeriesMap.get(eventName);
            }
            
            // Remove initial dummy point if exists
            if (info.series.getItemCount() == 1 && info.series.getX(0).doubleValue() == 0 && 
                info.series.getY(0).doubleValue() == 0) {
                info.series.clear();
            }
            
            info.series.add(iteration, residual);
            info.lastUpdateTime = System.currentTimeMillis();
            
            // Limit data points to prevent memory issues
            if (info.series.getItemCount() > maxDataPoints) {
                info.series.remove(0);
            }
            
            // Update color if this is the active event
            if (eventName.equals(activeEventName)) {
                updateSeriesColor(eventName, true);
            }
            
            updateChart();
        });
    }
    
    /**
     * Registers a new event and creates its series.
     * 
     * @param eventName name of the event
     */
    public void registerEvent(String eventName) {
        if (!eventSeriesMap.containsKey(eventName)) {
            XYSeries series = new XYSeries(eventName);
            EventSeriesInfo info = new EventSeriesInfo(series);
            eventSeriesMap.put(eventName, info);
            dataset.addSeries(series);
            
            // Set initial color
            int seriesIndex = dataset.getSeriesCount() - 1;
            XYPlot plot = chart.getXYPlot();
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
            if (eventName.equals(activeEventName)) {
                renderer.setSeriesPaint(seriesIndex, new Color(50, 150, 200)); // Active: bright blue
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(2.5f));
            } else {
                renderer.setSeriesPaint(seriesIndex, new Color(200, 200, 200)); // Inactive: light gray
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(1.0f, BasicStroke.CAP_ROUND, 
                    BasicStroke.JOIN_ROUND, 1.0f, new float[]{3.0f, 3.0f}, 0.0f));
            }
            
            // For MCMC mode, ensure secondary axis is available
            if ("MCMC".equals(mode) && plot.getRangeAxis(1) == null) {
                NumberAxis likelihoodAxis = new NumberAxis("Log-Likelihood");
                likelihoodAxis.setAutoRange(true);
                plot.setRangeAxis(1, likelihoodAxis);
            }
        }
    }
    
    /**
     * Sets the active event (currently being processed).
     * 
     * @param eventName name of the active event
     */
    public void setActiveEvent(String eventName) {
        SwingUtilities.invokeLater(() -> {
            // Deactivate previous event
            if (activeEventName != null && eventSeriesMap.containsKey(activeEventName)) {
                EventSeriesInfo oldInfo = eventSeriesMap.get(activeEventName);
                oldInfo.isActive = false;
                updateSeriesColor(activeEventName, false);
            }
            
            // Activate new event
            activeEventName = eventName;
            if (!eventSeriesMap.containsKey(eventName)) {
                registerEvent(eventName);
            }
            
            EventSeriesInfo info = eventSeriesMap.get(eventName);
            info.isActive = true;
            updateSeriesColor(eventName, true);
            
            // Update label
            activeEventLabel.setText("Active: " + eventName);
            
            // Clean up old events
            cleanupOldEvents();
            
            updateChart();
        });
    }
    
    /**
     * Marks an event as completed.
     * 
     * @param eventName name of the completed event
     */
    public void markEventCompleted(String eventName) {
        SwingUtilities.invokeLater(() -> {
            EventSeriesInfo info = eventSeriesMap.get(eventName);
            if (info != null) {
                info.isActive = false;
                updateSeriesColor(eventName, false);
                
                // If this was the active event, find next active
                if (eventName.equals(activeEventName)) {
                    findNextActiveEvent();
                }
            }
        });
    }
    
    /**
     * Updates the color and style of a series based on its active status.
     */
    private void updateSeriesColor(String eventName, boolean isActive) {
        EventSeriesInfo info = eventSeriesMap.get(eventName);
        if (info == null) return;
        
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        
        // Find the series index
        int seriesIndex = -1;
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            if (dataset.getSeries(i).getKey().equals(eventName)) {
                seriesIndex = i;
                break;
            }
        }
        
        if (seriesIndex >= 0) {
            if (isActive) {
                renderer.setSeriesPaint(seriesIndex, new Color(50, 150, 200)); // Bright blue
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(2.5f));
            } else {
                renderer.setSeriesPaint(seriesIndex, new Color(200, 200, 200)); // Light gray
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(1.0f, BasicStroke.CAP_ROUND, 
                    BasicStroke.JOIN_ROUND, 1.0f, new float[]{3.0f, 3.0f}, 0.0f));
            }
        }
    }
    
    /**
     * Finds the next active event (if any).
     */
    private void findNextActiveEvent() {
        String nextActive = null;
        long latestTime = 0;
        
        for (Map.Entry<String, EventSeriesInfo> entry : eventSeriesMap.entrySet()) {
            if (entry.getValue().isActive && entry.getValue().lastUpdateTime > latestTime) {
                nextActive = entry.getKey();
                latestTime = entry.getValue().lastUpdateTime;
            }
        }
        
        if (nextActive != null) {
            activeEventName = nextActive;
            activeEventLabel.setText("Active: " + nextActive);
        } else {
            activeEventName = null;
            activeEventLabel.setText("Active: None");
        }
    }
    
    /**
     * Cleans up old completed events, keeping only the most recent ones.
     */
    private void cleanupOldEvents() {
        // Get all inactive events
        List<Map.Entry<String, EventSeriesInfo>> inactiveEvents = new ArrayList<>();
        for (Map.Entry<String, EventSeriesInfo> entry : eventSeriesMap.entrySet()) {
            if (!entry.getValue().isActive) {
                inactiveEvents.add(entry);
            }
        }
        
        // Sort by last update time (oldest first)
        Collections.sort(inactiveEvents, (a, b) -> 
            Long.compare(a.getValue().lastUpdateTime, b.getValue().lastUpdateTime));
        
        // Remove events beyond the limit
        while (inactiveEvents.size() > maxHistoryEvents) {
            Map.Entry<String, EventSeriesInfo> oldest = inactiveEvents.remove(0);
            EventSeriesInfo info = oldest.getValue();
            
            // Remove both residual and likelihood series if they exist
            dataset.removeSeries(info.series);
            if (info.likelihoodSeries != null) {
                dataset.removeSeries(info.likelihoodSeries);
            }
            
            eventSeriesMap.remove(oldest.getKey());
        }
    }
    
    /**
     * Adds a residual data point for TRD mode (with cluster info).
     * 
     * @param iteration iteration number
     * @param residual residual value
     * @param clusterId cluster ID (for TRD mode)
     */
    public void addResidualPoint(int iteration, double residual, int clusterId) {
        SwingUtilities.invokeLater(() -> {
            residualSeries.add(iteration, residual);
            
            if (trdClusterSeries != null) {
                // Add cluster-specific point
                trdClusterSeries.add(iteration, residual);
                if (trdClusterSeries.getItemCount() > maxDataPoints) {
                    trdClusterSeries.remove(0);
                }
            }
            
            // Limit data points
            if (residualSeries.getItemCount() > maxDataPoints) {
                residualSeries.remove(0);
            }
            
            updateChart();
        });
    }
    
    /**
     * Adds a log-likelihood point for MCMC mode.
     * 
     * @param sample sample number
     * @param logLikelihood log-likelihood value
     */
    public void addLikelihoodPoint(int sample, double logLikelihood) {
        if (activeEventName != null) {
            addLikelihoodPoint(activeEventName, sample, logLikelihood);
        } else {
            // Fallback to single series mode
            if (mcmcLikelihoodSeries != null) {
                SwingUtilities.invokeLater(() -> {
                    mcmcLikelihoodSeries.add(sample, logLikelihood);
                    
                    if (mcmcLikelihoodSeries.getItemCount() > maxDataPoints) {
                        mcmcLikelihoodSeries.remove(0);
                    }
                    
                    updateChart();
                });
            }
        }
    }
    
    /**
     * Adds a log-likelihood point for a specific event (for parallel processing in MCMC mode).
     * 
     * @param eventName name of the event (e.g., filename)
     * @param sample sample number
     * @param logLikelihood log-likelihood value
     */
    public void addLikelihoodPoint(String eventName, int sample, double logLikelihood) {
        SwingUtilities.invokeLater(() -> {
            EventSeriesInfo info = eventSeriesMap.get(eventName);
            if (info == null) {
                // Register new event if not exists
                registerEvent(eventName);
                info = eventSeriesMap.get(eventName);
            }
            
            // Create likelihood series if it doesn't exist (for MCMC mode)
            if (info.likelihoodSeries == null && "MCMC".equals(mode)) {
                info.likelihoodSeries = new XYSeries(eventName + " (Log-Likelihood)");
                dataset.addSeries(info.likelihoodSeries);
                
                // Configure secondary axis for likelihood
                XYPlot plot = chart.getXYPlot();
                if (plot.getRangeAxis(1) == null) {
                    NumberAxis likelihoodAxis = new NumberAxis("Log-Likelihood");
                    likelihoodAxis.setAutoRange(true);
                    plot.setRangeAxis(1, likelihoodAxis);
                }
                
                // Map likelihood series to secondary axis
                int seriesIndex = dataset.getSeriesCount() - 1;
                plot.mapDatasetToRangeAxis(seriesIndex, 1);
                
                // Set style for likelihood series
                XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
                Color eventColor = info.isActive ? new Color(50, 150, 200) : new Color(200, 200, 200);
                renderer.setSeriesPaint(seriesIndex, new Color(eventColor.getRed(), eventColor.getGreen(), 
                    eventColor.getBlue(), 150)); // Lighter shade
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(1.0f, BasicStroke.CAP_ROUND, 
                    BasicStroke.JOIN_ROUND, 1.0f, new float[]{5.0f, 5.0f}, 0.0f));
            }
            
            if (info.likelihoodSeries != null) {
                info.likelihoodSeries.add(sample, logLikelihood);
                
                // Limit data points
                if (info.likelihoodSeries.getItemCount() > maxDataPoints) {
                    info.likelihoodSeries.remove(0);
                }
            }
            
            updateChart();
        });
    }
    
    /**
     * Updates the chart display.
     */
    private void updateChart() {
        if (chart != null && chartPanel != null) {
            XYPlot plot = chart.getXYPlot();
            NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
            yAxis.setAutoRange(autoScale);
            
            chartPanel.repaint();
        }
    }
    
    /**
     * Clears all data from the chart.
     */
    public void clearData() {
        residualSeries.clear();
        // Add initial dummy point to ensure chart is visible
        residualSeries.add(0, 0);
        
        if (mcmcLikelihoodSeries != null) {
            mcmcLikelihoodSeries.clear();
        }
        if (trdClusterSeries != null) {
            trdClusterSeries.clear();
        }
        
        // Clear event series
        eventSeriesMap.clear();
        activeEventName = null;
        if (activeEventLabel != null) {
            activeEventLabel.setText("Active: None");
        }
        
        // Remove all event series from dataset
        dataset.removeAllSeries();
        
        // Re-add base series
        dataset.addSeries(residualSeries);
        if (mcmcLikelihoodSeries != null) {
            dataset.addSeries(mcmcLikelihoodSeries);
        }
        if (trdClusterSeries != null) {
            dataset.addSeries(trdClusterSeries);
        }
        
        updateChart();
    }
    
    /**
     * Sets the maximum number of completed events to keep in history.
     * 
     * @param maxHistory maximum number of history events
     */
    public void setMaxHistoryEvents(int maxHistory) {
        this.maxHistoryEvents = maxHistory;
        cleanupOldEvents();
    }
    
    /**
     * Exports the chart as an image file.
     */
    private void exportChartImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Chart Image");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "PNG Images", "png"));
        fileChooser.setSelectedFile(new java.io.File("residual_plot.png"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".png")) {
                    file = new java.io.File(file.getAbsolutePath() + ".png");
                }
                
                int width = 1200;
                int height = 800;
                java.awt.image.BufferedImage image = chart.createBufferedImage(width, height);
                javax.imageio.ImageIO.write(image, "png", file);
                
                JOptionPane.showMessageDialog(this,
                    "Chart exported successfully to:\n" + file.getAbsolutePath(),
                    "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Error exporting chart: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Sets the maximum number of data points to keep.
     * 
     * @param maxPoints maximum number of points
     */
    public void setMaxDataPoints(int maxPoints) {
        this.maxDataPoints = maxPoints;
    }
}

