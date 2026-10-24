package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComponent;

/**
 * Stacked SAC waveform canvas (no JFreeChart). Supports raw + CF / envelope overlays,
 * P/S markers, hover, and optional click picking when manual-edit is enabled.
 */
public class WaveformCanvas extends JComponent {

    public static final Color COLOR_P = Color.MAGENTA;
    public static final Color COLOR_S = Color.CYAN;
    public static final Color COLOR_CF = new Color(230, 126, 34, 200);
    public static final Color COLOR_ENV = new Color(39, 174, 96, 190);

    /** Overlay series (CF / envelope) with its own clock — may differ from the raw component. */
    public static final class Overlay {
        public final long startMillis;
        public final double deltaSec;
        public final double[] y;

        public Overlay(long startMillis, double deltaSec, double[] y) {
            this.startMillis = startMillis;
            this.deltaSec = deltaSec <= 0 ? 0.01 : deltaSec;
            this.y = y == null ? new double[0] : y;
        }

        public long sampleMillis(int i) {
            return startMillis + Math.round(i * deltaSec * 1000.0);
        }
    }

    public static final class Trace {
        public final String station;
        public final String component;
        public final String label;
        public final Color color;
        public final long startMillis;
        public final double deltaSec;
        public final float[] raw;
        public Overlay cf;
        public Overlay envelope;

        public Trace(String station, String component, Color color,
                     long startMillis, double deltaSec, float[] raw) {
            this.station = station == null ? "" : station.replace(" ", "");
            this.component = component == null ? "" : component.trim();
            this.label = (this.station + "__" + this.component);
            this.color = color == null ? Color.DARK_GRAY : color;
            this.startMillis = startMillis;
            this.deltaSec = deltaSec <= 0 ? 0.01 : deltaSec;
            this.raw = raw == null ? new float[0] : raw;
        }

        public long sampleMillis(int i) {
            return startMillis + Math.round(i * deltaSec * 1000.0);
        }

        public int npts() {
            return raw.length;
        }
    }

    public interface HoverListener {
        void onHover(int seriesIndex, int sampleIndex);

        void onLeave();
    }

    public interface PickListener {
        void onPick(int seriesIndex, int sampleIndex, String phase, Date time);
    }

    public interface PhaseResolver {
        String phaseFor(MouseEvent e, boolean keyP, boolean keyS);
    }

    private final List<Trace> traces = new ArrayList<>();
    private final Map<String, Date> pPicks = new HashMap<>();
    private final Map<String, Date> sPicks = new HashMap<>();
    /** Automatic picks waiting to be applied; drawn dashed over the waveforms. */
    private final Map<String, Date> pProposals = new HashMap<>();
    private final Map<String, Date> sProposals = new HashMap<>();

    private boolean showCf;
    private boolean showEnvelope;
    private boolean manualEditEnabled;
    private boolean panMode;

    private long viewT0 = Long.MIN_VALUE;
    private long viewT1 = Long.MAX_VALUE;
    private long dataT0;
    private long dataT1;

    private int hoverSeries = -1;
    private int hoverSample = -1;
    private boolean keyP;
    private boolean keyS;

    private HoverListener hoverListener;
    private PickListener pickListener;
    private PhaseResolver phaseResolver;
    private int buttonP = MouseEvent.BUTTON1;
    private int buttonS = MouseEvent.BUTTON3;
    private int buttonContext = MouseEvent.BUTTON2;
    private int keyCodeP = KeyEvent.VK_P;
    private int keyCodeS = KeyEvent.VK_S;

    private int dragX = -1;
    private long dragT0;
    private long dragT1;
    private boolean selecting;
    private int selectX0 = -1;
    private int selectX1 = -1;

    private static final int LEFT = 92;
    private static final int RIGHT = 16;
    private static final int TOP = 8;
    private static final int BOTTOM = 28;

