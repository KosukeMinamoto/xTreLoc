package com.treloc.xtreloc.app.gui.volume3d;

import com.treloc.xtreloc.app.gui.util.OverlayHandle;
import com.treloc.xtreloc.app.gui.view.MapView;
import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import com.treloc.xtreloc.util.TimeFormatConverter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Interactive 3-D view of a {@link VelocityVolume3D}: orthogonal slices rendered by {@link Volume3DRenderer} plus a
 * vector overlay (bounding box, slice outlines, axes, colour bar, hypocentres).
 *
 * <p>Left drag rotates, right drag (or Shift + left drag) pans, the wheel zooms and a double click resets the view.
 * While the mouse is busy the image is rendered at reduced resolution and refined shortly after.</p>
 */
public final class Volume3DCanvas extends JComponent {

    private static final Color BOX_COLOR = new Color(70, 75, 90);
    private static final Color TEXT_COLOR = new Color(35, 40, 52);
    private static final int REFINE_DELAY_MS = 160;

    private final Volume3DRenderer renderer = new Volume3DRenderer();

    private BufferedImage image;
    private int[] pixels;
    private float[] zbuf;
    private int imageScale = 1;
    private boolean dirty = true;
    private boolean interactive;
    private final Timer refineTimer;

    /** Hypocentres of one catalog in the catalog colour (and optional per-event colours). */
    public static final class PointGroup {
        public final Color color;
        /** {@code {lon, lat, depthKm}} */
        public final List<double[]> points;
        /** Same length as {@code points} when a catalog column colormap is active; otherwise {@code null}. */
        public final List<Color> pointColors;
        /** Catalog / Style symbol size in pixels ({@code 0} = canvas default). */
        public final int size;

        public PointGroup(Color color, List<double[]> points) {
            this(color, points, null, 0);
        }

        public PointGroup(Color color, List<double[]> points, List<Color> pointColors) {
            this(color, points, pointColors, 0);
        }

        public PointGroup(Color color, List<double[]> points, List<Color> pointColors, int size) {
            this.color = color;
            this.points = points;
            this.pointColors = pointColors;
            this.size = size;
        }
    }

    public static final class LegendItem {
        public final String name;
        public final Color color;

        public LegendItem(String name, Color color) {
            this.name = name;
            this.color = color;
        }
    }

    /** Station as drawn on the map: code and position. */
    public static final class StationPoint {
        public final String code;
        public final double lon;
        public final double lat;
        public final double depthKm;
        public final Color color;
        public final boolean filled;

        public StationPoint(String code, double lon, double lat, double depthKm) {
            this(code, lon, lat, depthKm, Color.BLACK, true);
        }

        public StationPoint(String code, double lon, double lat, double depthKm, Color color, boolean filled) {
            this.code = code;
            this.lon = lon;
            this.lat = lat;
            this.depthKm = depthKm;
            this.color = color != null ? color : Color.BLACK;
            this.filled = filled;
        }
    }

    private List<PointGroup> hypocenterGroups = new ArrayList<>();
    private List<StationPoint> stations = new ArrayList<>();
    /** Shapefile polylines as flat {@code {lon0, lat0, lon1, lat1, ...}} arrays, drawn on the surface. */
    private List<double[]> shapeLines = new ArrayList<>();
    private boolean showHypocenters = true;
    private boolean showStations = true;
    private boolean showStationLabels = true;
    private boolean showShapefiles = true;
    private int hypocenterSize = 5;
    private boolean showBox = true;
    private boolean showAxes = true;
    private boolean showCompass = true;
    private boolean showScale = true;
    private boolean showGrid = true;
    private boolean showColorBar = true;
    private boolean showHypocenterColorBar = true;
    private boolean showLegend = true;
    private List<LegendItem> legendItems = new ArrayList<>();

    private Consumer<Volume3DRenderer.Probe> probeListener;
    private Runnable colorBarClickListener;
    private Runnable catalogColorBarClickListener;
    private MapView.ColorBarPanel catalogColorBar;
    /** Screen rectangle of the colour bar (x, y, w, h) from the last paint; {@code null} if not drawn. */
    private int[] colorBarRect;
    private int[] catalogColorBarRect;
    private boolean pressedOnColorBar;
    private final OverlayHandle legendHandle = new OverlayHandle(OverlayHandle.Corner.NW, 8);
    private final OverlayHandle colorBarHandle = new OverlayHandle(OverlayHandle.Corner.NE, 8);
    private final OverlayHandle catalogColorBarHandle = new OverlayHandle(OverlayHandle.Corner.NE, 8);
    private final OverlayHandle scaleHandle = new OverlayHandle(OverlayHandle.Corner.SW, 12);
    private final OverlayHandle compassHandle = new OverlayHandle(OverlayHandle.Corner.SE, 12);
    private OverlayHandle draggingOverlay;
    private int dragOffX;
    private int dragOffY;
    private boolean overlayMoved;

