package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.AppSettingsCache;
import com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings;
import com.treloc.xtreloc.app.gui.util.OverlayHandle;
import com.treloc.xtreloc.app.gui.util.UiFonts;
import com.treloc.xtreloc.util.ModeNameMapper;
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
import java.awt.event.HierarchyEvent;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Solver convergence plot: custom canvas (no JFreeChart) so iteration updates stay cheap.
 */
public class ResidualPlotPanel extends JPanel {
    private static final Logger log = Logger.getLogger(ResidualPlotPanel.class.getName());
    private static final Color RESIDUAL_COLOR = new Color(50, 150, 200);
    private static final Color LIKELIHOOD_COLOR = new Color(200, 100, 50);
    private static final Color TRD_CLUSTER_COLOR = new Color(150, 200, 100);
    private static final Color INACTIVE = new Color(200, 200, 200);

    private final Map<String, Polyline> residuals = new ConcurrentHashMap<>();
    private final Map<String, Polyline> likelihoods = new ConcurrentHashMap<>();
    private final Polyline defaultResidual = new Polyline("Residual", RESIDUAL_COLOR, false);
    private final PlotCanvas canvas = new PlotCanvas();
    private final JPanel residualPlotContainer = new JPanel(new BorderLayout());
    private final JLabel activeEventLabel = new JLabel("Active: None");
    private final KDistancePlotPanel kDistancePlotPanel = new KDistancePlotPanel();

    private String mode;
    private volatile String activeEventName;
    private int maxDataPoints = 1000;
    private int maxHistoryEvents = 3;
    private int totalEventCount;
    private int completedEventCount;
    private boolean autoScale = true;
    private boolean showLegend = true;
    private final OverlayHandle legendHandle = new OverlayHandle(OverlayHandle.Corner.NW, 8);
    private String pendingBorderTitle = "Residual Convergence Plot";
    private boolean pendingEventLabelVisible = true;

    private final ConcurrentLinkedQueue<Sample> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private Timer flushTimer;

    public ResidualPlotPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Residual Convergence Plot"));
        AppPanelStyle.setPanelBackground(this);
        activeEventLabel.setFont(UiFonts.uiPlain(11f));
        JPanel control = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        AppPanelStyle.setPanelBackground(control);
        control.add(activeEventLabel);