    public WaveformCanvas() {
        setOpaque(true);
        setBackground(AppPanelStyle.getContentBg());
        setFocusable(true);
        setPreferredSize(new Dimension(640, 360));
        updateCursor();

        addMouseWheelListener(this::onWheel);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (!manualEditEnabled && !e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
                    if (panMode || e.isAltDown() || e.isControlDown()) {
                        dragX = e.getX();
                        dragT0 = viewT0;
                        dragT1 = viewT1;
                    } else {
                        selecting = true;
                        selectX0 = selectX1 = e.getX();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (selecting) {
                    int a = Math.min(selectX0, selectX1);
                    int b = Math.max(selectX0, selectX1);
                    selecting = false;
                    selectX0 = selectX1 = -1;
                    if (b - a > 8) {
                        zoomTo(xToMillis(a), xToMillis(b));
                    } else {
                        repaint();
                    }
                }
                dragX = -1;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!manualEditEnabled && e.getClickCount() >= 2 && e.getButton() == MouseEvent.BUTTON1) {
                    resetView();
                    return;
                }
                if (!manualEditEnabled || e.isPopupTrigger()) {
                    return;
                }
                int[] hit = hitTest(e.getX(), e.getY());
                if (hit == null) {
                    return;
                }
                String phase = resolvePhase(e);
                if (phase == null) {
                    return;
                }
                applyPickAt(hit[0], hit[1], phase);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverSeries = -1;
                hoverSample = -1;
                if (hoverListener != null) {
                    hoverListener.onLeave();
                }
                repaint();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (manualEditEnabled) {
                    return;
                }
                if (selecting) {
                    selectX1 = e.getX();
                    repaint();
                    return;
                }
                if (dragX < 0) {
                    return;
                }
                int plotW = Math.max(1, getWidth() - LEFT - RIGHT);
                long span = Math.max(1L, dragT1 - dragT0);
                long shift = Math.round(-(e.getX() - dragX) * (span / (double) plotW));
                setView(dragT0 + shift, dragT1 + shift);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int[] hit = hitTest(e.getX(), e.getY());
                if (hit == null) {
                    if (hoverSeries >= 0 && hoverListener != null) {
                        hoverListener.onLeave();
                    }
                    hoverSeries = -1;
                    hoverSample = -1;
                    repaint();
                    return;
                }
                if (hit[0] != hoverSeries || hit[1] != hoverSample) {
                    hoverSeries = hit[0];
                    hoverSample = hit[1];
                    if (hoverListener != null) {
                        hoverListener.onHover(hit[0], hit[1]);
                    }
                    repaint();
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == keyCodeP) {
                    keyP = true;
                }
                if (e.getKeyCode() == keyCodeS) {
                    keyS = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == keyCodeP) {
                    keyP = false;
                }
                if (e.getKeyCode() == keyCodeS) {
                    keyS = false;
                }
            }
        });
    }

    public void setHoverListener(HoverListener listener) {
        this.hoverListener = listener;
    }

    public void setPickListener(PickListener listener) {
        this.pickListener = listener;
    }

    public void setPhaseResolver(PhaseResolver resolver) {
        this.phaseResolver = resolver;
    }

    public void setPickButtons(int buttonP, int buttonS, int buttonContext) {
        this.buttonP = buttonP;
        this.buttonS = buttonS;
        this.buttonContext = buttonContext;
    }

    public void setPickKeys(int keyCodeP, int keyCodeS) {
        this.keyCodeP = keyCodeP;
        this.keyCodeS = keyCodeS;
    }

    public void setShowCf(boolean showCf) {
        this.showCf = showCf;
        repaint();
    }

    public void setShowEnvelope(boolean showEnvelope) {
        this.showEnvelope = showEnvelope;
        repaint();
    }

    public void setManualEditEnabled(boolean enabled) {
        this.manualEditEnabled = enabled;
        if (enabled) {
            this.panMode = false;
        }
        updateCursor();
        repaint();
    }

    public boolean isManualEditEnabled() {
        return manualEditEnabled;
    }

    public void setPanMode(boolean panMode) {
        this.panMode = panMode;
        if (panMode) {
            this.manualEditEnabled = false;
        }
        updateCursor();
        repaint();
    }

    public boolean isPanMode() {
        return panMode;
    }

    public List<Trace> getTraces() {
        return Collections.unmodifiableList(traces);
    }

    public int getHoverSeries() {
        return hoverSeries;
    }

    public int getHoverSample() {
        return hoverSample;
    }

    public void setTraces(List<Trace> next) {
        traces.clear();
        if (next != null) {
            traces.addAll(next);
        }
        dataT0 = Long.MAX_VALUE;
        dataT1 = Long.MIN_VALUE;
        for (Trace t : traces) {
            if (t.npts() == 0) {
                continue;
            }
            dataT0 = Math.min(dataT0, t.startMillis);
            dataT1 = Math.max(dataT1, t.sampleMillis(t.npts() - 1));
        }
        if (dataT0 >= dataT1) {
            dataT0 = 0;
            dataT1 = 1000;
        }
        resetView();
    }

    public void setPicks(Map<String, Date> p, Map<String, Date> s) {
        pPicks.clear();
        sPicks.clear();
        if (p != null) {
            pPicks.putAll(p);
        }
        if (s != null) {
            sPicks.putAll(s);
        }
        repaint();
    }

    /** Sets the proposed (not yet applied) P and S times by station; null or empty clears them. */
    public void setProposals(Map<String, Date> p, Map<String, Date> s) {
        pProposals.clear();
        sProposals.clear();
        if (p != null) {
            pProposals.putAll(p);
        }
        if (s != null) {
            sProposals.putAll(s);
        }
        repaint();
    }

    public void resetView() {
        setView(dataT0, dataT1);
    }

    /** Zooms the time axis to {@code [t0, t1]} (clamped to the data). */
    public void zoomTo(long t0, long t1) {
        setView(t0, t1);
    }

    /**
     * Applies a pick when manual-edit is on. Returns {@code true} if the listener fired.
     */
    public boolean applyPickAt(int seriesIndex, int sampleIndex, String phase) {
        if (!manualEditEnabled || phase == null) {
            return false;
        }
        if (seriesIndex < 0 || seriesIndex >= traces.size()) {
            return false;
        }
        Trace t = traces.get(seriesIndex);
        if (sampleIndex < 0 || sampleIndex >= t.npts()) {
            return false;
        }
        Date time = new Date(t.sampleMillis(sampleIndex));
        if ("P".equals(phase)) {
            pPicks.put(t.station, time);
        } else if ("S".equals(phase)) {
            sPicks.put(t.station, time);
        } else {
            return false;
        }
        if (pickListener != null) {
            pickListener.onPick(seriesIndex, sampleIndex, phase, time);
        }
        repaint();
        return true;
    }

    private void updateCursor() {
        int kind = Cursor.E_RESIZE_CURSOR;
        if (manualEditEnabled) {
            kind = Cursor.CROSSHAIR_CURSOR;
        } else if (panMode) {
            kind = Cursor.MOVE_CURSOR;
        }
        setCursor(Cursor.getPredefinedCursor(kind));
    }

    private void onWheel(MouseWheelEvent e) {
        if (manualEditEnabled || traces.isEmpty()) {
            return;
        }
        long span = Math.max(1L, viewT1 - viewT0);
        double factor = e.getWheelRotation() < 0 ? 0.8 : 1.25;
        long newSpan = Math.max(20L, Math.min(dataT1 - dataT0, Math.round(span * factor)));
        double frac = (e.getX() - LEFT) / (double) Math.max(1, getWidth() - LEFT - RIGHT);
        frac = Math.max(0, Math.min(1, frac));
        long center = viewT0 + Math.round(span * frac);
        long n0 = center - Math.round(newSpan * frac);
        setView(n0, n0 + newSpan);
    }

    private void setView(long t0, long t1) {
        long span = Math.max(20L, t1 - t0);
        long min = dataT0;
        long max = dataT1;
        if (span > max - min) {
            viewT0 = min;
            viewT1 = max;
        } else {
            if (t0 < min) {
                t0 = min;
                t1 = t0 + span;
            }
            if (t1 > max) {
                t1 = max;
                t0 = t1 - span;
            }
            viewT0 = t0;
            viewT1 = t1;
        }
        repaint();
    }

    private String resolvePhase(MouseEvent e) {
        if (phaseResolver != null) {
            return phaseResolver.phaseFor(e, keyP, keyS);
        }
        if (keyCodeP != 0 && keyP) {
            return "P";
        }
        if (keyCodeS != 0 && keyS) {
            return "S";
        }
        int b = e.getButton();
        if (b == buttonContext) {
            return null;
        }
        if (b == buttonP) {
            return "P";
        }
        if (b == buttonS) {
            return "S";
        }
        return null;
    }

    int[] hitTest(int px, int py) {
        if (traces.isEmpty()) {
            return null;
        }
        int w = getWidth();
        int h = getHeight();
        int plotW = w - LEFT - RIGHT;
        int plotH = h - TOP - BOTTOM;
        if (plotW < 8 || plotH < 8 || px < LEFT || px > LEFT + plotW) {
            return null;
        }
        int n = traces.size();
        double rowH = plotH / (double) n;
        int series = (int) Math.floor((py - TOP) / rowH);
        if (series < 0 || series >= n) {
            return null;
        }
        Trace t = traces.get(series);
        if (t.npts() == 0) {
            return null;
        }
        long tAt = viewT0 + Math.round((px - LEFT) / (double) plotW * (viewT1 - viewT0));
        int sample = (int) Math.round((tAt - t.startMillis) / (t.deltaSec * 1000.0));
        if (sample < 0 || sample >= t.npts()) {
            return null;
        }
        return new int[] { series, sample };
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
        if (traces.isEmpty()) {
            g.setColor(new Color(120, 120, 120));
            g.drawString("No waveforms", LEFT, h / 2);
            g.dispose();
            return;
        }
        int plotW = Math.max(1, w - LEFT - RIGHT);
        int plotH = Math.max(1, h - TOP - BOTTOM);
        int n = traces.size();
        double rowH = plotH / (double) n;
        Font labelFont = new Font("SansSerif", Font.PLAIN, 10);
        g.setFont(labelFont);
        FontMetrics fm = g.getFontMetrics();

        g.setColor(new Color(230, 230, 232));
        g.drawRect(LEFT, TOP, plotW, plotH);

        for (int i = 0; i < n; i++) {
            Trace t = traces.get(i);
            int y0 = TOP + (int) Math.round(i * rowH);
            int y1 = TOP + (int) Math.round((i + 1) * rowH);
            int mid = (y0 + y1) / 2;
            if (i % 2 == 1) {
                g.setColor(new Color(248, 248, 250));
                g.fillRect(LEFT + 1, y0, plotW - 1, Math.max(1, y1 - y0));
            }
            g.setColor(new Color(70, 70, 74));
            String lab = t.label;
            int lw = fm.stringWidth(lab);
            g.drawString(lab, Math.max(4, LEFT - 6 - lw), mid + fm.getAscent() / 2 - 2);

            if (showEnvelope && t.envelope != null) {
                drawOverlay(g, t.envelope, COLOR_ENV, y0, y1, plotW, 1.4f);
            }
            if (showCf && t.cf != null) {
                drawOverlay(g, t.cf, COLOR_CF, y0, y1, plotW, 1.6f);
            }
            drawRaw(g, t, t.color, y0, y1, plotW, 1.1f);
            drawPick(g, t.station, pPicks, COLOR_P, y0, y1);
            drawPick(g, t.station, sPicks, COLOR_S, y0, y1);
            drawProposal(g, t.station, pProposals, COLOR_P, y0, y1);
            drawProposal(g, t.station, sProposals, COLOR_S, y0, y1);
        }

        if (hoverSeries >= 0 && hoverSeries < n && hoverSample >= 0) {
            Trace t = traces.get(hoverSeries);
            if (hoverSample < t.npts()) {
                int x = xOf(t.sampleMillis(hoverSample), plotW);
                g.setColor(new Color(120, 120, 120, 180));
                Stroke old = g.getStroke();
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[] { 5f, 4f }, 0f));
                g.drawLine(x, TOP, x, TOP + plotH);
                g.setStroke(old);
            }
        }

        if (selecting && selectX0 >= 0 && selectX1 >= 0) {
            int a = Math.min(selectX0, selectX1);
            int b = Math.max(selectX0, selectX1);
            g.setColor(new Color(59, 130, 246, 50));
            g.fillRect(a, TOP, Math.max(1, b - a), plotH);
            g.setColor(new Color(59, 130, 246, 180));
            g.drawRect(a, TOP, Math.max(1, b - a), plotH);
        }

        drawTimeAxis(g, plotW, plotH);
        g.dispose();
    }

    private long xToMillis(int px) {
        int plotW = Math.max(1, getWidth() - LEFT - RIGHT);
        double frac = (px - LEFT) / (double) plotW;
        frac = Math.max(0, Math.min(1, frac));
        return viewT0 + Math.round(frac * (viewT1 - viewT0));
    }

    private void drawRaw(Graphics2D g, Trace t, Color color, int y0, int y1, int plotW, float stroke) {
        float[] y = t.raw;
        if (y.length < 2) {
            return;
        }
        int i0 = indexAt(t.startMillis, t.deltaSec, y.length, viewT0);
        int i1 = indexAt(t.startMillis, t.deltaSec, y.length, viewT1);
        if (i1 <= i0) {
            i0 = 0;
            i1 = y.length - 1;
        }
        double max = visibleAbsMax(y, i0, i1);
        if (max == 0) {
            max = 1;
        }
        int mid = (y0 + y1) / 2;
        double amp = 0.42 * (y1 - y0);
        int nVis = Math.max(1, i1 - i0 + 1);
        int step = Math.max(1, nVis / Math.max(1, plotW * 3));
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke));
        int prevX = -1;
        int prevY = 0;
        for (int i = i0; i <= i1; i += step) {
            long tm = t.sampleMillis(i);
            int x = xOf(tm, plotW);
            int yy = mid - (int) Math.round((y[i] / (2 * max)) * 2 * amp);
            if (prevX >= 0) {
                g.drawLine(prevX, prevY, x, yy);
            }
            prevX = x;
            prevY = yy;
        }
    }

    private void drawOverlay(Graphics2D g, Overlay o, Color color, int y0, int y1, int plotW, float stroke) {
        if (o == null || o.y.length < 2) {
            return;
        }
        int i0 = indexAt(o.startMillis, o.deltaSec, o.y.length, viewT0);
        int i1 = indexAt(o.startMillis, o.deltaSec, o.y.length, viewT1);
        if (i1 <= i0) {
            i0 = 0;
            i1 = o.y.length - 1;
        }
        double max = 0;
        for (int i = i0; i <= i1; i++) {
            max = Math.max(max, Math.abs(o.y[i]));
        }
        if (max == 0) {
            max = 1;
        }
        int mid = (y0 + y1) / 2;
        double amp = 0.42 * (y1 - y0);
        int nVis = Math.max(1, i1 - i0 + 1);
        int step = Math.max(1, nVis / Math.max(1, plotW * 3));
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke));
        int prevX = -1;
        int prevY = 0;
        for (int i = i0; i <= i1; i += step) {
            long tm = o.sampleMillis(i);
            int x = xOf(tm, plotW);
            int yy = mid - (int) Math.round((o.y[i] / (2 * max)) * 2 * amp);
            if (prevX >= 0) {
                g.drawLine(prevX, prevY, x, yy);
            }
            prevX = x;
            prevY = yy;
        }
    }

    static int indexAt(long startMillis, double deltaSec, int npts, long tMillis) {
        if (npts <= 0) {
            return 0;
        }
        int i = (int) Math.round((tMillis - startMillis) / (Math.max(1e-9, deltaSec) * 1000.0));
        if (i < 0) {
            return 0;
        }
        if (i > npts - 1) {
            return npts - 1;
        }
        return i;
    }

    static double visibleAbsMax(float[] y, int i0, int i1) {
        double max = 0;
        int a = Math.max(0, i0);
        int b = Math.min(y.length - 1, i1);
        for (int i = a; i <= b; i++) {
            max = Math.max(max, Math.abs(y[i]));
        }
        return max;
    }

    private void drawPick(Graphics2D g, String station, Map<String, Date> picks, Color color, int y0, int y1) {
        Date d = picks.get(station);
        if (d == null) {
            return;
        }
        long tm = d.getTime();
        if (tm < viewT0 || tm > viewT1) {
            return;
        }
        int x = xOf(tm, Math.max(1, getWidth() - LEFT - RIGHT));
        g.setColor(color);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x, y0 + 1, x, y1 - 1);
    }

    private void drawProposal(Graphics2D g, String station, Map<String, Date> proposals, Color color, int y0, int y1) {
        Date d = proposals.get(station);
        if (d == null) {
            return;
        }
        long tm = d.getTime();
        if (tm < viewT0 || tm > viewT1) {
            return;
        }
        int x = xOf(tm, Math.max(1, getWidth() - LEFT - RIGHT));
        java.awt.Stroke old = g.getStroke();
        g.setColor(color);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[] {5f, 4f}, 0f));
        g.drawLine(x, y0 + 1, x, y1 - 1);
        g.setStroke(old);
    }

    private int xOf(long millis, int plotW) {
        double span = Math.max(1, viewT1 - viewT0);
        return LEFT + (int) Math.round((millis - viewT0) / span * plotW);
    }

    private void drawTimeAxis(Graphics2D g, int plotW, int plotH) {
        g.setColor(new Color(80, 80, 84));
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
        int ticks = 6;
        for (int i = 0; i <= ticks; i++) {
            long tm = viewT0 + Math.round((viewT1 - viewT0) * (i / (double) ticks));
            int x = xOf(tm, plotW);
            g.drawLine(x, TOP + plotH, x, TOP + plotH + 4);
            String lab = fmt.format(new Date(tm));
            int tw = g.getFontMetrics().stringWidth(lab);
            g.drawString(lab, x - tw / 2, TOP + plotH + 18);
        }
    }
}