    private int lastX;
    private int lastY;
    private boolean panning;

    public Volume3DCanvas() {
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(640, 480));
        setFocusable(true);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        refineTimer = new Timer(REFINE_DELAY_MS, e -> {
            interactive = false;
            dirty = true;
            repaint();
        });
        refineTimer.setRepeats(false);

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                lastX = e.getX();
                lastY = e.getY();
                draggingOverlay = pickOverlay(e.getX(), e.getY());
                overlayMoved = false;
                if (draggingOverlay != null) {
                    dragOffX = e.getX() - draggingOverlay.x();
                    dragOffY = e.getY() - draggingOverlay.y();
                    pressedOnColorBar = draggingOverlay == colorBarHandle
                        || draggingOverlay == catalogColorBarHandle;
                    return;
                }
                pressedOnColorBar = false;
                panning = SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)
                    || e.isShiftDown();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingOverlay != null) {
                    int nx = e.getX() - dragOffX;
                    int ny = e.getY() - dragOffY;
                    if (Math.abs(nx - draggingOverlay.x()) > 1 || Math.abs(ny - draggingOverlay.y()) > 1) {
                        overlayMoved = true;
                    }
                    draggingOverlay.moveTo(nx, ny, getWidth(), getHeight());
                    setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                    lastX = e.getX();
                    lastY = e.getY();
                    repaint();
                    return;
                }
                if (renderer.volume() == null || pressedOnColorBar) {
                    return;
                }
                int dx = e.getX() - lastX;
                int dy = e.getY() - lastY;
                lastX = e.getX();
                lastY = e.getY();
                if (panning) {
                    double unit = renderer.distance / renderer.camera(getWidth(), getHeight()).focal;
                    renderer.panRight -= dx * unit;
                    renderer.panUp += dy * unit;
                } else {
                    renderer.azimuthDeg += dx * 0.4;
                    renderer.elevationDeg = Math.max(-85.0, Math.min(85.0, renderer.elevationDeg + dy * 0.4));
                }
                invalidateScene(true);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggingOverlay = null;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                OverlayHandle hit = pickOverlay(e.getX(), e.getY());
                boolean onBar = hit == colorBarHandle || hit == catalogColorBarHandle;
                if (hit != null) {
                    setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                } else {
                    setCursor(java.awt.Cursor.getDefaultCursor());
                }
                setToolTipText(onBar ? "Drag to move, click to change the colour palette and range" : null);
                if (onBar) {
                    if (probeListener != null) {
                        probeListener.accept(null);
                    }
                    return;
                }
                fireProbe(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (probeListener != null) {
                    probeListener.accept(null);
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (renderer.volume() == null) {
                    return;
                }
                renderer.distance = Math.max(20.0, Math.min(20000.0,
                    renderer.distance * Math.pow(1.1, e.getPreciseWheelRotation())));
                invalidateScene(true);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (overlayMoved) {
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e) && inCatalogColorBar(e.getX(), e.getY())) {
                    if (e.getClickCount() == 1 && catalogColorBarClickListener != null) {
                        catalogColorBarClickListener.run();
                    }
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e) && inColorBar(e.getX(), e.getY())) {
                    if (e.getClickCount() == 1 && colorBarClickListener != null) {
                        colorBarClickListener.run();
                    }
                    return;
                }
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && renderer.volume() != null) {
                    resetView();
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    // ------------------------------------------------------------------ public API

    public Volume3DRenderer renderer() {
        return renderer;
    }

    /** Marks the scene as changed and repaints; {@code fast} renders at reduced resolution first. */
    public void invalidateScene(boolean fast) {
        dirty = true;
        if (fast) {
            interactive = true;
            refineTimer.restart();
        }
        repaint();
    }

    public void resetView() {
        if (renderer.volume() == null) {
            return;
        }
        renderer.resetCamera();
        invalidateScene(false);
    }

    /** Sets the camera to a preset: "top", "south" (looking north), "west" (looking east), "oblique". */
    public void setViewPreset(String preset) {
        if (renderer.volume() == null) {
            return;
        }
        renderer.panRight = 0;
        renderer.panUp = 0;
        switch (preset) {
            case "top":
                renderer.azimuthDeg = 0;
                renderer.elevationDeg = 85;
                break;
            case "south":
                renderer.azimuthDeg = 0;
                renderer.elevationDeg = 0;
                break;
            case "west":
                renderer.azimuthDeg = 90;
                renderer.elevationDeg = 0;
                break;
            default:
                renderer.azimuthDeg = -25;
                renderer.elevationDeg = 28;
                break;
        }
        invalidateScene(false);
    }

    /** Hypocentres per catalog (catalog colour). */
    public void setHypocenterGroups(List<PointGroup> groups) {
        this.hypocenterGroups = groups == null ? new ArrayList<>() : new ArrayList<>(groups);
        repaint();
    }

    public int hypocenterCount() {
        int n = 0;
        for (PointGroup g : hypocenterGroups) {
            n += g.points.size();
        }
        return n;
    }

    public void setStations(List<StationPoint> list) {
        this.stations = list == null ? new ArrayList<>() : new ArrayList<>(list);
        repaint();
    }

    public int stationCount() {
        return stations.size();
    }

    /** Shapefile polylines (flat lon/lat arrays) drawn on the surface plane. */
    public void setShapeLines(List<double[]> lines) {
        this.shapeLines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
        repaint();
    }

    public int shapeLineCount() {
        return shapeLines.size();
    }

    public void setShowHypocenters(boolean on) {
        showHypocenters = on;
        repaint();
    }

    public void setShowStations(boolean on) {
        showStations = on;
        repaint();
    }

    public void setShowStationLabels(boolean on) {
        showStationLabels = on;
        repaint();
    }

    /** Runs when the colour bar is clicked (opens the colour map settings). */
    public void setColorBarClickListener(Runnable r) {
        this.colorBarClickListener = r;
    }

    public void setCatalogColorBarClickListener(Runnable r) {
        this.catalogColorBarClickListener = r;
    }

    public void setCatalogColorBar(MapView.ColorBarPanel bar) {
        this.catalogColorBar = bar;
        repaint();
    }

    private boolean showCatalogColorBar() {
        return showHypocenterColorBar && catalogColorBar != null;
    }

    public void setShowCatalogColorBar(boolean on) {
        showHypocenterColorBar = on;
        repaint();
    }

    private boolean inColorBar(int x, int y) {
        return showColorBar && colorBarHandle.contains(x, y);
    }

    private boolean inCatalogColorBar(int x, int y) {
        return showCatalogColorBar() && catalogColorBarHandle.contains(x, y);
    }

    private OverlayHandle pickOverlay(int px, int py) {
        if (showLegend && legendHandle.contains(px, py)) {
            return legendHandle;
        }
        if (showCatalogColorBar() && catalogColorBarHandle.contains(px, py)) {
            return catalogColorBarHandle;
        }
        if (showColorBar && colorBarHandle.contains(px, py)) {
            return colorBarHandle;
        }
        if (showScale && scaleHandle.contains(px, py)) {
            return scaleHandle;
        }
        if (showCompass && compassHandle.contains(px, py)) {
            return compassHandle;
        }
        return null;
    }

    public void setHypocenterSize(int px) {
        hypocenterSize = Math.max(1, px);
        repaint();
    }

    public void setShowBox(boolean on) {
        showBox = on;
        repaint();
    }

    public void setShowAxes(boolean on) {
        showAxes = on;
        repaint();
    }

    public void setShowCompass(boolean on) {
        showCompass = on;
        repaint();
    }

    public void setShowScale(boolean on) {
        showScale = on;
        repaint();
    }

    public void setShowGrid(boolean on) {
        showGrid = on;
        repaint();
    }

    public void setShowColorBar(boolean on) {
        showColorBar = on;
        repaint();
    }

    public void setShowLegend(boolean on) {
        showLegend = on;
        repaint();
    }

    public void setLegendItems(List<LegendItem> items) {
        this.legendItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
        repaint();
    }

    public void setProbeListener(Consumer<Volume3DRenderer.Probe> l) {
        this.probeListener = l;
    }

    /** Current view as an image (full resolution slices + overlay). */
    public BufferedImage snapshot() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        boolean wasInteractive = interactive;
        interactive = false;
        dirty = true;
        paintScene(g, w, h);
        interactive = wasInteractive;
        dirty = true;
        g.dispose();
        return out;
    }

    private void fireProbe(int x, int y) {
        if (probeListener == null || renderer.volume() == null) {
            return;
        }
        probeListener.accept(renderer.probe(getWidth(), getHeight(), x + 0.5, y + 0.5));
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            paintScene(g, getWidth(), getHeight());
        } finally {
            g.dispose();
        }
    }

    private void paintScene(Graphics2D g, int w, int h) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (renderer.volume() == null || w < 4 || h < 4) {
            g.setColor(TEXT_COLOR);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 14f));
            if (renderer.volume() == null) {
                g.drawString("No velocity model.", 20, 30);
                g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
                g.drawString("Check a model in the Model tab (Velocity / Visualize).", 20, 52);
            }
            return;
        }
        int scale = interactive ? (w * h > 1_500_000 ? 3 : 2) : 1;
        int iw = Math.max(1, w / scale);
        int ih = Math.max(1, h / scale);
        if (image == null || image.getWidth() != iw || image.getHeight() != ih || scale != imageScale) {
            image = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            zbuf = new float[iw * ih];
            imageScale = scale;
            dirty = true;
        }
        if (dirty) {
            renderer.render(iw, ih, pixels, zbuf);
            dirty = false;
        }
        g.drawImage(image, 0, 0, w, h, null);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Volume3DRenderer.Camera cam = renderer.camera(w, h);
        if (showBox) {
            drawBox(g, cam);
            drawSliceOutlines(g, cam);
        }
        if (showGrid) {
            drawGrid(g, cam);
        }
        if (showAxes) {
            drawAxes(g, cam);
        }
        if (showCompass) {
            drawCompass(g, cam, w, h);
        }
        if (showScale) {
            drawScale(g, cam, w, h);
        }
        if (showShapefiles && !shapeLines.isEmpty()) {
            drawShapeLines(g, cam);
        }
        if (showStations && !stations.isEmpty()) {
            drawStations(g, cam, iw, ih);
        }
        if (showHypocenters && hypocenterCount() > 0) {
            drawHypocenters(g, cam, iw, ih);
        }
        if (showLegend) {
            drawLegend(g, w, h);
        }
        if (showColorBar) {
            drawColorBar(g, w, h);
        } else {
            colorBarRect = null;
        }
        if (showCatalogColorBar()) {
            drawCatalogColorBar(g, w, h);
        } else {
            catalogColorBarRect = null;
        }
    }

    private void drawBox(Graphics2D g, Volume3DRenderer.Camera cam) {
        double[] b = renderer.box();
        g.setColor(BOX_COLOR);
        g.setStroke(new BasicStroke(1.0f));
        for (int a = 0; a < 3; a++) {
            for (int m = 0; m < 4; m++) {
                double[] p = new double[3];
                // two free coordinates enumerate the 4 edges parallel to axis a
                int o1 = (a + 1) % 3;
                int o2 = (a + 2) % 3;
                p[o1] = ((m & 1) == 0) ? b[2 * o1] : b[2 * o1 + 1];
                p[o2] = ((m & 2) == 0) ? b[2 * o2] : b[2 * o2 + 1];
                double[] p0 = p.clone();
                double[] p1 = p.clone();
                p0[a] = b[2 * a];
                p1[a] = b[2 * a + 1];
                line(g, cam, p0, p1);
            }
        }
    }

    private void drawSliceOutlines(Graphics2D g, Volume3DRenderer.Camera cam) {
        double[] b = renderer.box();
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(new Color(20, 20, 20, 170));
        if (renderer.showLonSlice) {
            double x = renderer.sceneX(renderer.lonSlice);
            rect(g, cam, new double[] {x, b[2], b[4]}, new double[] {x, b[3], b[4]},
                new double[] {x, b[3], b[5]}, new double[] {x, b[2], b[5]});
        }
        if (renderer.showLatSlice) {
            double y = renderer.sceneY(renderer.latSlice);
            rect(g, cam, new double[] {b[0], y, b[4]}, new double[] {b[1], y, b[4]},
                new double[] {b[1], y, b[5]}, new double[] {b[0], y, b[5]});
        }
        if (renderer.showDepthSlice) {
            double z = renderer.sceneZ(renderer.depthSlice);
            rect(g, cam, new double[] {b[0], b[2], z}, new double[] {b[1], b[2], z},
                new double[] {b[1], b[3], z}, new double[] {b[0], b[3], z});
        }
    }

    private void rect(Graphics2D g, Volume3DRenderer.Camera cam, double[] a, double[] b, double[] c, double[] d) {
        line(g, cam, a, b);
        line(g, cam, b, c);
        line(g, cam, c, d);
        line(g, cam, d, a);
    }

    private void line(Graphics2D g, Volume3DRenderer.Camera cam, double[] p0, double[] p1) {
        double[] s0 = renderer.projectScene(cam, p0[0], p0[1], p0[2]);
        double[] s1 = renderer.projectScene(cam, p1[0], p1[1], p1[2]);
        if (s0 != null && s1 != null) {
            g.drawLine((int) Math.round(s0[0]), (int) Math.round(s0[1]),
                (int) Math.round(s1[0]), (int) Math.round(s1[1]));
        }
    }

    // ---- axes

    private void drawAxes(Graphics2D g, Volume3DRenderer.Camera cam) {
        VelocityVolume3D v = renderer.volume();
        double[] b = renderer.box();
        double[] ctr = renderer.center();
        double[] cs = renderer.projectScene(cam, ctr[0], ctr[1], ctr[2]);
        if (cs == null) {
            return;
        }
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        g.setColor(TEXT_COLOR);
        g.setStroke(new BasicStroke(1.0f));

        // the box side facing the camera
        double yFront = cam.pos[1] < ctr[1] ? b[2] : b[3];
        double xFront = cam.pos[0] < ctr[0] ? b[0] : b[1];
        double zBottom = b[4];

        // Axes seen (almost) edge-on get no labels: they would only overlap.
        final double minEdgePx = 70.0;
        // longitude along the front-bottom edge
        if (screenLength(cam, new double[] {b[0], yFront, zBottom}, new double[] {b[1], yFront, zBottom}) > minEdgePx) {
            for (double lon : niceTicks(v.minLon(), v.maxLon(), 5)) {
                tick(g, cam, cs, renderer.sceneX(lon), yFront, zBottom,
                    String.format(Locale.ROOT, "%.1f\u00B0E", lon));
            }
        }
        // latitude along the front side edge
        if (screenLength(cam, new double[] {xFront, b[2], zBottom}, new double[] {xFront, b[3], zBottom}) > minEdgePx) {
            for (double lat : niceTicks(v.minLat(), v.maxLat(), 6)) {
                tick(g, cam, cs, xFront, renderer.sceneY(lat), zBottom,
                    String.format(Locale.ROOT, "%.1f\u00B0N", lat));
            }
        }
        // depth along the vertical edge whose projection is leftmost
        double[][] corners = {{b[0], b[2]}, {b[1], b[2]}, {b[0], b[3]}, {b[1], b[3]}};
        double best = Double.POSITIVE_INFINITY;
        double[] bc = corners[0];
        for (double[] c : corners) {
            double[] s = renderer.projectScene(cam, c[0], c[1], b[5]);
            if (s != null && s[0] < best) {
                best = s[0];
                bc = c;
            }
        }
        for (double d : niceTicks(v.minDepth(), v.maxDepth(), 5)) {
            tick(g, cam, cs, bc[0], bc[1], renderer.sceneZ(d), String.format(Locale.ROOT, "%.0f km", d));
        }
    }

    private double screenLength(Volume3DRenderer.Camera cam, double[] p0, double[] p1) {
        double[] s0 = renderer.projectScene(cam, p0[0], p0[1], p0[2]);
        double[] s1 = renderer.projectScene(cam, p1[0], p1[1], p1[2]);
        return (s0 == null || s1 == null) ? 0.0 : Math.hypot(s1[0] - s0[0], s1[1] - s0[1]);
    }

    private void tick(Graphics2D g, Volume3DRenderer.Camera cam, double[] centerScreen,
                      double x, double y, double z, String label) {
        double[] s = renderer.projectScene(cam, x, y, z);
        if (s == null) {
            return;
        }
        double dx = s[0] - centerScreen[0];
        double dy = s[1] - centerScreen[1];
        double n = Math.hypot(dx, dy);
        if (n < 1e-6) {
            return;
        }
        dx /= n;
        dy /= n;
        int x0 = (int) Math.round(s[0]);
        int y0 = (int) Math.round(s[1]);
        g.drawLine(x0, y0, (int) Math.round(s[0] + dx * 5), (int) Math.round(s[1] + dy * 5));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(label);
        double lx = s[0] + dx * 8;
        double ly = s[1] + dy * 8;
        int px = (int) Math.round(lx - (dx < -0.3 ? tw : (dx > 0.3 ? 0 : tw / 2.0)));
        int py = (int) Math.round(ly + (dy > 0.3 ? fm.getAscent() : (dy < -0.3 ? 0 : fm.getAscent() / 2.0)));
        g.drawString(label, px, py);
    }

    /** Round tick values inside [lo, hi] (about {@code target} of them). */
    static double[] niceTicks(double lo, double hi, int target) {
        double span = hi - lo;
        if (!(span > 0)) {
            return new double[] {lo};
        }
        double raw = span / Math.max(1, target);
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / mag;
        double step = (f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10) * mag;
        double first = Math.ceil(lo / step - 1e-9) * step;
        List<Double> out = new ArrayList<>();
        for (double v = first; v <= hi + 1e-9 * span; v += step) {
            out.add(Math.abs(v) < 1e-12 ? 0.0 : v);
        }
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }
        return a;
    }

    private void drawGrid(Graphics2D g, Volume3DRenderer.Camera cam) {
        VelocityVolume3D v = renderer.volume();
        double[] b = renderer.box();
        g.setColor(new Color(70, 75, 90, 90));
        g.setStroke(new BasicStroke(0.8f));
        double zBottom = b[4];
        for (double lon : niceTicks(v.minLon(), v.maxLon(), 5)) {
            double x = renderer.sceneX(lon);
            line(g, cam, new double[] {x, b[2], zBottom}, new double[] {x, b[3], zBottom});
        }
        for (double lat : niceTicks(v.minLat(), v.maxLat(), 6)) {
            double y = renderer.sceneY(lat);
            line(g, cam, new double[] {b[0], y, zBottom}, new double[] {b[1], y, zBottom});
        }
        double[] ctr = renderer.center();
        double xFront = cam.pos[0] < ctr[0] ? b[0] : b[1];
        double yFront = cam.pos[1] < ctr[1] ? b[2] : b[3];
        for (double d : niceTicks(v.minDepth(), v.maxDepth(), 5)) {
            double z = renderer.sceneZ(d);
            line(g, cam, new double[] {xFront, b[2], z}, new double[] {xFront, b[3], z});
            line(g, cam, new double[] {b[0], yFront, z}, new double[] {b[1], yFront, z});
        }
    }

    private void drawCompass(Graphics2D g, Volume3DRenderer.Camera cam, int w, int h) {
        double[] c = renderer.center();
        double[] o = renderer.projectScene(cam, c[0], c[1], c[2]);
        double[] n = renderer.projectScene(cam, c[0], c[1] + 10, c[2]);
        if (o == null || n == null) {
            return;
        }
        double ndx = n[0] - o[0];
        double ndy = n[1] - o[1];
        double len = Math.hypot(ndx, ndy);
        if (len < 1e-6) {
            return;
        }
        ndx /= len;
        ndy /= len;
        int r = 22;
        int[] p = compassHandle.place(w, h, 2 * r, 2 * r);
        int cx = p[0] + r;
        int cy = p[1] + r;
        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(new Color(70, 75, 90, 180));
        g.setStroke(new BasicStroke(1.0f));
        g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
        String[] lab = {"N", "E", "S", "W"};
        double[][] dir = {{ndx, ndy}, {-ndy, ndx}, {-ndx, -ndy}, {ndy, -ndx}};
        for (int i = 0; i < 4; i++) {
            int lx = (int) Math.round(cx + dir[i][0] * (r - 8));
            int ly = (int) Math.round(cy + dir[i][1] * (r - 8));
            g.setColor(i == 0 ? new Color(180, 40, 40) : TEXT_COLOR);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(lab[i], lx - fm.stringWidth(lab[i]) / 2, ly + fm.getAscent() / 2 - 1);
        }
    }

    private void drawLegend(Graphics2D g, int w, int h) {
        List<LegendItem> items = legendItems;
        if (items == null || items.isEmpty()) {
            return;
        }
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        int rowH = Math.max(14, fm.getHeight());
        int box = 10;
        int pad = 8;
        int maxW = 0;
        for (LegendItem it : items) {
            maxW = Math.max(maxW, fm.stringWidth(it.name));
        }
        int lw = pad * 2 + box + 6 + maxW;
        int lh = pad * 2 + items.size() * rowH;
        int[] p = legendHandle.place(w, h, lw, lh);
        int x = p[0];
        int y = p[1];
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRoundRect(x, y, lw, lh, 6, 6);
        g.setColor(new Color(70, 75, 90, 140));
        g.drawRoundRect(x, y, lw, lh, 6, 6);
        int ty = y + pad + fm.getAscent();
        for (LegendItem it : items) {
            g.setColor(it.color != null ? it.color : Color.GRAY);
            g.fillRect(x + pad, ty - fm.getAscent() + 2, box, box);
            g.setColor(new Color(35, 40, 52));
            g.drawString(it.name, x + pad + box + 6, ty);
            ty += rowH;
        }
    }

    private void drawScale(Graphics2D g, Volume3DRenderer.Camera cam, int w, int h) {
        double[] c = renderer.center();
        double[] b = renderer.box();
        double[] p0 = renderer.projectScene(cam, c[0], c[1], b[4]);
        double[] p1 = renderer.projectScene(cam, c[0] + 10.0, c[1], b[4]);
        if (p0 == null || p1 == null) {
            return;
        }
        double pxPerKm = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) / 10.0;
        if (!(pxPerKm > 1e-3)) {
            return;
        }
        double targetPx = 80.0;
        double rawKm = targetPx / pxPerKm;
        double mag = Math.pow(10, Math.floor(Math.log10(rawKm)));
        double f = rawKm / mag;
        double km = (f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10) * mag;
        int bar = (int) Math.round(km * pxPerKm);
        int boxW = bar + 12;
        int boxH = 22;
        int[] p = scaleHandle.place(w, h, boxW, boxH);
        int x0 = p[0] + 6;
        int y0 = p[1] + 16;
        g.setColor(new Color(255, 255, 255, 180));
        g.fillRect(p[0], p[1], boxW, boxH);
        g.setColor(TEXT_COLOR);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(x0, y0, x0 + bar, y0);
        g.drawLine(x0, y0 - 4, x0, y0 + 4);
        g.drawLine(x0 + bar, y0 - 4, x0 + bar, y0 + 4);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        String lab = km >= 10 ? String.format(Locale.ROOT, "%.0f km", km) : String.format(Locale.ROOT, "%g km", km);
        g.drawString(lab, x0, y0 - 6);
    }

    // ---- hypocentres

    private void drawShapeLines(Graphics2D g, Volume3DRenderer.Camera cam) {
        g.setColor(new Color(45, 50, 65, 200));
        g.setStroke(new BasicStroke(1.0f));
        double z = renderer.sceneZ(renderer.volume().minDepth());
        for (double[] pl : shapeLines) {
            if (pl.length == 2) {
                double[] s = renderer.projectScene(cam, renderer.sceneX(pl[0]), renderer.sceneY(pl[1]), z);
                if (s != null) {
                    g.fillRect((int) Math.round(s[0]) - 1, (int) Math.round(s[1]) - 1, 2, 2);
                }
                continue;
            }
            int px = 0;
            int py = 0;
            boolean have = false;
            for (int i = 0; i + 1 < pl.length; i += 2) {
                double[] s = renderer.projectScene(cam, renderer.sceneX(pl[i]), renderer.sceneY(pl[i + 1]), z);
                if (s == null) {
                    have = false;
                    continue;
                }
                int x = (int) Math.round(s[0]);
                int y = (int) Math.round(s[1]);
                if (have) {
                    g.drawLine(px, py, x, y);
                }
                px = x;
                py = y;
                have = true;
            }
        }
    }

    private boolean occluded(double[] s, int iw, int ih) {
        int ix = (int) (s[0] / imageScale);
        int iy = (int) (s[1] / imageScale);
        if (ix >= 0 && iy >= 0 && ix < iw && iy < ih) {
            return zbuf[iy * iw + ix] < s[2] - 1.0;
        }
        return false;
    }

    private void drawStations(Graphics2D g, Volume3DRenderer.Camera cam, int iw, int ih) {
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 10f));
        for (StationPoint st : stations) {
            double[] s = renderer.project(cam, st.lon, st.lat, st.depthKm);
            if (s == null) {
                continue;
            }
            boolean hide = occluded(s, iw, ih);
            int cx = (int) Math.round(s[0]);
            int cy = (int) Math.round(s[1]);
            int r = 5;
            int[] xs = {cx - r, cx + r, cx};
            int[] ys = {cy - r, cy - r, cy + r};
            Color base = st.color != null ? st.color : Color.BLACK;
            g.setColor(hide ? new Color(base.getRed(), base.getGreen(), base.getBlue(), 50) : base);
            if (st.filled) {
                g.fillPolygon(xs, ys, 3);
            } else {
                g.setStroke(new BasicStroke(1.4f));
                g.drawPolygon(xs, ys, 3);
            }
            if (showStationLabels && !hide && st.code != null) {
                g.setColor(TEXT_COLOR);
                g.drawString(st.code, cx + r + 2, cy + 4);
            }
        }
    }

    private void drawHypocenters(Graphics2D g, Volume3DRenderer.Camera cam, int iw, int ih) {
        Color edge = new Color(0, 0, 0, 170);
        for (PointGroup grp : hypocenterGroups) {
            int r = grp.size > 0 ? grp.size : hypocenterSize;
            int i = 0;
            for (double[] p : grp.points) {
                Color base = grp.color == null ? new Color(255, 110, 0) : grp.color;
                if (grp.pointColors != null && i < grp.pointColors.size() && grp.pointColors.get(i) != null) {
                    base = grp.pointColors.get(i);
                }
                i++;
                Color visible = new Color(base.getRed(), base.getGreen(), base.getBlue(), 230);
                Color hidden = new Color(base.getRed(), base.getGreen(), base.getBlue(), 55);
                double[] s = renderer.project(cam, p[0], p[1], p[2]);
                if (s == null) {
                    continue;
                }
                boolean hide = occluded(s, iw, ih);
                int x = (int) Math.round(s[0]) - r / 2;
                int y = (int) Math.round(s[1]) - r / 2;
                g.setColor(hide ? hidden : visible);
                g.fillOval(x, y, r, r);
                if (!hide) {
                    g.setColor(edge);
                    g.drawOval(x, y, r, r);
                }
            }
        }
    }

    // ---- colour bar

    private void drawColorBar(Graphics2D g, int w, int h) {
        VelocityVolume3D v = renderer.volume();
        String ch = renderer.channel;
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        int bw = 14;
        int height = Math.max(40, Math.min(240, h - 120));
        int boxW = VelocityVolume3D.CH_LAYER.equals(ch) ? bw + 90 : bw + 48;
        int boxH = VelocityVolume3D.CH_LAYER.equals(ch) ? 4 * 20 + 20 : height + 20;
        int[] p = colorBarHandle.place(w, h, boxW, boxH);
        int x = p[0];
        int top = p[1] + 16;
        colorBarRect = new int[] {p[0], p[1], boxW, boxH};
        g.setColor(TEXT_COLOR);
        String title = channelTitle(ch);
        g.drawString(title, x, p[1] + 12);
        if (VelocityVolume3D.CH_LAYER.equals(ch)) {
            for (int i = 0; i < ColorMaps.LAYER_RGB.length; i++) {
                int yy = top + i * 20;
                g.setColor(new Color(ColorMaps.LAYER_RGB[i]));
                g.fillRect(x, yy, bw, 14);
                g.setColor(BOX_COLOR);
                g.drawRect(x, yy, bw, 14);
                g.setColor(TEXT_COLOR);
                g.drawString((i + 1) + " " + ColorMaps.LAYER_NAMES[i], x + bw + 5, yy + 12);
            }
            return;
        }
        for (int i = 0; i < height; i++) {
            double f = 1.0 - (double) i / (height - 1);
            g.setColor(new Color(renderer.colorOf(renderer.vmin + f * (renderer.vmax - renderer.vmin))));
            g.drawLine(x, top + i, x + bw, top + i);
        }
        g.setColor(BOX_COLOR);
        g.drawRect(x, top, bw, height - 1);
        g.setColor(TEXT_COLOR);
        double span = renderer.vmax - renderer.vmin;
        if (span > 0) {
            for (double t : niceTicks(renderer.vmin, renderer.vmax, 5)) {
                int yy = top + (int) Math.round((1.0 - (t - renderer.vmin) / span) * (height - 1));
                g.drawLine(x + bw, yy, x + bw + 3, yy);
                g.drawString(formatTick(t, span), x + bw + 6, yy + fm.getAscent() / 2 - 1);
            }
        }
    }

    private void drawCatalogColorBar(Graphics2D g, int w, int h) {
        MapView.ColorBarPanel bar = catalogColorBar;
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        int bw = 14;
        int height = Math.max(40, Math.min(240, h - 120));
        String title = bar.getLabel() != null && !bar.getLabel().isBlank() ? bar.getLabel() : "Catalog";
        boolean time = "Time".equalsIgnoreCase(title.trim());
        double min = bar.getMinValue();
        double max = bar.getMaxValue();
        double span = max - min;
        int labelW = fm.stringWidth(title);
        if (span > 0) {
            for (double t : niceTicks(min, max, 5)) {
                String tick = time
                    ? TimeFormatConverter.formatColorBarTime(t, min, max)
                    : formatTick(t, span);
                labelW = Math.max(labelW, fm.stringWidth(tick));
            }
        }
        int boxW = bw + 14 + labelW;
        int boxH = height + 20;
        if (showColorBar && colorBarHandle.isLaidOut() && !catalogColorBarHandle.hasUserPosition()) {
            int avail = Math.max(1, colorBarHandle.x() - 8);
            catalogColorBarHandle.placeIn(0, 0, avail, h, boxW, boxH);
        } else {
            catalogColorBarHandle.place(w, h, boxW, boxH);
        }
        int x = catalogColorBarHandle.x();
        int top = catalogColorBarHandle.y() + 16;
        catalogColorBarRect = new int[] {x, catalogColorBarHandle.y(), boxW, boxH};
        g.setColor(new Color(255, 255, 255, 200));
        g.fillRect(x, catalogColorBarHandle.y(), boxW, boxH);
        g.setColor(TEXT_COLOR);
        g.drawString(title, x + 4, catalogColorBarHandle.y() + 12);
        for (int i = 0; i < height; i++) {
            double f = 1.0 - (double) i / (height - 1);
            g.setColor(bar.colorAt(min + f * span));
            g.drawLine(x + 4, top + i, x + 4 + bw, top + i);
        }
        g.setColor(BOX_COLOR);
        g.drawRect(x + 4, top, bw, height - 1);
        g.setColor(TEXT_COLOR);
        if (span > 0) {
            for (double t : niceTicks(min, max, 5)) {
                int yy = top + (int) Math.round((1.0 - (t - min) / span) * (height - 1));
                g.drawLine(x + 4 + bw, yy, x + 4 + bw + 3, yy);
                String tick = time
                    ? TimeFormatConverter.formatColorBarTime(t, min, max)
                    : formatTick(t, span);
                g.drawString(tick, x + 4 + bw + 6, yy + fm.getAscent() / 2 - 1);
            }
        }
    }

    private static String formatTick(double t, double span) {
        int dec = span >= 20 ? 0 : (span >= 2 ? 1 : 2);
        return String.format(Locale.ROOT, "%." + dec + "f", t);
    }

    /** Human-readable title for a channel name. */
    public static String channelTitle(String ch) {
        if (VelocityVolume3D.CH_VS.equals(ch)) {
            return "Vs (km/s)";
        }
        if (VelocityVolume3D.CH_DVS_INIT.equals(ch)) {
            return "dVs from initial (%)";
        }
        if (VelocityVolume3D.CH_DVS_MEAN.equals(ch)) {
            return "dVs from mean (%)";
        }
        if (VelocityVolume3D.CH_LAYER.equals(ch)) {
            return "Layer";
        }
        return ch == null ? "" : ch;
    }
}
