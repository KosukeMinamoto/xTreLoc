package com.treloc.xtreloc.app.gui.volume3d;

import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Software renderer of orthogonal slices through a {@link VelocityVolume3D}.
 *
 * <p>Every pixel casts a ray that is intersected with up to three slice planes (longitude, latitude and depth
 * sections); the hits are sampled (trilinear) and composited front to back. No GPU or native 3-D library is
 * needed, and the same ray-casting gives the exact position under the mouse ({@link #probe}) and a depth buffer for
 * occlusion tests of overlays.</p>
 *
 * <p>Scene coordinates are kilometres: x east, y north, z up ({@code z = -depth * verticalExaggeration}), measured
 * from the volume centre with an equirectangular scaling at the centre latitude. The view is an orbit camera around
 * a target point.</p>
 *
 * <p>Fields are plain and mutable; the renderer is not thread-safe against concurrent modification while
 * {@link #render} runs.</p>
 */
public final class Volume3DRenderer {

    public static final double KM_PER_DEG = 111.19;
    private static final double NEAR = 1e-3;

    // ---- model / colouring
    private VelocityVolume3D volume;
    public String channel;
    /** 256-entry 0xRRGGBB colour table (shared with the 2-D map colour bar). */
    public int[] lut = ColorMaps.SEISMIC.lut();
    public double vmin;
    public double vmax;
    /** Visibility per layer id 1..4 (index 0 = layer 1); {@code null} = all visible. */
    public boolean[] layerVisible;
    public int background = 0xFFFFFF;

    // ---- slices
    public boolean showLonSlice = true;
    public boolean showLatSlice = true;
    public boolean showDepthSlice = true;
    public double lonSlice;
    public double latSlice;
    public double depthSlice;
    /** Slice opacity in (0, 1]. */
    public double alpha = 1.0;

    // ---- camera
    /** Heading of the viewing direction in degrees clockwise from north. */
    public double azimuthDeg = -25.0;
    /** Elevation of the camera above the horizon, degrees (-89..89). */
    public double elevationDeg = 28.0;
    /** Distance camera - target (km). */
    public double distance = 1000.0;
    /** Target offset from the volume centre, in screen-space units (km at the target distance). */
    public double panRight;
    public double panUp;
    public double fovDeg = 35.0;
    public double verticalExaggeration = 1.5;

    // ---- derived geometry
    private double lonC;
    private double latC;
    private double kLon;
    private double kLat;

    public Volume3DRenderer() {}

    public VelocityVolume3D volume() {
        return volume;
    }

    /** Sets the model and resets slices, colour range and camera to defaults. */
    public void setVolume(VelocityVolume3D v, String channelName) {
        setVolume(v, channelName, false);
    }

    /**
     * Sets the model. When {@code keepView} is true the camera and slice positions are kept (clamped to the
     * new volume) so Range / Quantity changes do not jump the viewpoint.
     */
    public void setVolume(VelocityVolume3D v, String channelName, boolean keepView) {
        this.volume = v;
        this.channel = channelName;
        updateGeometry();
        if (keepView) {
            lonSlice = clamp(lonSlice, v.minLon(), v.maxLon());
            latSlice = clamp(latSlice, v.minLat(), v.maxLat());
            depthSlice = clamp(depthSlice, v.minDepth(), v.maxDepth());
        } else {
            lonSlice = 0.5 * (v.minLon() + v.maxLon());
            latSlice = 0.5 * (v.minLat() + v.maxLat());
            depthSlice = Math.min(v.maxDepth(), v.minDepth() + 0.15 * (v.maxDepth() - v.minDepth()));
            resetCamera();
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Removes the volume (nothing is drawn until a new one is set). */
    public void clearVolume() {
        this.volume = null;
        this.channel = null;
    }

    /** Recomputes the scene scaling; call after changing the volume. */
    public void updateGeometry() {
        if (volume == null) {
            return;
        }
        lonC = 0.5 * (volume.minLon() + volume.maxLon());
        latC = 0.5 * (volume.minLat() + volume.maxLat());
        kLat = KM_PER_DEG;
        kLon = KM_PER_DEG * Math.cos(Math.toRadians(latC));
    }

    public void resetCamera() {
        azimuthDeg = -25.0;
        elevationDeg = 28.0;
        panRight = 0.0;
        panUp = 0.0;
        double dx = sceneX(volume.maxLon()) - sceneX(volume.minLon());
        double dy = sceneY(volume.maxLat()) - sceneY(volume.minLat());
        double dz = (volume.maxDepth() - volume.minDepth()) * verticalExaggeration;
        distance = 1.7 * Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------ scene coordinates

    public double sceneX(double lon) { return (lon - lonC) * kLon; }
    public double sceneY(double lat) { return (lat - latC) * kLat; }
    public double sceneZ(double depthKm) { return -depthKm * verticalExaggeration; }

    public double lonOf(double x) { return lonC + x / kLon; }
    public double latOf(double y) { return latC + y / kLat; }
    public double depthOf(double z) { return -z / verticalExaggeration; }

    /** Scene-space box {@code {xmin, xmax, ymin, ymax, zmin, zmax}}. */
    public double[] box() {
        return new double[] {
            sceneX(volume.minLon()), sceneX(volume.maxLon()),
            sceneY(volume.minLat()), sceneY(volume.maxLat()),
            sceneZ(volume.maxDepth()), sceneZ(volume.minDepth())};
    }

    /** Scene coordinates of the volume centre. */
    public double[] center() {
        double[] b = box();
        return new double[] {0.5 * (b[0] + b[1]), 0.5 * (b[2] + b[3]), 0.5 * (b[4] + b[5])};
    }

    // ------------------------------------------------------------------ camera

    /** Camera basis for a viewport of {@code w x h} pixels. */
    public static final class Camera {
        public final double[] pos = new double[3];
        public final double[] fwd = new double[3];
        public final double[] right = new double[3];
        public final double[] up = new double[3];
        public double focal;
        public int w;
        public int h;
    }

    public Camera camera(int w, int h) {
        Camera c = new Camera();
        c.w = w;
        c.h = h;
        double az = Math.toRadians(azimuthDeg);
        double el = Math.toRadians(Math.max(-89.0, Math.min(89.0, elevationDeg)));
        double[] t = center().clone();
        // f: from camera toward target
        double fx = Math.sin(az) * Math.cos(el);
        double fy = Math.cos(az) * Math.cos(el);
        double fz = -Math.sin(el);
        c.fwd[0] = fx; c.fwd[1] = fy; c.fwd[2] = fz;
        // right = f x zUp (normalised)
        double rx = fy;
        double ry = -fx;
        double rn = Math.hypot(rx, ry);
        rx /= rn;
        ry /= rn;
        c.right[0] = rx; c.right[1] = ry; c.right[2] = 0.0;
        // up = right x f
        c.up[0] = ry * fz - 0.0 * fy;
        c.up[1] = 0.0 * fx - rx * fz;
        c.up[2] = rx * fy - ry * fx;
        // pan the target in screen space
        for (int a = 0; a < 3; a++) {
            t[a] += c.right[a] * panRight + c.up[a] * panUp;
            c.pos[a] = t[a] - c.fwd[a] * distance;
        }
        c.focal = 0.5 * h / Math.tan(Math.toRadians(fovDeg) * 0.5);
        return c;
    }

    /**
     * Projects a geographic point; returns {@code {sx, sy, forwardDepth}} in viewport pixels, or {@code null}
     * if the point is behind the camera.
     */
    public double[] project(Camera c, double lon, double lat, double depthKm) {
        return projectScene(c, sceneX(lon), sceneY(lat), sceneZ(depthKm));
    }

    public double[] projectScene(Camera c, double x, double y, double z) {
        double px = x - c.pos[0];
        double py = y - c.pos[1];
        double pz = z - c.pos[2];
        double zc = px * c.fwd[0] + py * c.fwd[1] + pz * c.fwd[2];
        if (zc <= NEAR) {
            return null;
        }
        double xc = px * c.right[0] + py * c.right[1] + pz * c.right[2];
        double yc = px * c.up[0] + py * c.up[1] + pz * c.up[2];
        return new double[] {0.5 * c.w + c.focal * xc / zc, 0.5 * c.h - c.focal * yc / zc, zc};
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Renders into {@code argb} (length {@code w*h}); {@code zbuf} (same length, optional) receives the forward
     * distance of the nearest slice hit or {@code +Infinity}.
     */
    public void render(int w, int h, int[] argb, float[] zbuf) {
        final Camera cam = camera(w, h);
        final Setup s = new Setup();
        IntStream.range(0, h).parallel().forEach(py -> {
            double[] ts = new double[3];
            int[] ax = new int[3];
            double[] dir = new double[3];
            for (int px = 0; px < w; px++) {
                rayDir(cam, px + 0.5, py + 0.5, dir);
                double r = 0.0;
                double g = 0.0;
                double b = 0.0;
                double trans = 1.0;
                double zFirst = Double.POSITIVE_INFINITY;
                int n = hits(cam, dir, s, ts, ax);
                for (int q = 0; q < n && trans > 0.002; q++) {
                    double t = ts[q];
                    double x = cam.pos[0] + t * dir[0];
                    double y = cam.pos[1] + t * dir[1];
                    double z = cam.pos[2] + t * dir[2];
                    int rgb = colorAt(x, y, z);
                    if (rgb < 0) {
                        continue; // masked out
                    }
                    if (zFirst == Double.POSITIVE_INFINITY) {
                        zFirst = t;
                    }
                    r += trans * alpha * ((rgb >> 16) & 255);
                    g += trans * alpha * ((rgb >> 8) & 255);
                    b += trans * alpha * (rgb & 255);
                    trans *= 1.0 - alpha;
                }
                r += trans * ((background >> 16) & 255);
                g += trans * ((background >> 8) & 255);
                b += trans * (background & 255);
                int i = py * w + px;
                argb[i] = 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
                if (zbuf != null) {
                    zbuf[i] = (float) zFirst;
                }
            }
        });
    }

    private static int clamp255(double v) {
        return v <= 0 ? 0 : (v >= 255 ? 255 : (int) (v + 0.5));
    }

    /** Cached per-frame slice positions in scene coordinates. */
    private final class Setup {
        final double[] box = box();
        final boolean[] on = {showLonSlice, showLatSlice, showDepthSlice};
        final double[] pos = {sceneX(lonSlice), sceneY(latSlice), sceneZ(depthSlice)};
        final double[] lo = {box[0], box[2], box[4]};
        final double[] hi = {box[1], box[3], box[5]};
    }

    private static void rayDir(Camera c, double px, double py, double[] dir) {
        double a = (px - 0.5 * c.w) / c.focal;
        double b = (py - 0.5 * c.h) / c.focal;
        for (int k = 0; k < 3; k++) {
            dir[k] = c.fwd[k] + a * c.right[k] - b * c.up[k];
        }
    }

    /** Sorted slice hits; returns the count. */
    private int hits(Camera cam, double[] dir, Setup s, double[] ts, int[] ax) {
        int n = 0;
        for (int a = 0; a < 3; a++) {
            if (!s.on[a]) {
                continue;
            }
            double d = dir[a];
            if (Math.abs(d) < 1e-12) {
                continue;
            }
            double t = (s.pos[a] - cam.pos[a]) / d;
            if (t <= NEAR) {
                continue;
            }
            boolean inside = true;
            for (int o = 0; o < 3; o++) {
                if (o == a) {
                    continue;
                }
                double v = cam.pos[o] + t * dir[o];
                if (v < s.lo[o] - 1e-6 || v > s.hi[o] + 1e-6) {
                    inside = false;
                    break;
                }
            }
            if (!inside) {
                continue;
            }
            int p = n++;
            while (p > 0 && ts[p - 1] > t) {
                ts[p] = ts[p - 1];
                ax[p] = ax[p - 1];
                p--;
            }
            ts[p] = t;
            ax[p] = a;
        }
        return n;
    }

    /** 0xRRGGBB colour at a scene point, or {@code -1} if masked out by the layer filter. */
    private int colorAt(double x, double y, double z) {
        double lon = lonOf(x);
        double lat = latOf(y);
        double dep = depthOf(z);
        if (layerVisible != null && volume.hasChannel(VelocityVolume3D.CH_LAYER)) {
            int id = (int) Math.round(volume.sample(VelocityVolume3D.CH_LAYER, lon, lat, dep));
            if (id >= 1 && id <= layerVisible.length && !layerVisible[id - 1]) {
                return -1;
            }
        }
        double v = volume.sample(channel, lon, lat, dep);
        return colorOf(v);
    }

    /** Colour of a channel value under the current channel / range / colour map. */
    public int colorOf(double v) {
        if (VelocityVolume3D.CH_LAYER.equals(channel)) {
            return ColorMaps.layerRgb(v);
        }
        double span = vmax - vmin;
        double t = span > 0 ? (v - vmin) / span : 0.5;
        int[] l = lut;
        if (!(t > 0.0)) {
            return l[0];
        }
        if (t >= 1.0) {
            return l[l.length - 1];
        }
        return l[(int) (t * (l.length - 1) + 0.5)];
    }

    /** Sets the colour table from a palette array (e.g. the colour bar's 256 colours). */
    public void setLut(java.awt.Color[] colors) {
        if (colors == null || colors.length < 2) {
            return;
        }
        int[] l = new int[colors.length];
        for (int i = 0; i < l.length; i++) {
            l[i] = colors[i].getRGB() & 0xFFFFFF;
        }
        this.lut = l;
    }

    // ------------------------------------------------------------------ probing

    /** Result of {@link #probe}. */
    public static final class Probe {
        public final double lon;
        public final double lat;
        public final double depthKm;
        /** Values of every channel at the point, in {@link VelocityVolume3D#channelNames()} order. */
        public final double[] values;

        Probe(double lon, double lat, double depthKm, double[] values) {
            this.lon = lon;
            this.lat = lat;
            this.depthKm = depthKm;
            this.values = values;
        }
    }

    /** Nearest visible slice point under the pixel, or {@code null}. */
    public Probe probe(int w, int h, double px, double py) {
        Camera cam = camera(w, h);
        Setup s = new Setup();
        double[] dir = new double[3];
        double[] ts = new double[3];
        int[] ax = new int[3];
        rayDir(cam, px, py, dir);
        int n = hits(cam, dir, s, ts, ax);
        for (int q = 0; q < n; q++) {
            double t = ts[q];
            double x = cam.pos[0] + t * dir[0];
            double y = cam.pos[1] + t * dir[1];
            double z = cam.pos[2] + t * dir[2];
            if (colorAt(x, y, z) < 0) {
                continue;
            }
            double lon = lonOf(x);
            double lat = latOf(y);
            double dep = depthOf(z);
            java.util.List<String> names = volume.channelNames();
            double[] vals = new double[names.size()];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = volume.sample(names.get(i), lon, lat, dep);
            }
            return new Probe(lon, lat, dep, vals);
        }
        return null;
    }

    @Override
    public String toString() {
        return "Volume3DRenderer[" + (volume == null ? "no volume" : volume.name()) + ", az=" + azimuthDeg + ", el="
            + elevationDeg + ", D=" + Arrays.toString(new double[] {distance}) + "]";
    }
}
