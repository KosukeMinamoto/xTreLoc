package com.treloc.xtreloc.app.gui.map2d;

import com.treloc.xtreloc.app.gui.volume3d.ColorMaps;
import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D.Plane2D;
import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D.Reduce;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Lon/lat (or section) projection and velocity-background raster for the custom 2-D map.
 * Scene: x east, y north (or along-section), pixels with y down. World axes are converted to
 * kilometres so {@link #scaleRatio} stretches the vertical axis relative to an equal-km plot.
 */
public final class Map2DRenderer {

    public VelocityVolume3D volume;
    public String channel;
    public Reduce reduce = Reduce.SLICE;
    /** 0 = lon (lat–depth), 1 = lat (lon–depth), 2 = depth (lon–lat map). */
    public int collapse = 2;
    /** Slice position along the collapsed axis (lon / lat / depth). */
    public double slicePos;
    /** Vertical / horizontal scale (1 = equal kilometres). */
    public double scaleRatio = 1.0;
    public boolean showVelocity = false;
    /** How the velocity / travel-time field is painted. */
    public FillStyle fillStyle = FillStyle.GRADUAL;
    public int[] lut = ColorMaps.SEISMIC.lut();
    public double vmin;
    public double vmax;
    public double alpha = 0.85;

    public double lonMin = 138;
    public double lonMax = 145;
    public double latMin = 34;
    public double latMax = 42;

    public double zoom = 1.0;
    public double panX;
    public double panY;

    private Plane2D plane;

    public void setVolume(VelocityVolume3D v, String ch) {
        setVolume(v, ch, false);
    }

    public void setVolume(VelocityVolume3D v, String ch, boolean keepView) {
        this.volume = v;
        this.channel = ch != null ? ch : (v != null && !v.channelNames().isEmpty() ? v.channelNames().get(0) : null);
        if (v != null) {
            lonMin = v.minLon();
            lonMax = v.maxLon();
            latMin = v.minLat();
            latMax = v.maxLat();
            slicePos = keepView ? clampSlice(slicePos) : defaultSlice();
        }
        rebuildPlane();
        if (!keepView) {
            resetView();
        }
    }

    public void rebuildPlane() {
        if (volume == null || channel == null || !volume.hasChannel(channel)) {
            plane = null;
            return;
        }
        if (reduce == Reduce.SLICE) {
            plane = volume.slice(channel, collapse, clampSlice(slicePos));
        } else {
            plane = volume.reduce(channel, reduce, collapse);
        }
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (float z : plane.z) {
            if (Float.isFinite(z)) {
                lo = Math.min(lo, z);
                hi = Math.max(hi, z);
            }
        }
        if (lo <= hi && Double.isFinite(lo) && Double.isFinite(hi)) {
            vmin = lo;
            vmax = hi > lo ? hi : lo + Math.max(0.05, Math.abs(lo) * 0.02);
        }
    }

    public Plane2D plane() {
        return plane;
    }

    public void resetView() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
    }

    public double sliceMin() {
        if (volume == null) {
            return collapse == 2 ? 0 : (collapse == 0 ? lonMin : latMin);
        }
        return collapse == 2 ? volume.minDepth() : (collapse == 0 ? volume.minLon() : volume.minLat());
    }

    public double sliceMax() {
        if (volume == null) {
            return collapse == 2 ? 100 : (collapse == 0 ? lonMax : latMax);
        }
        return collapse == 2 ? volume.maxDepth() : (collapse == 0 ? volume.maxLon() : volume.maxLat());
    }

    public double defaultSlice() {
        return 0.5 * (sliceMin() + sliceMax());
    }

    public double clampSlice(double pos) {
        double lo = sliceMin();
        double hi = sliceMax();
        if (!(hi > lo)) {
            return lo;
        }
        return Math.max(lo, Math.min(hi, pos));
    }

    public double[] worldBounds() {
        if (collapse == 2) {
            return new double[] {lonMin, lonMax, latMin, latMax};
        }
        if (plane != null) {
            return new double[] {plane.u[0], plane.u[plane.u.length - 1], plane.v[0], plane.v[plane.v.length - 1]};
        }
        if (volume != null && collapse == 0) {
            return new double[] {volume.minLat(), volume.maxLat(), volume.minDepth(), volume.maxDepth()};
        }
        if (volume != null) {
            return new double[] {volume.minLon(), volume.maxLon(), volume.minDepth(), volume.maxDepth()};
        }
        return new double[] {lonMin, lonMax, latMin, latMax};
    }

    /** Kilometres per world-u unit (degree or km). */
    public double kmPerU() {
        if (collapse == 2 || collapse == 1) {
            return 111.19 * Math.cos(Math.toRadians(0.5 * (latMin + latMax)));
        }
        return 111.19;
    }

    /** Kilometres per world-v unit, including {@link #scaleRatio}. */
    public double kmPerV() {
        double base = collapse == 2 ? 111.19 : 1.0;
        return base * (collapse == 2 ? 1.0 : Math.max(0.05, scaleRatio));
    }

    /** True on sections: depth increases downward. Plan view keeps north up. */
    public boolean depthDown() {
        return collapse != 2;
    }

    /** Maps world (u, v) to pixel. Plan: v (lat) up. Section: v (depth) down. */
    public double[] toPixel(int w, int h, double u, double vv) {
        double[] s = scales(w, h);
        double x = w * 0.5 + (u - s[2]) * s[0] + panX;
        double y = depthDown()
            ? h * 0.5 + (vv - s[3]) * s[1] + panY
            : h * 0.5 - (vv - s[3]) * s[1] + panY;
        return new double[] {x, y};
    }

    public double[] toWorld(int w, int h, double px, double py) {
        double[] s = scales(w, h);
        double u = s[2] + (px - w * 0.5 - panX) / s[0];
        double v = depthDown()
            ? s[3] + (py - h * 0.5 - panY) / s[1]
            : s[3] - (py - h * 0.5 - panY) / s[1];
        return new double[] {u, v};
    }

    /** {sx, sy, cx, cy} — pixels per world unit and world centre. */
    private double[] scales(int w, int h) {
        double[] b = worldBounds();
        double du = Math.max(1e-9, b[1] - b[0]);
        double dv = Math.max(1e-9, b[3] - b[2]);
        double pad = 0.06;
        double sx = w * (1 - 2 * pad) / (du * kmPerU());
        double sy = h * (1 - 2 * pad) / (dv * kmPerV());
        double s = Math.min(sx, sy) * zoom;
        return new double[] {s * kmPerU(), s * kmPerV(), 0.5 * (b[0] + b[1]), 0.5 * (b[2] + b[3])};
    }

    public double kmPerPixel(int w, int h) {
        double[] b = worldBounds();
        double[] p0 = toPixel(w, h, b[0], 0.5 * (b[2] + b[3]));
        double[] p1 = toPixel(w, h, b[0] + 1.0, 0.5 * (b[2] + b[3]));
        double px = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
        return px < 1e-6 ? 1.0 : kmPerU() / px;
    }

    public enum FillStyle {
        GRID("Grid cells"),
        GRADUAL("Gradual"),
        CONTOUR("Contour");

        private final String label;

        FillStyle(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public void renderBackground(int w, int h, int[] pixels) {
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        if (!showVelocity || plane == null || fillStyle == FillStyle.CONTOUR) {
            return;
        }
        double span = vmax - vmin;
        if (!(span > 0)) {
            span = 1.0;
        }
        boolean nearest = fillStyle == FillStyle.GRID;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] uv = toWorld(w, h, x + 0.5, y + 0.5);
                if (uv[0] < plane.u[0] || uv[0] > plane.u[plane.u.length - 1]
                    || uv[1] < plane.v[0] || uv[1] > plane.v[plane.v.length - 1]) {
                    continue;
                }
                double val = nearest ? plane.sampleNearest(uv[0], uv[1]) : plane.sample(uv[0], uv[1]);
                if (!Double.isFinite(val)) {
                    continue;
                }
                double t = (val - vmin) / span;
                int idx = (int) Math.round(Math.max(0, Math.min(1, t)) * (lut.length - 1));
                int rgb = lut[idx];
                int a = (int) Math.round(alpha * 255);
                pixels[y * w + x] = (a << 24) | rgb;
            }
        }
    }

    /** Isolines of {@link #plane} when {@link #fillStyle} is {@link FillStyle#CONTOUR}. */
    public void drawContours(Graphics2D g, int w, int h) {
        if (!showVelocity || fillStyle != FillStyle.CONTOUR || plane == null) {
            return;
        }
        int nu = plane.nU();
        int nv = plane.nV();
        double span = vmax - vmin;
        if (!(span > 0) || nu < 2 || nv < 2) {
            return;
        }
        int nLevels = 8;
        g.setStroke(new BasicStroke(1.1f));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double[] pts = new double[8];
        for (int k = 1; k < nLevels; k++) {
            double level = vmin + span * k / (double) nLevels;
            g.setColor(new Color(0xFF000000 | colorOf(level), true));
            for (int j = 0; j < nv - 1; j++) {
                for (int i = 0; i < nu - 1; i++) {
                    float z00 = plane.z[j * nu + i];
                    float z10 = plane.z[j * nu + i + 1];
                    float z01 = plane.z[(j + 1) * nu + i];
                    float z11 = plane.z[(j + 1) * nu + i + 1];
                    if (!Float.isFinite(z00) || !Float.isFinite(z10)
                        || !Float.isFinite(z01) || !Float.isFinite(z11)) {
                        continue;
                    }
                    int n = 0;
                    n = addCross(pts, n, plane.u[i], plane.v[j], plane.u[i + 1], plane.v[j], z00, z10, level);
                    n = addCross(pts, n, plane.u[i + 1], plane.v[j], plane.u[i + 1], plane.v[j + 1], z10, z11, level);
                    n = addCross(pts, n, plane.u[i], plane.v[j + 1], plane.u[i + 1], plane.v[j + 1], z01, z11, level);
                    n = addCross(pts, n, plane.u[i], plane.v[j], plane.u[i], plane.v[j + 1], z00, z01, level);
                    if (n >= 4) {
                        drawWorldLine(g, w, h, pts[0], pts[1], pts[2], pts[3]);
                    }
                    if (n >= 8) {
                        drawWorldLine(g, w, h, pts[4], pts[5], pts[6], pts[7]);
                    }
                }
            }
        }
    }

    private void drawWorldLine(Graphics2D g, int w, int h, double u0, double v0, double u1, double v1) {
        double[] a = toPixel(w, h, u0, v0);
        double[] b = toPixel(w, h, u1, v1);
        g.drawLine((int) Math.round(a[0]), (int) Math.round(a[1]),
            (int) Math.round(b[0]), (int) Math.round(b[1]));
    }

    private static int addCross(double[] pts, int n, double xa, double ya, double xb, double yb,
                                float za, float zb, double level) {
        if (n > 6) {
            return n;
        }
        if ((za < level && zb >= level) || (za >= level && zb < level)) {
            double t = (zb - za) == 0 ? 0.5 : (level - za) / (zb - za);
            t = t < 0 ? 0 : (t > 1 ? 1 : t);
            pts[n] = xa + t * (xb - xa);
            pts[n + 1] = ya + t * (yb - ya);
            return n + 2;
        }
        return n;
    }

    public int colorOf(double val) {
        if (!Double.isFinite(val) || vmax <= vmin) {
            return 0x808080;
        }
        double t = (val - vmin) / (vmax - vmin);
        int idx = (int) Math.round(Math.max(0, Math.min(1, t)) * (lut.length - 1));
        return lut[idx];
    }
}
