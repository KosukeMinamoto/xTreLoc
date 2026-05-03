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
import com.treloc.xtreloc.util.ModeNameMapper;
import com.treloc.xtreloc.app.gui.util.AppSettingsCache;
import com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings;
import com.treloc.xtreloc.app.gui.util.UiFonts;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convergence plot area for the Solver tab: residual charts (GRD, LMO, MCMC, DE, TRD),
 * optional split multi-event view (DE/TRD), and CLS k-distance — all use the same central {@link #residualPlotContainer}.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class ResidualPlotPanel extends JPanel {
    private static final Logger log = Logger.getLogger(ResidualPlotPanel.class.getName());

    private ChartPanel chartPanel;
    private JFreeChart chart;
    private XYSeries residualSeries;
    private XYSeriesCollection dataset;
    private String mode;
    private int maxDataPoints = 1000;
    private boolean autoScale = true;
    
    private XYSeries mcmcLikelihoodSeries;
    private XYSeries trdClusterSeries;
    
    private Map<String, EventSeriesInfo> eventSeriesMap;
    private String activeEventName;
    private int maxHistoryEvents = 3;
    private JLabel activeEventLabel;
    
    private JPanel splitViewPanel;
    private Map<String, EventChartInfo> eventChartMap;
    private Map<String, JPanel> eventPanelMap;
    private boolean useSplitView = true;
    private int maxParallelJobs = 4;
    private Set<String> activeProcessingEvents;
    
    private JPanel residualPlotContainer;
    /** Strip above the main chart: Residual only, or + Log-Likelihood (MCMC), or + Per Cluster (TRD). */
    private JPanel externalLegendPanel;
    private KDistancePlotPanel kDistancePlotPanel;
    
    private volatile long lastRepaintRequestTime = 0;
    private Timer repaintThrottleTimer;

    /**
     * Reads {@link ChartAppearanceSettings#getConvergenceRepaintThrottleMs()}: 0 = no throttle;
     * positive values are clamped to {@code [16, 60000]} ms.
     */
    private static int effectiveConvergenceRepaintThrottleMs() {
        try {
            int v = AppSettingsCache.chartAppearance().getConvergenceRepaintThrottleMs();
            if (v <= 0) {
                return 0;
            }
            return Math.max(16, Math.min(60_000, v));
        } catch (Exception e) {
            return 350;
        }
    }

    /**
     * Lets the chart scale to the full {@link ChartPanel} size. Defaults (e.g. max width 1024) leave empty
     * margins so the plot no longer matches the outer frame.
     */
    private static void configureChartPanelScaling(ChartPanel panel) {
        ChartAppearanceSettings.applyResponsiveChartPanelSizing(panel);
    }
    
    /**
     * Schedules a repaint with throttling to reduce EDT load during convergence updates.
     * Interval comes from chart appearance settings ({@code convergenceRepaintThrottleMs}).
     */
    private void requestRepaint() {
        int throttleMs = effectiveConvergenceRepaintThrottleMs();
        if (throttleMs <= 0) {
            SwingUtilities.invokeLater(this::doThrottledRepaint);
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - lastRepaintRequestTime >= throttleMs) {
                lastRepaintRequestTime = now;
                if (repaintThrottleTimer != null && repaintThrottleTimer.isRunning()) {
                    repaintThrottleTimer.stop();
                }
                SwingUtilities.invokeLater(this::doThrottledRepaint);
                return;
            }
            if (repaintThrottleTimer == null || !repaintThrottleTimer.isRunning()) {
                repaintThrottleTimer = new Timer(throttleMs, e -> {
                    lastRepaintRequestTime = System.currentTimeMillis();
                    doThrottledRepaint();
                });
                repaintThrottleTimer.setRepeats(false);
                repaintThrottleTimer.start();
            }
        }
    }
    
    private void doThrottledRepaint() {
        if (chartPanel != null) chartPanel.repaint();
        if (splitViewPanel != null) {
            splitViewPanel.revalidate();
            splitViewPanel.repaint();
        }
        if (residualPlotContainer != null) {
            residualPlotContainer.revalidate();
            residualPlotContainer.repaint();
        }
        revalidate();
        repaint();
    }
    
    /**
     * Information about an event's series
     */
    private static class EventSeriesInfo {
        XYSeries series;
        XYSeries likelihoodSeries;
        boolean isActive;
        long lastUpdateTime;
        
        EventSeriesInfo(XYSeries series) {
            this.series = series;
            this.likelihoodSeries = null;
            this.isActive = true;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Information about an event's individual chart (for split view)
     */
    private static class EventChartInfo {
        JFreeChart chart;
        ChartPanel chartPanel;
        /** Chart plus external legend strip (same layout as the main convergence plot). */
        JPanel plotHost;
        XYSeries series;
        XYSeries likelihoodSeries;
        XYSeriesCollection dataset;
        boolean isActive;
        long lastUpdateTime;
        
        EventChartInfo(String eventName, String mode) {
            this.series = new XYSeries(eventName);
            this.likelihoodSeries = null;
            this.dataset = new XYSeriesCollection(this.series);
            this.isActive = true;
            this.lastUpdateTime = System.currentTimeMillis();
            
            this.chart = ChartFactory.createXYLineChart(
                eventName + " - Residual Convergence",
                "Iteration",
                "RMS Residual (s)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
            );
            
            ResidualPlotPanel.applyChartAppearance(this.chart);
            
            XYPlot plot = this.chart.getXYPlot();
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
            renderer.setSeriesPaint(0, ResidualPlotPanel.RESIDUAL_LEGEND_COLOR);
            
            ChartAppearanceSettings settings = AppSettingsCache.chartAppearance();
            renderer.setSeriesStroke(0, new BasicStroke(settings.getLineWidth()));
            plot.setRenderer(renderer);
            
            NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
            xAxis.setAutoRange(true);
            xAxis.setLabelFont(settings.getAxisLabelFont());
            xAxis.setTickLabelFont(settings.getTickLabelFont());
            
            NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
            yAxis.setAutoRange(true);
            yAxis.setLabelFont(settings.getAxisLabelFont());
            yAxis.setTickLabelFont(settings.getTickLabelFont());
            yAxis.setLabelPaint(ResidualPlotPanel.RESIDUAL_LEGEND_COLOR);
            yAxis.setTickLabelPaint(ResidualPlotPanel.RESIDUAL_LEGEND_COLOR);
            
            this.chartPanel = new ChartPanel(this.chart);
            configureChartPanelScaling(this.chartPanel);
            this.chartPanel.setMinimumSize(new Dimension(200, 100));
            this.chartPanel.setMouseWheelEnabled(true);
            this.chartPanel.setMouseZoomable(true);
            
            if ("MCMC".equals(mode)) {
                this.likelihoodSeries = new XYSeries(eventName + " (Log-Likelihood)");
                this.dataset.addSeries(this.likelihoodSeries);
                
                NumberAxis likelihoodAxis = new NumberAxis("Log-Likelihood");
                likelihoodAxis.setAutoRange(true);
                likelihoodAxis.setLabelPaint(ResidualPlotPanel.LIKELIHOOD_LEGEND_COLOR);
                likelihoodAxis.setTickLabelPaint(ResidualPlotPanel.LIKELIHOOD_LEGEND_COLOR);
                plot.setRangeAxis(1, likelihoodAxis);
                plot.mapDatasetToRangeAxis(1, 1);
                
                renderer.setSeriesPaint(1, ResidualPlotPanel.LIKELIHOOD_LEGEND_COLOR);
                renderer.setSeriesStroke(1, new BasicStroke(1.5f));
            }
            
            if (this.chart.getLegend() != null) {
                this.chart.getLegend().setVisible(false);
            }
            JPanel legendStrip = ResidualPlotPanel.createSplitEventLegendStrip(mode);
            this.plotHost = new JPanel(new BorderLayout());
            this.plotHost.setOpaque(false);
            this.plotHost.add(legendStrip, BorderLayout.NORTH);
            this.plotHost.add(this.chartPanel, BorderLayout.CENTER);
        }
    }
    
    public ResidualPlotPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Residual Convergence Plot"));
        
        eventSeriesMap = new HashMap<>();
        eventChartMap = new HashMap<>();
        eventPanelMap = new HashMap<>();
        activeProcessingEvents = new HashSet<>();
        
        splitViewPanel = new JPanel();
        splitViewPanel.setLayout(new BoxLayout(splitViewPanel, BoxLayout.Y_AXIS));
        JScrollPane splitScrollPane = new JScrollPane(splitViewPanel);
        splitScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        splitScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        createEmptyChart();
        
        kDistancePlotPanel = new KDistancePlotPanel();
        
        residualPlotContainer = new JPanel(new BorderLayout());
        residualPlotContainer.add(chartPanel, BorderLayout.CENTER);

        JPopupMenu plotContextMenu = new JPopupMenu();
        plotContextMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                fillResidualPlotContextMenu(plotContextMenu);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        residualPlotContainer.setComponentPopupMenu(plotContextMenu);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        activeEventLabel = new JLabel("Active: None");
        activeEventLabel.setFont(UiFonts.uiPlain(11f));
        controlPanel.add(activeEventLabel);

        add(controlPanel, BorderLayout.NORTH);
        add(residualPlotContainer, BorderLayout.CENTER);
        
        setVisible(true);
        setOpaque(true);
        
        SwingUtilities.invokeLater(() -> {
            updateChart();
            if (chartPanel != null) {
                requestRepaint();
            }
        });
    }

    /**
     * Right-click on the plot area: Split view / Auto scale / Clear / Export (CLS: Export only).
     */
    private void fillResidualPlotContextMenu(JPopupMenu popup) {
        popup.removeAll();
        boolean cls = "CLS".equals(mode);
        if (cls) {
            JMenuItem export = new JMenuItem("Export image…");
            export.addActionListener(e -> exportChartImage());
            popup.add(export);
            return;
        }
        boolean locationMode = "GRD".equals(mode) || "LMO".equals(mode) || "MCMC".equals(mode)
            || "DE".equals(mode) || "TRD".equals(mode);
        if (!locationMode) {
            JMenuItem export = new JMenuItem("Export image…");
            export.addActionListener(e -> exportChartImage());
            popup.add(export);
            return;
        }

        if (isSplitViewSupported(mode)) {
            JCheckBoxMenuItem splitItem = new JCheckBoxMenuItem("Split view", useSplitView);
            splitItem.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED || e.getStateChange() == ItemEvent.DESELECTED) {
                    useSplitView = splitItem.isSelected();
                    updateViewMode();
                }
            });
            popup.add(splitItem);
        }

        JCheckBoxMenuItem autoItem = new JCheckBoxMenuItem("Auto scale", autoScale);
        autoItem.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED || e.getStateChange() == ItemEvent.DESELECTED) {
                autoScale = autoItem.isSelected();
                updateChart();
                updateSplitViewCharts();
            }
        });
        popup.add(autoItem);

        popup.addSeparator();
        JMenuItem clearItem = new JMenuItem("Clear");
        clearItem.addActionListener(e -> clearData());
        popup.add(clearItem);
        JMenuItem exportItem = new JMenuItem("Export image…");
        exportItem.addActionListener(e -> exportChartImage());
        popup.add(exportItem);
    }
    
    private static final Color RESIDUAL_LEGEND_COLOR = new Color(50, 150, 200);
    private static final Color LIKELIHOOD_LEGEND_COLOR = new Color(200, 100, 50);
    private static final Color TRD_CLUSTER_LEGEND_COLOR = new Color(150, 200, 100);

    private static boolean isResidualLocationMode(String m) {
        return "GRD".equals(m) || "LMO".equals(m) || "MCMC".equals(m) || "DE".equals(m) || "TRD".equals(m);
    }

    /** GRD/LMO/MCMC use a single combined chart (no split view); DE/TRD may use split for parallel events. */
    private static boolean isSplitViewSupported(String m) {
        return !"MCMC".equals(m) && !"LMO".equals(m) && !"GRD".equals(m);
    }

    private static JPanel createLineSwatch(Color color, boolean dashed, float lineWidth) {
        JPanel swatch = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    if (dashed) {
                        g2.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            1.0f, new float[]{5.0f, 5.0f}, 0.0f));
                    } else {
                        g2.setStroke(new BasicStroke(lineWidth));
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

    private static void addExternalLegendResidual(JPanel row, Font labelFont) {
        row.add(createLineSwatch(RESIDUAL_LEGEND_COLOR, false, 2.5f));
        JLabel lab = new JLabel("Residual");
        lab.setForeground(RESIDUAL_LEGEND_COLOR);
        lab.setFont(labelFont);
        row.add(lab);
    }

    private static void addExternalLegendSecondSeries(JPanel row, String text, Color color, boolean dashed,
                                                      float lineWidth, Font labelFont) {
        row.add(createLineSwatch(color, dashed, lineWidth));
        JLabel lab = new JLabel(text);
        lab.setForeground(color);
        lab.setFont(labelFont);
        row.add(lab);
    }

    /**
     * Legend strip above the main convergence chart for GRD/LMO/DE (residual only), MCMC (+ likelihood), TRD (+ per cluster).
     */
    private static JPanel createExternalLegendStrip(String mode) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        panel.setOpaque(false);
        Font labelFont = UiFonts.getLabelFont();
        addExternalLegendResidual(panel, labelFont);
        if ("MCMC".equals(mode)) {
            addExternalLegendSecondSeries(panel, "Log-Likelihood", LIKELIHOOD_LEGEND_COLOR, false, 1.5f, labelFont);
        } else if ("TRD".equals(mode)) {
            addExternalLegendSecondSeries(panel, "Per Cluster", TRD_CLUSTER_LEGEND_COLOR, true, 1.5f, labelFont);
        }
        return panel;
    }

    /**
     * Compact strip for split-view per-event charts (TRD mini-charts only show RMS residual, not the dashed cluster line).
     */
    private static JPanel createSplitEventLegendStrip(String mode) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        panel.setOpaque(false);
        Font labelFont = UiFonts.uiPlain(11f);
        addExternalLegendResidual(panel, labelFont);
        if ("MCMC".equals(mode)) {
            addExternalLegendSecondSeries(panel, "Log-Likelihood", LIKELIHOOD_LEGEND_COLOR, false, 1.5f, labelFont);
        }
        return panel;
    }
    
    /**
     * Creates an empty chart.
     */
    private void createEmptyChart() {
        residualSeries = new XYSeries("Residual");
        residualSeries.add(0, 0);
        dataset = new XYSeriesCollection(residualSeries);
        
        chart = ChartFactory.createXYLineChart(
            "Residual Convergence",
            "Iteration",
            "RMS Residual (s)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        
        applyChartAppearance(chart);
        
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, RESIDUAL_LEGEND_COLOR);
        
        // Get line width from settings
        ChartAppearanceSettings settings = AppSettingsCache.chartAppearance();
        renderer.setSeriesStroke(0, new BasicStroke(settings.getLineWidth()));
        plot.setRenderer(renderer);
        
        // Configure axes
        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        xAxis.setAutoRange(true);
        xAxis.setLabelFont(settings.getAxisLabelFont());
        xAxis.setTickLabelFont(settings.getTickLabelFont());
        
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setAutoRange(autoScale);
        yAxis.setLabelFont(settings.getAxisLabelFont());
        yAxis.setTickLabelFont(settings.getTickLabelFont());
        yAxis.setLabelPaint(RESIDUAL_LEGEND_COLOR);
        yAxis.setTickLabelPaint(RESIDUAL_LEGEND_COLOR);
        
        chartPanel = new ChartPanel(chart);
        configureChartPanelScaling(chartPanel);
        chartPanel.setMinimumSize(new Dimension(200, 100));
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setMouseZoomable(true);
        chartPanel.setVisible(true);
    }
    
    /**
     * Sets the mode and initializes appropriate series.
     * 
     * @param mode the solver mode (LMO, MCMC, TRD, GRD, DE, SYN, CLS)
     */
    public void setMode(String mode) {
        this.mode = mode;
        if (!isSplitViewSupported(mode)) {
            useSplitView = false;
        }
        
        boolean isLocationMode = "GRD".equals(mode) || "LMO".equals(mode) || 
                                "MCMC".equals(mode) || "DE".equals(mode) || "TRD".equals(mode);
        
        if ("CLS".equals(mode)) {
            String clsTitle = ModeNameMapper.getDisplayName("CLS");
            if (clsTitle == null) {
                clsTitle = "CLS";
            }
            setBorder(BorderFactory.createTitledBorder(clsTitle + " — k-distance"));
            updateViewMode();
            refreshPlotToolbarForMode();
            return;
        }
        
        setBorder(BorderFactory.createTitledBorder("Residual Convergence Plot"));
        
        if (isLocationMode) {
            dataset.removeAllSeries();
            eventSeriesMap.clear();
            activeEventName = null;
            mcmcLikelihoodSeries = null;
            trdClusterSeries = null;
            
            residualSeries = new XYSeries("Residual");
            residualSeries.add(0, 0);
            dataset.addSeries(residualSeries);
            XYPlot plot = chart.getXYPlot();
            // Clear stale secondary axis/series mappings when switching away from MCMC/TRD.
            plot.setRangeAxis(1, null);
            for (int i = 1; i < dataset.getSeriesCount(); i++) {
                plot.mapDatasetToRangeAxis(i, 0);
            }
            
            if ("MCMC".equals(mode)) {
                mcmcLikelihoodSeries = new XYSeries("Log-Likelihood");
                dataset.addSeries(mcmcLikelihoodSeries);
                
                XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
                renderer.setSeriesPaint(1, LIKELIHOOD_LEGEND_COLOR);
                renderer.setSeriesStroke(1, new BasicStroke(1.5f));
                
                NumberAxis likelihoodAxis = new NumberAxis("Log-Likelihood");
                likelihoodAxis.setAutoRange(true);
                likelihoodAxis.setLabelPaint(LIKELIHOOD_LEGEND_COLOR);
                likelihoodAxis.setTickLabelPaint(LIKELIHOOD_LEGEND_COLOR);
                plot.setRangeAxis(1, likelihoodAxis);
                plot.mapDatasetToRangeAxis(1, 1);
            } else if ("TRD".equals(mode)) {
                trdClusterSeries = new XYSeries("Per Cluster");
                dataset.addSeries(trdClusterSeries);
                
                XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
                renderer.setSeriesPaint(1, TRD_CLUSTER_LEGEND_COLOR);
                renderer.setSeriesStroke(1, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, 
                    BasicStroke.JOIN_ROUND, 1.0f, new float[]{5.0f, 5.0f}, 0.0f));
            }
            
            if (chart.getLegend() != null) {
                chart.getLegend().setVisible(false);
            }
            externalLegendPanel = createExternalLegendStrip(mode);
            
            updateChartTitle();
            updateChart();
            updateViewMode();
        } else {
            clearData();
        }
        refreshPlotToolbarForMode();
    }
    
    private void refreshPlotToolbarForMode() {
        boolean cls = "CLS".equals(mode);
        if (activeEventLabel != null) {
            activeEventLabel.setVisible(!cls);
        }
    }
    
    /**
     * Shows k-distance graph (for clustering mode).
     * 
     * @param kDistances list of k-distances
     * @param estimatedEps estimated epsilon value
     */
    public void showKDistanceGraph(List<Double> kDistances, double estimatedEps) {
        if (kDistancePlotPanel != null) {
            SwingUtilities.invokeLater(() -> {
                kDistancePlotPanel.setKDistances(kDistances, estimatedEps);
                updateViewMode();
            });
        }
    }
    
    /**
     * Rebuilds the plot area (e.g. after external layout changes).
     */
    public void showResidualPlot() {
        SwingUtilities.invokeLater(this::updateViewMode);
    }
    
    /**
     * Updates the chart title based on current mode.
     */
    private void updateChartTitle() {
        String title;
        String displayName = ModeNameMapper.getDisplayName(mode);
        switch (mode) {
            case "LMO":
            case "GRD":
                title = displayName + " - Residual Convergence";
                break;
            case "MCMC":
                title = displayName + " - Residual & Log-Likelihood";
                break;
            case "TRD":
                title = displayName + " - Residual Convergence (Per Cluster)";
                break;
            default:
                title = (displayName != null ? displayName + " - " : "") + "Residual Convergence";
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
            SwingUtilities.invokeLater(() -> {
                if (residualSeries.getItemCount() == 1 && residualSeries.getX(0).doubleValue() == 0 && 
                    residualSeries.getY(0).doubleValue() == 0) {
                    residualSeries.clear();
                }
                residualSeries.add(iteration, residual);
                
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
                registerEvent(eventName);
                info = eventSeriesMap.get(eventName);
            }
            
            if (info.series.getItemCount() == 1 && info.series.getX(0).doubleValue() == 0 && 
                info.series.getY(0).doubleValue() == 0) {
                info.series.clear();
            }
            info.series.add(iteration, residual);
            info.lastUpdateTime = System.currentTimeMillis();
            
            if (info.series.getItemCount() > maxDataPoints) {
                info.series.remove(0);
            }
            
            if (eventName.equals(activeEventName)) {
                updateSeriesColor(eventName, true);
            }
            
            if (useSplitView) {
                EventChartInfo chartInfo = eventChartMap.get(eventName);
                if (chartInfo == null) {
                    if (activeProcessingEvents.contains(eventName)) {
                        chartInfo = new EventChartInfo(eventName, mode);
                        eventChartMap.put(eventName, chartInfo);
                        
                        JPanel eventPanel = new JPanel(new BorderLayout());
                        eventPanel.setBorder(BorderFactory.createTitledBorder(eventName + " (Processing)"));
                        eventPanel.add(chartInfo.plotHost, BorderLayout.CENTER);
                        splitViewPanel.add(eventPanel);
                        eventPanelMap.put(eventName, eventPanel);
                        requestRepaint();
                    } else {
                        return;
                    }
                }
                
                if (chartInfo.series.getItemCount() == 1 && chartInfo.series.getX(0).doubleValue() == 0 && 
                    chartInfo.series.getY(0).doubleValue() == 0) {
                    chartInfo.series.clear();
                }
                chartInfo.series.add(iteration, residual);
                chartInfo.lastUpdateTime = System.currentTimeMillis();
                
                if (chartInfo.series.getItemCount() > maxDataPoints) {
                    chartInfo.series.remove(0);
                }
                
                XYPlot plot = chartInfo.chart.getXYPlot();
                NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
                yAxis.setAutoRange(autoScale);
                requestRepaint();
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
            
            int seriesIndex = dataset.getSeriesCount() - 1;
            XYPlot plot = chart.getXYPlot();
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
            if (eventName.equals(activeEventName)) {
                renderer.setSeriesPaint(seriesIndex, RESIDUAL_LEGEND_COLOR);
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(2.5f));
            } else {
                renderer.setSeriesPaint(seriesIndex, new Color(200, 200, 200));
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(1.0f, BasicStroke.CAP_ROUND, 
                    BasicStroke.JOIN_ROUND, 1.0f, new float[]{3.0f, 3.0f}, 0.0f));
            }
            
            if ("MCMC".equals(mode) && plot.getRangeAxis(1) == null) {
                NumberAxis likelihoodAxis = new NumberAxis("Log-Likelihood");
                likelihoodAxis.setAutoRange(true);
                plot.setRangeAxis(1, likelihoodAxis);
            }
        }
    }
    
    /**
     * Sets the maximum number of parallel jobs.
     * 
     * @param maxJobs maximum number of parallel jobs
     */
    public void setMaxParallelJobs(int maxJobs) {
        this.maxParallelJobs = maxJobs;
    }
    
    /**
     * Starts processing an event (adds it to active processing set).
     * 
     * @param eventName name of the event
     */
    public void startProcessingEvent(String eventName) {
        SwingUtilities.invokeLater(() -> {
            activeProcessingEvents.add(eventName);
            
            if (useSplitView) {
                EventChartInfo chartInfo = eventChartMap.get(eventName);
                if (chartInfo == null) {
                    chartInfo = new EventChartInfo(eventName, mode);
                    eventChartMap.put(eventName, chartInfo);
                    
                    JPanel eventPanel = new JPanel(new BorderLayout());
                    eventPanel.setBorder(BorderFactory.createTitledBorder(eventName + " (Processing)"));
                    eventPanel.add(chartInfo.plotHost, BorderLayout.CENTER);
                    splitViewPanel.add(eventPanel);
                    eventPanelMap.put(eventName, eventPanel);
                    requestRepaint();
                }
                chartInfo.isActive = true;
                
                updateVisibleEvents();
                
                if (eventChartMap.size() > 0) {
                    updateViewMode();
                }
            }
            
            activeEventName = eventName;
            if (!eventSeriesMap.containsKey(eventName)) {
                registerEvent(eventName);
            }
            
            EventSeriesInfo info = eventSeriesMap.get(eventName);
            info.isActive = true;
            updateSeriesColor(eventName, true);
            
            activeEventLabel.setText("Active: " + eventName + " (" + activeProcessingEvents.size() + " processing)");
            
            updateChart();
        });
    }
    
    /**
     * Stops processing an event (removes it from active processing set).
     * 
     * @param eventName name of the event
     */
    public void stopProcessingEvent(String eventName) {
        SwingUtilities.invokeLater(() -> {
            activeProcessingEvents.remove(eventName);
            
            if (useSplitView) {
                JPanel eventPanel = eventPanelMap.get(eventName);
                if (eventPanel != null) {
                    eventPanel.setBorder(BorderFactory.createTitledBorder(eventName + " (Completed)"));
                    updateVisibleEvents();
                }
                
                EventChartInfo chartInfo = eventChartMap.get(eventName);
                if (chartInfo != null) {
                    chartInfo.isActive = false;
                }
            }
            
            if (eventSeriesMap.containsKey(eventName)) {
                EventSeriesInfo info = eventSeriesMap.get(eventName);
                info.isActive = false;
                updateSeriesColor(eventName, false);
            }
            
            if (activeProcessingEvents.isEmpty()) {
                activeEventLabel.setText("Active: None");
            } else {
                activeEventLabel.setText("Active: " + activeProcessingEvents.size() + " processing");
            }
            
            updateChart();
        });
    }
    
    /**
     * Updates visible events in split view (shows only processing events and recent completed ones).
     */
    private void updateVisibleEvents() {
        for (String eventName : activeProcessingEvents) {
            JPanel eventPanel = eventPanelMap.get(eventName);
            if (eventPanel != null) {
                eventPanel.setVisible(true);
            }
        }
        
        List<String> completedEvents = new ArrayList<>();
        for (String eventName : eventChartMap.keySet()) {
            if (!activeProcessingEvents.contains(eventName)) {
                completedEvents.add(eventName);
            }
        }
        
        Collections.sort(completedEvents, (a, b) -> {
            EventChartInfo infoA = eventChartMap.get(a);
            EventChartInfo infoB = eventChartMap.get(b);
            return Long.compare(infoB.lastUpdateTime, infoA.lastUpdateTime);
        });
        
        int visibleCount = 0;
        for (String eventName : completedEvents) {
            JPanel eventPanel = eventPanelMap.get(eventName);
            if (eventPanel != null) {
                if (visibleCount < maxParallelJobs) {
                    eventPanel.setVisible(true);
                    visibleCount++;
                } else {
                    eventPanel.setVisible(false);
                }
            }
        }
        
        requestRepaint();
    }
    
    /**
     * Sets the active event (currently being processed).
     * 
     * @param eventName name of the active event
     */
    public void setActiveEvent(String eventName) {
        startProcessingEvent(eventName);
    }
    
    /**
     * Marks an event as completed.
     * 
     * @param eventName name of the completed event
     */
    public void markEventCompleted(String eventName) {
        stopProcessingEvent(eventName);
    }
    
    /**
     * Updates the color and style of a series based on its active status.
     */
    private void updateSeriesColor(String eventName, boolean isActive) {
        EventSeriesInfo info = eventSeriesMap.get(eventName);
        if (info == null) return;
        
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        
        int seriesIndex = -1;
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            if (dataset.getSeries(i).getKey().equals(eventName)) {
                seriesIndex = i;
                break;
            }
        }
        
        if (seriesIndex >= 0) {
            if (isActive) {
                renderer.setSeriesPaint(seriesIndex, RESIDUAL_LEGEND_COLOR);
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(2.5f));
            } else {
                renderer.setSeriesPaint(seriesIndex, new Color(200, 200, 200));
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
        List<Map.Entry<String, EventSeriesInfo>> inactiveEvents = new ArrayList<>();
        for (Map.Entry<String, EventSeriesInfo> entry : eventSeriesMap.entrySet()) {
            if (!entry.getValue().isActive) {
                inactiveEvents.add(entry);
            }
        }
        
        Collections.sort(inactiveEvents, (a, b) -> 
            Long.compare(a.getValue().lastUpdateTime, b.getValue().lastUpdateTime));
        
        while (inactiveEvents.size() > maxHistoryEvents) {
            Map.Entry<String, EventSeriesInfo> oldest = inactiveEvents.remove(0);
            EventSeriesInfo info = oldest.getValue();
            
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
                trdClusterSeries.add(iteration, residual);
                if (trdClusterSeries.getItemCount() > maxDataPoints) {
                    trdClusterSeries.remove(0);
                }
            }
            
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
                registerEvent(eventName);
                info = eventSeriesMap.get(eventName);
            }
            
            if (info.likelihoodSeries == null && "MCMC".equals(mode)) {
                info.likelihoodSeries = new XYSeries(eventName + " (Log-Likelihood)");
                dataset.addSeries(info.likelihoodSeries);
                
                XYPlot plot = chart.getXYPlot();
                if (plot.getRangeAxis(1) == null) {
                    NumberAxis likelihoodAxis = new NumberAxis("Log-Likelihood");
                    likelihoodAxis.setAutoRange(true);
                    plot.setRangeAxis(1, likelihoodAxis);
                }
                
                int seriesIndex = dataset.getSeriesCount() - 1;
                plot.mapDatasetToRangeAxis(seriesIndex, 1);
                
                XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
                Color eventColor = info.isActive ? RESIDUAL_LEGEND_COLOR : new Color(200, 200, 200);
                renderer.setSeriesPaint(seriesIndex, new Color(eventColor.getRed(), eventColor.getGreen(), 
                    eventColor.getBlue(), 150));
                renderer.setSeriesStroke(seriesIndex, new BasicStroke(1.0f, BasicStroke.CAP_ROUND, 
                    BasicStroke.JOIN_ROUND, 1.0f, new float[]{5.0f, 5.0f}, 0.0f));
            }
            
            if (info.likelihoodSeries != null) {
                info.likelihoodSeries.add(sample, logLikelihood);
                
                if (info.likelihoodSeries.getItemCount() > maxDataPoints) {
                    info.likelihoodSeries.remove(0);
                }
            }
            
            updateChart();
        });
    }
    
    /**
     * Applies chart appearance settings to a JFreeChart.
     * Static method so it can be called from static inner classes.
     * 
     * @param chart the chart to apply settings to
     */
    private static void applyChartAppearance(JFreeChart chart) {
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
            
            NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
            if (domainAxis != null) {
                domainAxis.setLabelFont(settings.getAxisLabelFont());
                domainAxis.setTickLabelFont(settings.getTickLabelFont());
            }
            
            NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            if (rangeAxis != null) {
                rangeAxis.setLabelFont(settings.getAxisLabelFont());
                rangeAxis.setTickLabelFont(settings.getTickLabelFont());
            }
        }
        
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(settings.getLegendFont());
        }
    }
    
    /**
     * Updates the chart display.
     */
    private void updateChart() {
        if (chart != null && chartPanel != null) {
            applyChartAppearance(chart);
            
            XYPlot plot = chart.getXYPlot();
            NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
            yAxis.setAutoRange(autoScale);
            yAxis.setLabelPaint(RESIDUAL_LEGEND_COLOR);
            yAxis.setTickLabelPaint(RESIDUAL_LEGEND_COLOR);
            if ("MCMC".equals(mode) && plot.getRangeAxisCount() > 1) {
                NumberAxis likelihoodAxis = (NumberAxis) plot.getRangeAxis(1);
                if (likelihoodAxis != null) {
                    likelihoodAxis.setLabelPaint(LIKELIHOOD_LEGEND_COLOR);
                    likelihoodAxis.setTickLabelPaint(LIKELIHOOD_LEGEND_COLOR);
                }
            }
            if (chart.getLegend() != null) {
                chart.getLegend().setVisible(!isResidualLocationMode(mode));
            }
            requestRepaint();
        }
    }
    
    /**
     * Updates the view mode (single chart vs split view).
     */
    private void updateViewMode() {
        if (residualPlotContainer == null) {
            return;
        }
        residualPlotContainer.removeAll();
        if ("CLS".equals(mode)) {
            residualPlotContainer.add(kDistancePlotPanel, BorderLayout.CENTER);
            requestRepaint();
            return;
        }
        if (isSplitViewSupported(mode) && useSplitView && eventChartMap.size() > 0) {
            JScrollPane splitScrollPane = new JScrollPane(splitViewPanel);
            splitScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            splitScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            residualPlotContainer.add(splitScrollPane, BorderLayout.CENTER);
        } else {
            if (isResidualLocationMode(mode) && externalLegendPanel != null) {
                residualPlotContainer.add(externalLegendPanel, BorderLayout.NORTH);
            }
            residualPlotContainer.add(chartPanel, BorderLayout.CENTER);
        }
        requestRepaint();
    }
    
    /**
     * Updates all charts in split view.
     */
    private void updateSplitViewCharts() {
        for (EventChartInfo chartInfo : eventChartMap.values()) {
            XYPlot plot = chartInfo.chart.getXYPlot();
            NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
            yAxis.setAutoRange(autoScale);
            requestRepaint();
        }
    }
    
    /**
     * Clears all data from the chart.
     */
    public void clearData() {
        residualSeries.clear();
        residualSeries.add(0, 0);
        
        if (mcmcLikelihoodSeries != null) {
            mcmcLikelihoodSeries.clear();
        }
        if (trdClusterSeries != null) {
            trdClusterSeries.clear();
        }
        
        eventSeriesMap.clear();
        activeEventName = null;
        if (activeEventLabel != null) {
            activeEventLabel.setText("Active: None");
        }
        
        eventChartMap.clear();
        splitViewPanel.removeAll();
        
        dataset.removeAllSeries();
        
        dataset.addSeries(residualSeries);
        if (mcmcLikelihoodSeries != null) {
            dataset.addSeries(mcmcLikelihoodSeries);
        }
        if (trdClusterSeries != null) {
            dataset.addSeries(trdClusterSeries);
        }
        
        updateChart();
        updateViewMode();
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
     * Exports either residual plot or k-distance graph depending on which is currently displayed.
     * For residual view, exports the actually visible component (single chart or split view panel).
     */
    private void exportChartImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Chart Image");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "PNG Images", "png"));
        java.awt.Window parent = SwingUtilities.getWindowAncestor(this);
        
        if ("CLS".equals(mode) && kDistancePlotPanel != null) {
            fileChooser.setSelectedFile(new java.io.File("kdistance.png"));
            if (fileChooser.showSaveDialog(parent != null ? parent : this) == JFileChooser.APPROVE_OPTION) {
                try {
                    java.io.File file = fileChooser.getSelectedFile();
                    kDistancePlotPanel.exportChartImageToFile(file);
                    log.info("Solver chart export saved: " + file.getAbsolutePath());
                } catch (Exception e) {
                    log.log(Level.WARNING, "Solver chart export failed", e);
                }
            }
        } else {
            fileChooser.setSelectedFile(new java.io.File("residual_plot.png"));
            if (fileChooser.showSaveDialog(parent != null ? parent : this) == JFileChooser.APPROVE_OPTION) {
                try {
                    java.io.File file = fileChooser.getSelectedFile();
                    if (!file.getName().toLowerCase().endsWith(".png")) {
                        file = new java.io.File(file.getAbsolutePath() + ".png");
                    }
                    int width = 1200;
                    int height = 800;
                    BufferedImage image = createVisibleResidualImage(width, height);
                    if (image != null) {
                        javax.imageio.ImageIO.write(image, "png", file);
                        log.info("Solver chart export saved: " + file.getAbsolutePath());
                    } else {
                        log.warning("Solver chart export: no chart visible to capture.");
                    }
                } catch (Exception e) {
                    log.log(Level.WARNING, "Solver chart export failed", e);
                }
            }
        }
    }
    
    /**
     * Renders the currently visible residual view (single chart or split view) to a BufferedImage.
     */
    private BufferedImage createVisibleResidualImage(int width, int height) {
        if ("CLS".equals(mode) && kDistancePlotPanel != null) {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);
            kDistancePlotPanel.setSize(width, height);
            kDistancePlotPanel.doLayout();
            kDistancePlotPanel.printAll(g2);
            g2.dispose();
            return img;
        }
        if (residualPlotContainer == null || residualPlotContainer.getComponentCount() == 0) {
            return chart != null ? chart.createBufferedImage(width, height) : null;
        }
        JPanel residualContainer = residualPlotContainer;
        if (residualContainer.getComponentCount() == 0) {
            return chart != null ? chart.createBufferedImage(width, height) : null;
        }
        for (Component visible : residualContainer.getComponents()) {
            if (visible instanceof ChartPanel) {
                JFreeChart ch = ((ChartPanel) visible).getChart();
                return ch != null ? ch.createBufferedImage(width, height) : null;
            }
            if (visible instanceof JScrollPane) {
                Component view = ((JScrollPane) visible).getViewport().getView();
                int w = Math.max(width, view.getWidth() > 0 ? view.getWidth() : width);
                int h = Math.max(height, view.getHeight() > 0 ? view.getHeight() : height);
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = img.createGraphics();
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, w, h);
                view.paint(g2);
                g2.dispose();
                return img;
            }
        }
        return chart != null ? chart.createBufferedImage(width, height) : null;
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

