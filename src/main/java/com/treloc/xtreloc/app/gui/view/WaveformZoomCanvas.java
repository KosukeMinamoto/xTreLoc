package com.treloc.xtreloc.app.gui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * Single-trace zoom view around a hovered sample, with that station's P/S markers.
 */
public class WaveformZoomCanvas extends JComponent {

    private WaveformCanvas.Trace trace;
    private int centerSample = -1;
    private double windowSeconds = 2.0;
    private Date pickP;
    private Date pickS;
    private boolean showRaw = true;
    private boolean showCf;
    private boolean showEnvelope;

    public WaveformZoomCanvas() {
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(640, 140));
    }

    public void setShowFlags(boolean raw, boolean cf, boolean envelope) {
        this.showRaw = raw;
        this.showCf = cf;
        this.showEnvelope = envelope;
        repaint();
    }

    public void clear() {
        this.trace = null;
        this.centerSample = -1;
        this.pickP = null;
        this.pickS = null;
        repaint();
    }

    public void setView(WaveformCanvas.Trace trace, int centerSample, double windowSeconds,
                        Date pickP, Date pickS) {
        this.trace = trace;
        this.centerSample = centerSample;
        this.windowSeconds = windowSeconds <= 0 ? 2.0 : windowSeconds;
        this.pickP = pickP;
        this.pickS = pickS;
        repaint();
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
        if (trace == null || centerSample < 0 || trace.npts() == 0) {
            g.setColor(new Color(140, 140, 140));
            g.drawString("Hover a trace to zoom", 12, h / 2);
            g.dispose();
            return;
        }
        int left = 48;
        int right = 12;
        int top = 8;
        int bottom = 22;
        int plotW = Math.max(1, w - left - right);
        int plotH = Math.max(1, h - top - bottom);
        int half = Math.max(1, (int) Math.round(windowSeconds / trace.deltaSec));
        int i0 = Math.max(0, centerSample - half);
        int i1 = Math.min(trace.npts() - 1, centerSample + half);
        long t0 = trace.sampleMillis(i0);
        long t1 = trace.sampleMillis(i1);
        if (t1 <= t0) {
            t1 = t0 + 1;
        }
        int mid = top + plotH / 2;

        g.setColor(new Color(230, 230, 232));
        g.drawRect(left, top, plotW, plotH);

        if (showEnvelope && trace.envelope != null) {
            drawOverlay(g, trace.envelope, t0, t1, left, plotW, mid, plotH, WaveformCanvas.COLOR_ENV, 1.4f);
        }
        if (showCf && trace.cf != null) {
            drawOverlay(g, trace.cf, t0, t1, left, plotW, mid, plotH, WaveformCanvas.COLOR_CF, 1.6f);
        }
        if (showRaw) {
            drawRaw(g, i0, i1, t0, t1, left, plotW, mid, plotH);
        }
        drawPick(g, pickP, WaveformCanvas.COLOR_P, t0, t1, left, plotW, top, plotH);
        drawPick(g, pickS, WaveformCanvas.COLOR_S, t0, t1, left, plotW, top, plotH);

        int cx = xOf(trace.sampleMillis(centerSample), t0, t1, left, plotW);
        g.setColor(new Color(120, 120, 120, 180));
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 5f, 4f }, 0));
        g.drawLine(cx, top, cx, top + plotH);
        g.setStroke(old);

        g.setColor(new Color(80, 80, 84));
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        g.drawString(fmt.format(new Date(t0)), left, top + plotH + 16);
        String end = fmt.format(new Date(t1));
        g.drawString(end, left + plotW - g.getFontMetrics().stringWidth(end), top + plotH + 16);
        g.dispose();
    }

    private void drawRaw(Graphics2D g, int i0, int i1, long t0, long t1,
                         int left, int plotW, int mid, int plotH) {
        float[] y = trace.raw;
        double max = 0;
        for (int i = i0; i <= i1; i++) {
            max = Math.max(max, Math.abs(y[i]));
        }
        if (max == 0) {
            max = 1;
        }
        double amp = 0.42 * plotH;
        g.setColor(trace.color);
        g.setStroke(new BasicStroke(1.2f));
        int prevX = -1;
        int prevY = 0;
        int step = Math.max(1, (i1 - i0 + 1) / Math.max(1, plotW * 2));
        for (int i = i0; i <= i1; i += step) {
            int x = xOf(trace.sampleMillis(i), t0, t1, left, plotW);
            int yy = mid - (int) Math.round((y[i] / (2 * max)) * 2 * amp);
            if (prevX >= 0) {
                g.drawLine(prevX, prevY, x, yy);
            }
            prevX = x;
            prevY = yy;
        }
    }

    private void drawOverlay(Graphics2D g, WaveformCanvas.Overlay o, long t0, long t1,
                             int left, int plotW, int mid, int plotH, Color color, float stroke) {
        if (o == null || o.y.length < 2) {
            return;
        }
        int i0 = (int) Math.floor((t0 - o.startMillis) / (o.deltaSec * 1000.0));
        int i1 = (int) Math.ceil((t1 - o.startMillis) / (o.deltaSec * 1000.0));
        i0 = Math.max(0, i0);
        i1 = Math.min(o.y.length - 1, i1);
        if (i1 <= i0) {
            return;
        }
        double max = 0;
        for (int i = i0; i <= i1; i++) {
            max = Math.max(max, Math.abs(o.y[i]));
        }
        if (max == 0) {
            max = 1;
        }
        double amp = 0.42 * plotH;
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke));
        int prevX = -1;
        int prevY = 0;
        int step = Math.max(1, (i1 - i0 + 1) / Math.max(1, plotW * 2));
        for (int i = i0; i <= i1; i += step) {
            int x = xOf(o.sampleMillis(i), t0, t1, left, plotW);
            int yy = mid - (int) Math.round((o.y[i] / (2 * max)) * 2 * amp);
            if (prevX >= 0) {
                g.drawLine(prevX, prevY, x, yy);
            }
            prevX = x;
            prevY = yy;
        }
    }

    private void drawPick(Graphics2D g, Date pick, Color color, long t0, long t1,
                          int left, int plotW, int top, int plotH) {
        if (pick == null) {
            return;
        }
        long tm = pick.getTime();
        if (tm < t0 || tm > t1) {
            return;
        }
        int x = xOf(tm, t0, t1, left, plotW);
        g.setColor(color);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x, top + 1, x, top + plotH - 1);
    }

    private static int xOf(long tm, long t0, long t1, int left, int plotW) {
        return left + (int) Math.round((tm - t0) / (double) Math.max(1L, t1 - t0) * plotW);
    }
}