        residualPlotContainer.setBackground(AppPanelStyle.getPanelBg());
        residualPlotContainer.add(canvas, BorderLayout.CENTER);
        JPopupMenu menu = new JPopupMenu();
        canvas.setComponentPopupMenu(menu);
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    fillMenu(menu);
                    menu.show(canvas, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    fillMenu(menu);
                    menu.show(canvas, e.getX(), e.getY());
                }
            }
        });

        add(control, BorderLayout.NORTH);
        add(residualPlotContainer, BorderLayout.CENTER);
        setMinimumSize(new Dimension(200, 120));
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                applyPendingChrome();
            }
        });
    }

    /**
     * Updates series chrome for the solver method. Does not request focus or revalidate
     * while this panel is in a hidden viewer tab (that would steal the JTabbedPane selection).
     */
    public void setMode(String mode) {
        this.mode = mode;
        if ("CLS".equals(mode)) {
            String title = ModeNameMapper.getDisplayName("CLS");
            pendingBorderTitle = (title == null ? "CLS" : title) + " — k-distance";
            pendingEventLabelVisible = false;
            updateViewMode();
            applyChromeIfShowing();
            return;
        }
        pendingBorderTitle = "Residual Convergence Plot";
        pendingEventLabelVisible = true;
        clearSeries();
        updateViewMode();
        applyChromeIfShowing();
        requestVisibleRepaint();
    }

    private void applyChromeIfShowing() {
        if (isShowing()) {
            applyPendingChrome();
        }
    }

    private void applyPendingChrome() {
        setBorder(BorderFactory.createTitledBorder(pendingBorderTitle));
        activeEventLabel.setVisible(pendingEventLabelVisible);
        residualPlotContainer.revalidate();
        residualPlotContainer.repaint();
    }

    public void showKDistanceGraph(List<Double> kDistances, double estimatedEps) {
        SwingUtilities.invokeLater(() -> {
            kDistancePlotPanel.setKDistances(kDistances, estimatedEps);
            updateViewMode();
        });
    }

    public void addResidualPoint(int iteration, double residual) {
        enqueue(Sample.RESIDUAL, activeEventName, iteration, residual);
    }

    public void addResidualPoint(String eventName, int iteration, double residual) {
        enqueue(Sample.RESIDUAL, eventName, iteration, residual);
    }

    public void addLikelihoodPoint(int sample, double logLikelihood) {
        enqueue(Sample.LIKELIHOOD, activeEventName, sample, logLikelihood);
    }

    public void addLikelihoodPoint(String eventName, int sample, double logLikelihood) {
        enqueue(Sample.LIKELIHOOD, eventName, sample, logLikelihood);
    }

    public void startProcessingEvent(String eventName) {
        activeEventName = eventName;
        runEdt(() -> {
            Polyline line = residuals.computeIfAbsent(eventName, n -> new Polyline(n, RESIDUAL_COLOR, false));
            line.active = true;
            line.color = RESIDUAL_COLOR;
            line.dashed = false;
            updateActiveEventStatusLabel();
            requestVisibleRepaint();
        });
    }

    public void stopProcessingEvent(String eventName) {
        runEdt(() -> {
            Polyline line = residuals.get(eventName);
            if (line != null) {
                line.active = false;
                line.color = INACTIVE;
                line.dashed = true;
            }
            completedEventCount = Math.min(totalEventCount, completedEventCount + 1);
            updateActiveEventStatusLabel();
            cleanupOldEvents();
            requestVisibleRepaint();
        });
    }

    public void setActiveEvent(String eventName) {
        startProcessingEvent(eventName);
    }

    public void markEventCompleted(String eventName) {
        stopProcessingEvent(eventName);
    }

    public void setTotalEventCount(int total) {
        totalEventCount = Math.max(total, 0);
        completedEventCount = 0;
        updateActiveEventStatusLabel();
    }

    public void clearData() {
        pending.clear();
        pendingCount.set(0);
        clearSeries();
        activeEventName = null;
        totalEventCount = 0;
        completedEventCount = 0;
        updateActiveEventStatusLabel();
        updateViewMode();
        requestVisibleRepaint();
    }

    public void setAutoScale(boolean on) {
        autoScale = on;
        if (isShowing()) {
            canvas.repaint();
        }
    }

    public void setShowLegend(boolean on) {
        showLegend = on;
        if (isShowing()) {
            canvas.repaint();
        }
    }

    public void exportChartImage() {
        exportImage();
    }

    private void clearSeries() {
        residuals.clear();
        likelihoods.clear();
        defaultResidual.clear();
    }

    private void updateViewMode() {
        residualPlotContainer.removeAll();
        if ("CLS".equals(mode)) {
            residualPlotContainer.add(kDistancePlotPanel, BorderLayout.CENTER);
        } else {
            residualPlotContainer.add(canvas, BorderLayout.CENTER);
        }
        if (isShowing()) {
            residualPlotContainer.revalidate();
            residualPlotContainer.repaint();
        }
    }

    private void updateActiveEventStatusLabel() {
        if (totalEventCount <= 0) {
            activeEventLabel.setText("Active: None");
            return;
        }
        activeEventLabel.setText(String.format(Locale.ROOT, "Active: %d/%d (%.1f%%)",
            completedEventCount, totalEventCount, 100.0 * completedEventCount / totalEventCount));
    }

    private void cleanupOldEvents() {
        List<Polyline> inactive = new ArrayList<>();
        for (Polyline p : residuals.values()) {
            if (!p.active) {
                inactive.add(p);
            }
        }
        Collections.sort(inactive, (a, b) -> Long.compare(a.lastUpdate, b.lastUpdate));
        while (inactive.size() > maxHistoryEvents) {
            Polyline oldest = inactive.remove(0);
            residuals.remove(oldest.name);
            likelihoods.remove(oldest.name);
        }
    }

    private static int throttleMs() {
        try {
            int v = AppSettingsCache.chartAppearance().getConvergenceRepaintThrottleMs();
            if (v <= 0) {
                return 250;
            }
            return Math.max(80, Math.min(60_000, v));
        } catch (RuntimeException e) {
            return 250;
        }
    }

    private static final class Sample {
        static final byte RESIDUAL = 0;
        static final byte LIKELIHOOD = 1;
        final byte kind;
        final String event;
        final float x;
        final float y;

        Sample(byte kind, String event, float x, float y) {
            this.kind = kind;
            this.event = event;
            this.x = x;
            this.y = y;
        }
    }

    private void enqueue(byte kind, String event, double x, double y) {
        int n = pendingCount.get();
        if (n > 6000 && (n & 3) != 0) {
            return;
        }
        pending.add(new Sample(kind, event, (float) x, (float) y));
        pendingCount.incrementAndGet();
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            armFlushTimer();
        } else {
            SwingUtilities.invokeLater(this::armFlushTimer);
        }
    }

    private void armFlushTimer() {
        if (flushTimer == null) {
            flushTimer = new Timer(throttleMs(), e -> flushPending());
            flushTimer.setRepeats(false);
        }
        flushTimer.setInitialDelay(throttleMs());
        if (!flushTimer.isRunning()) {
            flushTimer.start();
        }
    }

    private void flushPending() {
        flushScheduled.set(false);
        Sample s;
        int drained = 0;
        while ((s = pending.poll()) != null && drained < 4000) {
            pendingCount.decrementAndGet();
            applySample(s);
            drained++;
        }
        if (pending.peek() != null) {
            scheduleFlush();
        }
        if (drained > 0) {
            requestVisibleRepaint();
        }
    }

    private void applySample(Sample s) {
        if (s.kind == Sample.LIKELIHOOD) {
            String name = s.event == null ? "Log-Likelihood" : s.event;
            likelihoods.computeIfAbsent(name, n -> new Polyline(
                s.event == null ? n : n + " (LL)", LIKELIHOOD_COLOR, true))
                .add(s.x, s.y, maxDataPoints);
            return;
        }
        if (s.event == null) {
            defaultResidual.add(s.x, s.y, maxDataPoints);
            return;
        }
        Polyline line = residuals.computeIfAbsent(s.event, n -> new Polyline(n, RESIDUAL_COLOR, false));
        line.add(s.x, s.y, maxDataPoints);
        if (s.event.equals(activeEventName)) {
            line.active = true;
            line.color = RESIDUAL_COLOR;
        }
    }

    private void requestVisibleRepaint() {
        if (isShowing()) {
            canvas.repaint();
        }
    }

    private static void runEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private void fillMenu(JPopupMenu popup) {
        popup.removeAll();
        if ("CLS".equals(mode)) {
            JMenuItem export = new JMenuItem("Export image…");
            export.addActionListener(e -> exportImage());
            popup.add(export);
            return;
        }
        JCheckBoxMenuItem autoItem = new JCheckBoxMenuItem("Auto scale", autoScale);
        autoItem.addItemListener(e -> {
            autoScale = e.getStateChange() == ItemEvent.SELECTED;
            canvas.repaint();
        });
        popup.add(autoItem);
        popup.addSeparator();
        JMenuItem clear = new JMenuItem("Clear");
        clear.addActionListener(e -> clearData());
        popup.add(clear);
        JMenuItem export = new JMenuItem("Export image…");
        export.addActionListener(e -> exportImage());
        popup.add(export);
    }

    private void exportImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png"));
        fc.setSelectedFile(new java.io.File("CLS".equals(mode) ? "kdistance.png" : "residual_plot.png"));
        java.awt.Window parent = SwingUtilities.getWindowAncestor(this);
        if (fc.showSaveDialog(parent != null ? parent : this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            java.io.File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                file = new java.io.File(file.getAbsolutePath() + ".png");
            }
            if ("CLS".equals(mode)) {
                kDistancePlotPanel.exportChartImageToFile(file);
            } else {
                BufferedImage img = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 1200, 800);
                canvas.paintPlot(g, 1200, 800);
                g.dispose();
                javax.imageio.ImageIO.write(img, "png", file);
            }
            log.info("Solver chart export saved: " + file.getAbsolutePath());
            com.treloc.xtreloc.app.gui.util.GuiExecutionLog.info("Residual chart saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            log.log(Level.WARNING, "Solver chart export failed", e);
            com.treloc.xtreloc.app.gui.util.GuiExecutionLog.warning("Residual chart export failed: " + e.getMessage());
        }
    }

    private void drawOnCanvasLegend(Graphics2D g, int canvasW, int canvasH) {
        if (!showLegend || "CLS".equals(mode)) {
            return;
        }
        List<String> names = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        names.add("Residual");
        colors.add(RESIDUAL_COLOR);
        if ("MCMC".equals(mode)) {
            names.add("Log-Likelihood");
            colors.add(LIKELIHOOD_COLOR);
        } else if ("TRD".equals(mode)) {
            names.add("Per Cluster");
            colors.add(TRD_CLUSTER_COLOR);
        }
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        int rowH = Math.max(14, fm.getHeight());
        int box = 10;
        int pad = 8;
        int maxW = 0;
        for (String n : names) {
            maxW = Math.max(maxW, fm.stringWidth(n));
        }
        int lw = pad * 2 + box + 6 + maxW;
        int lh = pad * 2 + names.size() * rowH;
        int[] p = legendHandle.place(canvasW, canvasH, lw, lh);
        int x = p[0];
        int y = p[1];
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRoundRect(x, y, lw, lh, 6, 6);
        g.setColor(new Color(70, 75, 90, 140));
        g.drawRoundRect(x, y, lw, lh, 6, 6);
        int ty = y + pad + fm.getAscent();
        for (int i = 0; i < names.size(); i++) {
            g.setColor(colors.get(i));
            g.fillRect(x + pad, ty - fm.getAscent() + 2, box, box);
            g.setColor(new Color(35, 40, 52));
            g.drawString(names.get(i), x + pad + box + 6, ty);
            ty += rowH;
        }
    }

    static final class Polyline {
        final String name;
        float[] x = new float[64];
        float[] y = new float[64];
        int n;
        boolean active;
        boolean dashed;
        Color color;
        long lastUpdate;

        Polyline(String name, Color color, boolean dashed) {
            this.name = name;
            this.color = color;
            this.dashed = dashed;
        }

        synchronized void add(double px, double py, int max) {
            if (n == x.length) {
                x = java.util.Arrays.copyOf(x, x.length * 2);
                y = java.util.Arrays.copyOf(y, y.length * 2);
            }
            x[n] = (float) px;
            y[n] = (float) py;
            n++;
            if (n > max) {
                int keep = max;
                System.arraycopy(x, n - keep, x, 0, keep);
                System.arraycopy(y, n - keep, y, 0, keep);
                n = keep;
            }
            lastUpdate = System.currentTimeMillis();
        }

        synchronized void clear() {
            n = 0;
        }

        synchronized int copy(float[] xo, float[] yo) {
            int c = Math.min(n, xo.length);
            System.arraycopy(x, 0, xo, 0, c);
            System.arraycopy(y, 0, yo, 0, c);
            return c;
        }
    }

    private final class PlotCanvas extends JComponent {
        private double zoom = 1.0;
        private double panX;
        private double panY;

        PlotCanvas() {
            setOpaque(true);
            addMouseWheelListener((MouseWheelEvent e) -> {
                double n = e.getPreciseWheelRotation();
                if (Math.abs(n) < 1e-6) {
                    n = e.getWheelRotation();
                }
                zoom = Math.max(0.4, Math.min(20.0, zoom * Math.pow(1.12, -n)));
                repaint();
            });
            MouseAdapter drag = new MouseAdapter() {
                int lx;
                int ly;
                boolean movingLegend;

                @Override
                public void mousePressed(MouseEvent e) {
                    lx = e.getX();
                    ly = e.getY();
                    movingLegend = showLegend && legendHandle.contains(e.getX(), e.getY());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (movingLegend) {
                        legendHandle.moveTo(legendHandle.x() + e.getX() - lx, legendHandle.y() + e.getY() - ly,
                            getWidth(), getHeight());
                        lx = e.getX();
                        ly = e.getY();
                        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                        repaint();
                        return;
                    }
                    panX += e.getX() - lx;
                    panY += e.getY() - ly;
                    lx = e.getX();
                    ly = e.getY();
                    repaint();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    if (showLegend && legendHandle.contains(e.getX(), e.getY())) {
                        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                    } else {
                        setCursor(java.awt.Cursor.getDefaultCursor());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    movingLegend = false;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        zoom = 1.0;
                        panX = 0;
                        panY = 0;
                        repaint();
                    }
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                paintPlot(g, getWidth(), getHeight());
            } finally {
                g.dispose();
            }
        }

        void paintPlot(Graphics2D g, int w, int h) {
            g.setColor(AppPanelStyle.getPanelBg());
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            List<Polyline> left = collectLeft();
            List<Polyline> right = "MCMC".equals(mode) ? new ArrayList<>(likelihoods.values()) : List.of();
            if (left.isEmpty() && right.isEmpty()) {
                g.setColor(AppPanelStyle.getMutedTextColor());
                g.drawString("Residual convergence", 16, 24);
                return;
            }
            double[] xb = rangeX(left, right);
            double[] yb = rangeY(left);
            double[] y2 = rangeY(right);
            ChartAppearanceSettings s = AppSettingsCache.chartAppearance();
            int leftM = 56;
            int rightM = right.isEmpty() ? 16 : 56;
            int top = 28;
            int bot = 36;
            int cw = Math.max(10, w - leftM - rightM);
            int ch = Math.max(10, h - top - bot);
            g.setColor(s.getGridlineColorAsColor());
            Stroke prev = g.getStroke();
            g.setStroke(s.getGridlineStroke());
            for (int i = 1; i <= 4; i++) {
                int x = leftM + cw * i / 5;
                int y = top + ch * i / 5;
                g.drawLine(x, top, x, top + ch);
                g.drawLine(leftM, y, leftM + cw, y);
            }
            g.setStroke(prev);
            g.setColor(s.getAxisLineColorAsColor());
            g.drawLine(leftM, top + ch, leftM + cw, top + ch);
            g.drawLine(leftM, top, leftM, top + ch);
            if (!right.isEmpty()) {
                g.setColor(LIKELIHOOD_COLOR);
                g.drawLine(leftM + cw, top, leftM + cw, top + ch);
            }
            drawPolylines(g, left, xb, yb, leftM, top, cw, ch, s);
            drawPolylines(g, right, xb, y2, leftM, top, cw, ch, s);
            g.setFont(s.getTickLabelFont());
            g.setColor(RESIDUAL_COLOR);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i <= 4; i++) {
                double v = yb[0] + (yb[1] - yb[0]) * i / 4.0;
                String lab = String.format(Locale.ROOT, "%.3g", v);
                int y = top + ch - (int) (ch * i / 4.0);
                g.drawString(lab, leftM - fm.stringWidth(lab) - 4, y + fm.getAscent() / 2);
            }
            g.setColor(s.getAxisLineColorAsColor());
            for (int i = 0; i <= 4; i++) {
                double v = xb[0] + (xb[1] - xb[0]) * i / 4.0;
                String lab = String.format(Locale.ROOT, "%.0f", v);
                int x = leftM + cw * i / 4;
                g.drawString(lab, x - fm.stringWidth(lab) / 2, top + ch + fm.getAscent() + 4);
            }
            if (!right.isEmpty()) {
                g.setColor(LIKELIHOOD_COLOR);
                for (int i = 0; i <= 4; i++) {
                    double v = y2[0] + (y2[1] - y2[0]) * i / 4.0;
                    String lab = String.format(Locale.ROOT, "%.3g", v);
                    int y = top + ch - (int) (ch * i / 4.0);
                    g.drawString(lab, leftM + cw + 6, y + fm.getAscent() / 2);
                }
            }
            g.setFont(s.getAxisLabelFont());
            g.setColor(RESIDUAL_COLOR);
            g.drawString("RMS Residual (s)", 8, 16);
            g.setColor(s.getAxisLineColorAsColor());
            String title = title();
            g.setFont(s.getTitleFont());
            g.drawString(title, Math.max(leftM, (w - g.getFontMetrics().stringWidth(title)) / 2), 18);
            g.setFont(s.getAxisLabelFont());
            g.drawString("Iteration", leftM + cw / 2 - 20, h - 6);
            drawOnCanvasLegend(g, w, h);
        }

        private void drawPolylines(Graphics2D g, List<Polyline> lines, double[] xb, double[] yb,
                                   int left, int top, int cw, int ch, ChartAppearanceSettings s) {
            double dx = Math.max(1e-9, xb[1] - xb[0]);
            double dy = Math.max(1e-9, yb[1] - yb[0]);
            float[] xs = new float[Math.max(8, maxDataPoints + 8)];
            float[] ys = new float[xs.length];
            float w = Math.max(0.5f, s.getLineWidth());
            for (Polyline p : lines) {
                int n = p.copy(xs, ys);
                if (n < 2) {
                    continue;
                }
                g.setColor(p.color);
                g.setStroke(p.dashed
                    ? new BasicStroke(p.active ? w : Math.max(0.5f, w * 0.6f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[] {4, 4}, 0)
                    : new BasicStroke(p.active ? w : Math.max(0.5f, w * 0.7f)));
                int px = 0;
                int py = 0;
                for (int i = 0; i < n; i++) {
                    double tx = (xs[i] - xb[0]) / dx;
                    double ty = (ys[i] - yb[0]) / dy;
                    int x = left + (int) (tx * cw * zoom + panX);
                    int y = top + ch - (int) (ty * ch * zoom - panY);
                    if (i > 0) {
                        g.drawLine(px, py, x, y);
                    }
                    px = x;
                    py = y;
                }
            }
        }

        private List<Polyline> collectLeft() {
            List<Polyline> out = new ArrayList<>();
            if (defaultResidual.n > 0) {
                out.add(defaultResidual);
            }
            out.addAll(residuals.values());
            return out;
        }

        private double[] rangeX(List<Polyline> a, List<Polyline> b) {
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            for (List<Polyline> group : List.of(a, b)) {
                for (Polyline p : group) {
                    synchronized (p) {
                        for (int i = 0; i < p.n; i++) {
                            lo = Math.min(lo, p.x[i]);
                            hi = Math.max(hi, p.x[i]);
                        }
                    }
                }
            }
            if (!(hi > lo)) {
                return new double[] {0, 1};
            }
            double pad = 0.04 * (hi - lo);
            return new double[] {lo - pad, hi + pad};
        }

        private double[] rangeY(List<Polyline> lines) {
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            for (Polyline p : lines) {
                synchronized (p) {
                    for (int i = 0; i < p.n; i++) {
                        lo = Math.min(lo, p.y[i]);
                        hi = Math.max(hi, p.y[i]);
                    }
                }
            }
            if (!(hi > lo)) {
                return new double[] {0, 1};
            }
            double pad = autoScale ? 0.08 * (hi - lo) : 0;
            return new double[] {lo - pad, hi + pad};
        }

        private String title() {
            String n = ModeNameMapper.getDisplayName(mode);
            if ("MCMC".equals(mode)) {
                return (n == null ? "MCMC" : n) + " — Residual & Log-Likelihood";
            }
            if ("TRD".equals(mode)) {
                return (n == null ? "TRD" : n) + " — Residual (Per Cluster)";
            }
            return (n == null ? "Residual" : n) + " — Residual Convergence";
        }
    }
}


