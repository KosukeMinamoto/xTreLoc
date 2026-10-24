package com.treloc.xtreloc.app.gui.map2d;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.model.MapLayerStyle;
import com.treloc.xtreloc.app.gui.util.EventSelectionModel;
import com.treloc.xtreloc.app.gui.util.OverlayHandle;
import com.treloc.xtreloc.app.gui.util.WorkingLayer;
import com.treloc.xtreloc.app.gui.view.MapView;
import com.treloc.xtreloc.app.gui.volume3d.Volume3DCanvas;
import com.treloc.xtreloc.io.Station;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Interactive 2-D map: velocity background plus stations, hypocentres, shapefiles, error ellipses, network. */
public final class Map2DCanvas extends JComponent {

    private static final Color TEXT = new Color(35, 40, 52);
    private static final Color BOX = new Color(40, 44, 55);

    private final Map2DRenderer renderer = new Map2DRenderer();
    private MapView mapView;
    private BufferedImage image;
    private int[] pixels;

    public boolean showStations = true;
    public boolean showStationCodes = true;
    public boolean showHypocenters = true;
    public boolean showShapefiles = true;
    public boolean showGrid = true;
    public boolean showScale = true;
    public boolean showLegend = true;
    public boolean showColorBar = true;
    public boolean showCatalogColorBar = true;
    public boolean showCompass = true;
    public boolean showError = true;
    public boolean showNetwork = true;
    public int symbolSize = 8;
    /** Model Range spacing (km); grid lines are multiples of this. {@code <= 0} uses automatic ticks. */
    public double gridSpacingKm = 2.0;
    public MapLayerStyle gridStyle = MapLayerStyle.defaultGrid();

    public static final class StyledPolyline {
        public final double[] xy;
        public final Color color;
        public final BasicStroke stroke;

        public StyledPolyline(double[] xy, Color color, BasicStroke stroke) {
            this.xy = xy;
            this.color = color != null ? color : new Color(45, 50, 65, 200);
            this.stroke = stroke != null ? stroke : new BasicStroke(1.0f);
        }
    }

    private List<StyledPolyline> shapeLines = new ArrayList<>();
    private double[] highlight;
    private EventSelectionModel selectionModel = EventSelectionModel.getInstance();
    private final EventSelectionModel.Listener selectionListener = m -> repaint();
    private Supplier<List<CatalogInfo>> catalogSource =
        () -> mapView == null ? List.<CatalogInfo>of() : mapView.getCatalogInfos();
    /** Rubber band {x0, y0, x1, y1} in pixels while Shift-dragging, otherwise null. */
    private int[] rubber;
    private boolean rubberAdditive;
    private Consumer<EventKey> eventActivatedListener;
    private WorkingLayer workingLayer = WorkingLayer.getInstance();
    private final WorkingLayer.Listener workingListener = l -> repaint();
    /** True while rendering an exported image (selection rings are not part of the figure). */
    private boolean exporting;
    private int[] colorBarRect;
    private int[] catalogColorBarRect;
    private Runnable colorBarClickListener;
    private Runnable catalogColorBarClickListener;
    private final OverlayHandle legendHandle = new OverlayHandle(OverlayHandle.Corner.NW, 8);
    private final OverlayHandle colorBarHandle = new OverlayHandle(OverlayHandle.Corner.NE, 8);
    private final OverlayHandle catalogColorBarHandle = new OverlayHandle(OverlayHandle.Corner.NE, 8);
    private final OverlayHandle scaleHandle = new OverlayHandle(OverlayHandle.Corner.SW, 24);
    private final OverlayHandle compassHandle = new OverlayHandle(OverlayHandle.Corner.SE, 24);
    private OverlayHandle draggingOverlay;
    private int dragOffX;
    private int dragOffY;
    private boolean overlayMoved;

    public Map2DCanvas() {
        setOpaque(true);
        setBackground(Color.WHITE);
        setFocusable(true);
        setPreferredSize(new Dimension(640, 480));
        MouseAdapter ma = new MouseAdapter() {
            int lastX;
            int lastY;

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
                } else if (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown()) {
                    rubber = new int[] {e.getX(), e.getY(), e.getX(), e.getY()};
                    rubberAdditive = e.isControlDown() || e.isMetaDown();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (rubber != null) {
                    rubber[2] = e.getX();
                    rubber[3] = e.getY();
                    repaint();
                    return;
                }
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
                renderer.panX += e.getX() - lastX;
                renderer.panY += e.getY() - lastY;
                lastX = e.getX();
                lastY = e.getY();
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                OverlayHandle hit = pickOverlay(e.getX(), e.getY());
                if (hit != null) {
                    setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                } else {
                    setCursor(java.awt.Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggingOverlay = null;
                if (rubber != null) {
                    int[] r = rubber;
                    rubber = null;
                    overlayMoved = true;
                    applyRectangleSelection(r[0], r[1], r[2], r[3], rubberAdditive);
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                e.consume();
                int w = getWidth();
                int h = getHeight();
                double notches = e.getPreciseWheelRotation();
                if (Math.abs(notches) < 1e-6) {
                    notches = e.getWheelRotation();
                }
                if (Math.abs(notches) < 1e-6) {
                    return;
                }
                double[] world = renderer.toWorld(w, h, e.getX(), e.getY());
                renderer.zoom *= Math.pow(1.12, -notches);
                renderer.zoom = Math.max(0.15, Math.min(80.0, renderer.zoom));
                double[] now = renderer.toPixel(w, h, world[0], world[1]);
                renderer.panX += e.getX() - now[0];
                renderer.panY += e.getY() - now[1];
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (overlayMoved) {
                    return;
                }
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)
                    && pickOverlay(e.getX(), e.getY()) == null) {
                    Hit hit = eventActivatedListener == null ? null : pickNearest(e.getX(), e.getY());
                    if (hit != null) {
                        eventActivatedListener.accept(hit.key());
                        return;
                    }
                    renderer.resetView();
                    repaint();
                    return;
                }
                if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e)) {
                    if (catalogColorBarClickListener != null && hitCatalogColorBar(e.getX(), e.getY())) {
                        catalogColorBarClickListener.run();
                    } else if (colorBarClickListener != null && hitColorBar(e.getX(), e.getY())) {
                        colorBarClickListener.run();
                    } else {
                        applyClickSelection(e.getX(), e.getY(), e.isControlDown() || e.isMetaDown());
                    }
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    public Map2DRenderer renderer() {
        return renderer;
    }

    public void setMapView(MapView view) {
        this.mapView = view;
        repaint();
    }

    public void setShapeLines(List<StyledPolyline> lines) {
        this.shapeLines = lines == null ? new ArrayList<>() : lines;
        repaint();
    }

    public void setHighlight(double lon, double lat) {
        this.highlight = Double.isFinite(lon) && Double.isFinite(lat) ? new double[] {lon, lat} : null;
        repaint();
    }

    public void clearHighlight() {
        this.highlight = null;
        repaint();
    }

    public void setColorBarClickListener(Runnable r) {
        this.colorBarClickListener = r;
    }

    public void setCatalogColorBarClickListener(Runnable r) {
        this.catalogColorBarClickListener = r;
    }

    private boolean catalogColorBarOn() {
        return showCatalogColorBar && mapView != null && mapView.hasCatalogColorMap() && mapView.getColorBar() != null;
    }

    public BufferedImage snapshot() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        exporting = true;
        try {
            paintMap(g, w, h);
        } finally {
            exporting = false;
            g.dispose();
        }
        return out;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            paintMap(g, getWidth(), getHeight());
        } finally {
            g.dispose();
        }
    }

    private void paintMap(Graphics2D g, int w, int h) {
        if (w < 4 || h < 4) {
            return;
        }
        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        }
        renderer.renderBackground(w, h, pixels);
        g.drawImage(image, 0, 0, null);
        if (!renderer.showVelocity) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
        } else {
            renderer.drawContours(g, w, h);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (showGrid) {
            drawGrid(g, w, h);
        }
        if (showShapefiles) {
            drawShapes(g, w, h);
        }
        if (mapView != null && showNetwork && mapView.isShowConnections() && renderer.collapse == 2) {
            drawNetwork(g, w, h);
        }
        if (mapView != null && showHypocenters) {
            drawHypos(g, w, h);
        }
        drawWorkingVectors(g, w, h);
        drawSelection(g, w, h);
        if (mapView != null && showStations) {
            drawStations(g, w, h);
        }
        if (highlight != null && renderer.collapse == 2) {
            double[] p = renderer.toPixel(w, h, highlight[0], highlight[1]);
            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(2f));
            g.drawOval((int) p[0] - 8, (int) p[1] - 8, 16, 16);
        }
        if (showScale) {
            drawScale(g, w, h);
        }
        if (showLegend) {
            drawLegend(g, w, h);
        }
        if (showCompass && renderer.collapse == 2) {
            drawCompass(g, w, h);
        }
        if (showColorBar && renderer.showVelocity) {
            drawColorBar(g, w, h);
        } else {
            colorBarRect = null;
        }
        if (catalogColorBarOn()) {
            drawCatalogColorBar(g, w, h);
        } else {
            catalogColorBarRect = null;
        }
    }

    // ------------------------------------------------------------------ event selection

    /** One hypocentre of a visible catalog with its position on screen. */
    static final class Hit {
        final CatalogInfo catalog;
        final Hypocenter hypocenter;
        final double x;
        final double y;
        final int size;

        Hit(CatalogInfo catalog, Hypocenter hypocenter, double x, double y, int size) {
            this.catalog = catalog;
            this.hypocenter = hypocenter;
            this.x = x;
            this.y = y;
            this.size = size;
        }

        EventKey key() {
            return EventKey.of(catalog, hypocenter);
        }
    }

    private interface HypoVisitor {
        void visit(Hit hit);
    }

    /** Model that receives the selection made on the map (default: the shared model). */
    public void setSelectionModel(EventSelectionModel model) {
        EventSelectionModel next = model != null ? model : EventSelectionModel.getInstance();
        if (isDisplayable()) {
            selectionModel.removeListener(selectionListener);
            next.addListener(selectionListener);
        }
        selectionModel = next;
        repaint();
    }

    /** Called with the event under the pointer when it is double-clicked (for example "open in Seismic"). */
    public void setEventActivatedListener(Consumer<EventKey> listener) {
        this.eventActivatedListener = listener;
    }

    /** Source of the vectors drawn from old to new position for relocated events. */
    void setWorkingLayer(WorkingLayer layer) {
        this.workingLayer = layer != null ? layer : WorkingLayer.getInstance();
        repaint();
    }

    public EventSelectionModel selectionModel() {
        return selectionModel;
    }

    /** Replaces where the hit test and the selection rings take the catalogs from. */
    void setCatalogSource(Supplier<List<CatalogInfo>> source) {
        this.catalogSource = source != null ? source : List::of;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        selectionModel.addListener(selectionListener);
        workingLayer.addListener(workingListener);
    }

    @Override
    public void removeNotify() {
        selectionModel.removeListener(selectionListener);
        workingLayer.removeListener(workingListener);
        super.removeNotify();
    }

    private void forEachHypo(int w, int h, HypoVisitor visitor) {
        if (!showHypocenters) {
            return;
        }
        for (CatalogInfo c : catalogSource.get()) {
            if (!c.isVisible() || c.getHypocenters() == null) {
                continue;
            }
            int s = Math.max(4, c.getSymbolSize() > 0 ? c.getSymbolSize() : symbolSize);
            for (Hypocenter hypo : c.getHypocenters()) {
                if (!Double.isFinite(hypo.lon) || !Double.isFinite(hypo.lat)) {
                    continue;
                }
                double[] uv = projectLonLatDepth(hypo.lon, hypo.lat, hypo.depth);
                if (uv == null) {
                    continue;
                }
                double[] p = renderer.toPixel(w, h, uv[0], uv[1]);
                visitor.visit(new Hit(c, hypo, p[0], p[1], s));
            }
        }
    }

    /** Nearest hypocentre within its symbol radius plus a small margin, or {@code null}. */
    Hit pickNearest(double x, double y) {
        final Hit[] best = new Hit[1];
        final double[] bestD = {Double.MAX_VALUE};
        forEachHypo(getWidth(), getHeight(), hit -> {
            double d = Math.hypot(hit.x - x, hit.y - y);
            if (d <= Math.max(6.0, hit.size / 2.0 + 3.0) && d < bestD[0]) {
                bestD[0] = d;
                best[0] = hit;
            }
        });
        return best[0];
    }

    /** All hypocentres whose symbol centre lies inside the rectangle (corners in any order). */
    List<Hit> pickInRect(double xa, double ya, double xb, double yb) {
        final double x0 = Math.min(xa, xb);
        final double x1 = Math.max(xa, xb);
        final double y0 = Math.min(ya, yb);
        final double y1 = Math.max(ya, yb);
        final List<Hit> out = new ArrayList<>();
        forEachHypo(getWidth(), getHeight(), hit -> {
            if (hit.x >= x0 && hit.x <= x1 && hit.y >= y0 && hit.y <= y1) {
                out.add(hit);
            }
        });
        return out;
    }

    /** Click: select the event under the pointer; Ctrl / Cmd toggles; empty space clears. */
    void applyClickSelection(double x, double y, boolean toggle) {
        Hit hit = pickNearest(x, y);
        if (hit != null) {
            if (toggle) {
                selectionModel.toggle(hit.key());
            } else {
                selectionModel.select(hit.key());
            }
        } else if (!toggle) {
            selectionModel.clear();
        }
    }

    /** Rectangle: replaces the selection, or extends it when {@code additive}. */
    void applyRectangleSelection(double xa, double ya, double xb, double yb, boolean additive) {
        List<EventKey> keys = new ArrayList<>();
        for (Hit hit : pickInRect(xa, ya, xb, yb)) {
            keys.add(hit.key());
        }
        if (additive) {
            selectionModel.addAll(keys);
        } else {
            selectionModel.setSelection(keys);
        }
    }

    /** Arrow from the catalog position to the relocated position of every event in the Working layer. */
    private void drawWorkingVectors(Graphics2D g, int w, int h) {
        if (!showHypocenters || workingLayer.size() == 0) {
            return;
        }
        boolean visible = false;
        for (CatalogInfo c : catalogSource.get()) {
            if (WorkingLayer.CATALOG_NAME.equals(c.getName()) && c.isVisible()) {
                visible = true;
                break;
            }
        }
        if (!visible) {
            return;
        }
        g.setColor(new Color(60, 60, 60, 200));
        g.setStroke(new BasicStroke(1.4f));
        for (WorkingLayer.Entry e : workingLayer.entries()) {
            double[] a = projectLonLatDepth(e.before.lon, e.before.lat, e.before.depth);
            double[] b = projectLonLatDepth(e.after.lon, e.after.lat, e.after.depth);
            if (a == null || b == null) {
                continue;
            }
            double[] pa = renderer.toPixel(w, h, a[0], a[1]);
            double[] pb = renderer.toPixel(w, h, b[0], b[1]);
            double dx = pb[0] - pa[0];
            double dy = pb[1] - pa[1];
            double len = Math.hypot(dx, dy);
            if (len < 1.5) {
                continue;
            }
            g.drawLine((int) Math.round(pa[0]), (int) Math.round(pa[1]),
                (int) Math.round(pb[0]), (int) Math.round(pb[1]));
            double ux = dx / len;
            double uy = dy / len;
            double head = Math.min(9.0, len * 0.5);
            int[] xs = {(int) Math.round(pb[0]),
                (int) Math.round(pb[0] - head * ux + head * 0.45 * uy),
                (int) Math.round(pb[0] - head * ux - head * 0.45 * uy)};
            int[] ys = {(int) Math.round(pb[1]),
                (int) Math.round(pb[1] - head * uy - head * 0.45 * ux),
                (int) Math.round(pb[1] - head * uy + head * 0.45 * ux)};
            g.fillPolygon(xs, ys, 3);
        }
    }

    private void drawSelection(Graphics2D g, int w, int h) {
        if (exporting) {
            return;
        }
        if (!selectionModel.isEmpty()) {
            final Set<EventKey> selected = selectionModel.selection();
            final EventKey focus = selectionModel.focus();
            final Color ring = new Color(255, 190, 0);
            final Color focusRing = new Color(235, 60, 20);
            forEachHypo(w, h, hit -> {
                EventKey key = hit.key();
                if (!selected.contains(key)) {
                    return;
                }
                boolean isFocus = key.equals(focus);
                int r = hit.size / 2 + (isFocus ? 6 : 4);
                g.setColor(isFocus ? focusRing : ring);
                g.setStroke(new BasicStroke(isFocus ? 3f : 2f));
                g.drawOval((int) Math.round(hit.x) - r, (int) Math.round(hit.y) - r, 2 * r, 2 * r);
            });
        }
        if (rubber != null) {
            int x = Math.min(rubber[0], rubber[2]);
            int y = Math.min(rubber[1], rubber[3]);
            int rw = Math.abs(rubber[2] - rubber[0]);
            int rh = Math.abs(rubber[3] - rubber[1]);
            g.setColor(new Color(40, 120, 220, 40));
            g.fillRect(x, y, rw, rh);
            g.setColor(new Color(40, 120, 220));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {4f, 3f}, 0f));
            g.drawRect(x, y, rw, rh);
        }
    }

    private void drawGrid(Graphics2D g, int w, int h) {
        double[] b = renderer.worldBounds();
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 10f));
        MapLayerStyle gs = gridStyle != null ? gridStyle : MapLayerStyle.defaultGrid();
        g.setStroke(gs.stroke());
        Color line = gs.colorWithOpacity();
        for (double u : axisTicks(b[0], b[1], true)) {
            double[] p0 = renderer.toPixel(w, h, u, b[2]);
            double[] p1 = renderer.toPixel(w, h, u, b[3]);
            g.setColor(line);
            g.drawLine((int) p0[0], (int) p0[1], (int) p1[0], (int) p1[1]);
            g.setColor(TEXT);
            g.drawString(labelU(u), (int) p1[0] - 10, h - 6);
        }
        for (double v : axisTicks(b[2], b[3], false)) {
            double[] p0 = renderer.toPixel(w, h, b[0], v);
            double[] p1 = renderer.toPixel(w, h, b[1], v);
            g.setColor(line);
            g.drawLine((int) p0[0], (int) p0[1], (int) p1[0], (int) p1[1]);
            g.setColor(TEXT);
            g.drawString(labelV(v), 6, (int) p0[1] + 4);
        }
        g.setStroke(new BasicStroke(1f));
    }

    /**
     * Ticks at multiples of the Model Range spacing, thinned so neighbouring lines stay ≥ 24 px apart.
     * Horizontal axes are degrees (spacing converted with the local km/deg); depth is kilometres.
     */
    private double[] axisTicks(double lo, double hi, boolean horizontal) {
        double span = hi - lo;
        if (!(span > 0)) {
            return new double[] {lo};
        }
        double stepWorld = spacingWorld(horizontal);
        if (!(stepWorld > 0)) {
            return nice(lo, hi, 6);
        }
        double[] p0 = horizontal
            ? renderer.toPixel(Math.max(1, getWidth()), Math.max(1, getHeight()), lo, 0.5 * (renderer.worldBounds()[2] + renderer.worldBounds()[3]))
            : renderer.toPixel(Math.max(1, getWidth()), Math.max(1, getHeight()), 0.5 * (renderer.worldBounds()[0] + renderer.worldBounds()[1]), lo);
        double[] p1 = horizontal
            ? renderer.toPixel(Math.max(1, getWidth()), Math.max(1, getHeight()), lo + stepWorld, 0.5 * (renderer.worldBounds()[2] + renderer.worldBounds()[3]))
            : renderer.toPixel(Math.max(1, getWidth()), Math.max(1, getHeight()), 0.5 * (renderer.worldBounds()[0] + renderer.worldBounds()[1]), lo + stepWorld);
        double px = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
        int mult = 1;
        if (px > 1e-6 && px < 24) {
            mult = (int) Math.ceil(24.0 / px);
        }
        double step = stepWorld * mult;
        double first = Math.ceil(lo / step - 1e-9) * step;
        List<Double> out = new ArrayList<>();
        for (double v = first; v <= hi + 1e-9 * span; v += step) {
            out.add(v);
        }
        if (out.isEmpty()) {
            return nice(lo, hi, 6);
        }
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }
        return a;
    }

    private double spacingWorld(boolean horizontal) {
        double s = gridSpacingKm > 0 ? gridSpacingKm : 2.0;
        boolean depthAxis = !horizontal && renderer.collapse != 2;
        if (depthAxis) {
            return s;
        }
        if (horizontal && renderer.collapse == 0) {
            return s / 111.19;
        }
        double lat = 0.5 * (renderer.latMin + renderer.latMax);
        if (horizontal && (renderer.collapse == 2 || renderer.collapse == 1)) {
            return s / Math.max(1e-6, 111.19 * Math.cos(Math.toRadians(lat)));
        }
        return s / 111.19;
    }

    private String labelU(double u) {
        if (renderer.collapse == 0) {
            return String.format(Locale.ROOT, "%.2f°", u);
        }
        return String.format(Locale.ROOT, "%.2f°", u);
    }

    private String labelV(double v) {
        if (renderer.collapse == 2) {
            return String.format(Locale.ROOT, "%.2f°", v);
        }
        return String.format(Locale.ROOT, "%.0f km", v);
    }

    private void drawShapes(Graphics2D g, int w, int h) {
        if (renderer.collapse != 2) {
            return;
        }
        for (StyledPolyline sl : shapeLines) {
            double[] pl = sl.xy;
            g.setColor(sl.color);
            g.setStroke(sl.stroke);
            if (pl.length < 4) {
                if (pl.length == 2) {
                    double[] p = renderer.toPixel(w, h, pl[0], pl[1]);
                    g.fillRect((int) p[0], (int) p[1], 2, 2);
                }
                continue;
            }
            int px = 0;
            int py = 0;
            boolean have = false;
            for (int i = 0; i + 1 < pl.length; i += 2) {
                double[] p = renderer.toPixel(w, h, pl[i], pl[i + 1]);
                int x = (int) Math.round(p[0]);
                int y = (int) Math.round(p[1]);
                if (have) {
                    g.drawLine(px, py, x, y);
                }
                px = x;
                py = y;
                have = true;
            }
        }
    }

    private double sliceTolerance() {
        double s = gridSpacingKm > 0 ? gridSpacingKm : 2.0;
        if (renderer.collapse == 0) {
            return s / Math.max(1e-6, 111.19 * Math.cos(Math.toRadians(0.5 * (renderer.latMin + renderer.latMax))));
        }
        if (renderer.collapse == 1) {
            return s / 111.19;
        }
        return s;
    }

    private double[] projectLonLatDepth(double lon, double lat, double depthKm) {
        if (renderer.collapse == 2) {
            return new double[] {lon, lat};
        }
        double tol = sliceTolerance();
        if (renderer.collapse == 0) {
            if (Math.abs(lon - renderer.slicePos) > tol) {
                return null;
            }
            return new double[] {lat, depthKm};
        }
        if (Math.abs(lat - renderer.slicePos) > tol) {
            return null;
        }
        return new double[] {lon, depthKm};
    }

    private void drawStations(Graphics2D g, int w, int h) {
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 10f));
        for (Station st : mapView.getDisplayedStations()) {
            if (!Double.isFinite(st.getLon()) || !Double.isFinite(st.getLat())) {
                continue;
            }
            double depKm = Double.isFinite(st.getDep()) ? -st.getDep() / 1000.0 : 0.0;
            double[] uv = projectLonLatDepth(st.getLon(), st.getLat(), depKm);
            if (uv == null) {
                continue;
            }
            double[] p = renderer.toPixel(w, h, uv[0], uv[1]);
            int cx = (int) p[0];
            int cy = (int) p[1];
            com.treloc.xtreloc.app.gui.model.MapLayerStyle style = mapView.styleForStation(st);
            int s = Math.max(4, Math.round(style.getSize()));
            g.setColor(style.colorWithOpacity());
            drawSymbol(g, style.getSymbol(), cx, cy, s, mapView.isStationFilled(st));
            if (showStationCodes && st.getCode() != null && !st.getCode().isEmpty()) {
                g.setColor(TEXT);
                g.drawString(st.getCode(), cx + s / 2 + 2, cy + 4);
            }
        }
    }

    private void drawHypos(Graphics2D g, int w, int h) {
        for (CatalogInfo c : mapView.getCatalogInfos()) {
            if (!c.isVisible() || c.getHypocenters() == null) {
                continue;
            }
            int s = Math.max(4, c.getSymbolSize() > 0 ? c.getSymbolSize() : symbolSize);
            boolean err = showError && (mapView.isShowErrorEllipse() || c.isShowErrorEllipse());
            for (Hypocenter hypo : c.getHypocenters()) {
                if (!Double.isFinite(hypo.lon) || !Double.isFinite(hypo.lat)) {
                    continue;
                }
                double[] uv = projectLonLatDepth(hypo.lon, hypo.lat, hypo.depth);
                if (uv == null) {
                    continue;
                }
                Color col = mapView.colorOf(c, hypo);
                double[] p = renderer.toPixel(w, h, uv[0], uv[1]);
                int cx = (int) Math.round(p[0]);
                int cy = (int) Math.round(p[1]);
                if (err) {
                    drawError(g, w, h, hypo, uv, p, col);
                }
                g.setColor(col);
                drawSymbol(g, c.getSymbolType(), cx, cy, s);
            }
        }
    }

    private void drawError(Graphics2D g, int w, int h, Hypocenter hypo, double[] uv, double[] p, Color col) {
        double ru;
        double rv;
        if (renderer.collapse == 2) {
            if (!(hypo.xerr > 0 && hypo.yerr > 0)) {
                return;
            }
            double[] ex = renderer.toPixel(w, h, hypo.lon + hypo.xerr / kmPerDegLon(hypo.lat), hypo.lat);
            double[] ey = renderer.toPixel(w, h, hypo.lon, hypo.lat + hypo.yerr / 111.19);
            ru = Math.abs(ex[0] - p[0]);
            rv = Math.abs(ey[1] - p[1]);
        } else {
            double hErr = renderer.collapse == 0 ? hypo.yerr : hypo.xerr;
            if (!(hErr > 0 || hypo.zerr > 0)) {
                return;
            }
            double du = hErr > 0
                ? (renderer.collapse == 0 ? hErr / 111.19 : hErr / kmPerDegLon(hypo.lat))
                : 0;
            double[] ex = renderer.toPixel(w, h, uv[0] + du, uv[1]);
            double[] ez = renderer.toPixel(w, h, uv[0], uv[1] + Math.max(0, hypo.zerr));
            ru = Math.abs(ex[0] - p[0]);
            rv = Math.abs(ez[1] - p[1]);
        }
        int rx = Math.max(2, (int) ru);
        int ry = Math.max(2, (int) rv);
        int cx = (int) Math.round(p[0]);
        int cy = (int) Math.round(p[1]);
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 80));
        g.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 160));
        g.drawOval(cx - rx, cy - ry, rx * 2, ry * 2);
    }

    private void drawNetwork(Graphics2D g, int w, int h) {
        com.treloc.xtreloc.app.gui.model.MapLayerStyle ns = mapView.getNetworkStyle();
        g.setColor(ns.colorWithOpacity());
        g.setStroke(ns.stroke());
        for (double[] seg : mapView.connectionSegments()) {
            double[] a = renderer.toPixel(w, h, seg[0], seg[1]);
            double[] b = renderer.toPixel(w, h, seg[2], seg[3]);
            g.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]);
        }
    }

    private static void drawSymbol(Graphics2D g, CatalogInfo.SymbolType t, int cx, int cy, int s) {
        drawSymbol(g, t, cx, cy, s, true);
    }

    private static void drawSymbol(Graphics2D g, CatalogInfo.SymbolType t, int cx, int cy, int s, boolean filled) {
        int h = s / 2;
        if (t == CatalogInfo.SymbolType.CROSS) {
            g.setStroke(new BasicStroke(1.6f));
            g.drawLine(cx - h, cy, cx + h, cy);
            g.drawLine(cx, cy - h, cx, cy + h);
            return;
        }
        if (t == CatalogInfo.SymbolType.SQUARE) {
            if (filled) {
                g.fillRect(cx - h, cy - h, s, s);
            } else {
                g.setStroke(new BasicStroke(1.4f));
                g.drawRect(cx - h, cy - h, s, s);
            }
        } else if (t == CatalogInfo.SymbolType.TRIANGLE) {
            int[] xs = {cx, cx - h, cx + h};
            int[] ys = {cy - h, cy + h, cy + h};
            if (filled) {
                g.fillPolygon(xs, ys, 3);
            } else {
                g.setStroke(new BasicStroke(1.4f));
                g.drawPolygon(xs, ys, 3);
            }
        } else if (t == CatalogInfo.SymbolType.DIAMOND) {
            int[] xs = {cx, cx + h, cx, cx - h};
            int[] ys = {cy - h, cy, cy + h, cy};
            if (filled) {
                g.fillPolygon(xs, ys, 4);
            } else {
                g.setStroke(new BasicStroke(1.4f));
                g.drawPolygon(xs, ys, 4);
            }
        } else if (t == CatalogInfo.SymbolType.STAR) {
            int[] xs = {cx, cx + 2, cx + h, cx + 2, cx, cx - 2, cx - h, cx - 2};
            int[] ys = {cy - h, cy - 2, cy, cy + 2, cy + h, cy + 2, cy, cy - 2};
            if (filled) {
                g.fillPolygon(xs, ys, 8);
            } else {
                g.setStroke(new BasicStroke(1.4f));
                g.drawPolygon(xs, ys, 8);
            }
        } else if (filled) {
            g.fillOval(cx - h, cy - h, s, s);
        } else {
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(cx - h, cy - h, s, s);
        }
    }

    private OverlayHandle pickOverlay(int px, int py) {
        if (showLegend && legendHandle.contains(px, py)) {
            return legendHandle;
        }
        if (showColorBar && renderer.showVelocity && colorBarHandle.contains(px, py)) {
            return colorBarHandle;
        }
        if (catalogColorBarOn() && catalogColorBarHandle.contains(px, py)) {
            return catalogColorBarHandle;
        }
        if (showScale && scaleHandle.contains(px, py)) {
            return scaleHandle;
        }
        if (showCompass && renderer.collapse == 2 && compassHandle.contains(px, py)) {
            return compassHandle;
        }
        return null;
    }

    private void drawCompass(Graphics2D g, int w, int h) {
        int r = 18;
        int[] p = compassHandle.place(w, h, 2 * r, 2 * r);
        int cx = p[0] + r;
        int cy = p[1] + r;
        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(new Color(70, 75, 90, 160));
        g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
        FontMetrics fm = g.getFontMetrics();
        String[] lab = {"N", "E", "S", "W"};
        int[][] d = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            int lx = cx + d[i][0] * (r - 8);
            int ly = cy + d[i][1] * (r - 8);
            g.setColor(i == 0 ? new Color(180, 40, 40) : TEXT);
            g.drawString(lab[i], lx - fm.stringWidth(lab[i]) / 2, ly + fm.getAscent() / 2 - 1);
        }
    }

    private void drawLegend(Graphics2D g, int w, int h) {
        if (mapView == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        if (showStations) {
            names.add("Stations");
            colors.add(new Color(20, 20, 20));
        }
        for (CatalogInfo c : mapView.getCatalogInfos()) {
            if (c != null && c.isVisible()) {
                names.add(c.getName() != null ? c.getName() : "catalog");
                colors.add(c.getColor() != null ? c.getColor() : Color.GRAY);
            }
        }
        if (names.isEmpty()) {
            return;
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
        int[] p = legendHandle.place(w, h, lw, lh);
        int x = p[0];
        int y = p[1];
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRoundRect(x, y, lw, lh, 6, 6);
        g.setColor(new Color(70, 75, 90, 140));
        g.drawRoundRect(x, y, lw, lh, 6, 6);
        int ty = y + pad + fm.getAscent();
        for (int i = 0; i < names.size(); i++) {
            g.setColor(colors.get(i));
            if (i == 0 && showStations && "Stations".equals(names.get(0))) {
                int[] xs = {x + pad + box / 2, x + pad, x + pad + box};
                int[] ys = {ty - fm.getAscent() + 2, ty - fm.getAscent() + box, ty - fm.getAscent() + box};
                g.fillPolygon(xs, ys, 3);
            } else {
                g.fillRect(x + pad, ty - fm.getAscent() + 2, box, box);
            }
            g.setColor(TEXT);
            g.drawString(names.get(i), x + pad + box + 6, ty);
            ty += rowH;
        }
    }

    private void drawScale(Graphics2D g, int w, int h) {
        double kmPx = renderer.kmPerPixel(w, h);
        if (!(kmPx > 1e-9)) {
            return;
        }
        double raw = 80.0 * kmPx;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / mag;
        double km = (f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10) * mag;
        int bar = (int) Math.round(km / kmPx);
        int boxW = bar + 12;
        int boxH = 22;
        int[] p = scaleHandle.place(w, h, boxW, boxH);
        int x0 = p[0] + 6;
        int y0 = p[1] + 16;
        g.setColor(new Color(255, 255, 255, 200));
        g.fillRect(p[0], p[1], boxW, boxH);
        g.setColor(TEXT);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x0, y0, x0 + bar, y0);
        g.drawLine(x0, y0 - 3, x0, y0 + 3);
        g.drawLine(x0 + bar, y0 - 3, x0 + bar, y0 + 3);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        String lab = km >= 10 ? String.format(Locale.ROOT, "%.0f km", km) : String.format(Locale.ROOT, "%g km", km);
        g.drawString(lab, x0, y0 - 6);
    }

    private void drawColorBar(Graphics2D g, int w, int h) {
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        int bw = 14;
        int height = Math.max(40, Math.min(240, h - 120));
        String title = Volume3DCanvas.channelTitle(renderer.channel);
        double span = renderer.vmax - renderer.vmin;
        int labelW = fm.stringWidth(title);
        if (span > 0) {
            for (double t : nice(renderer.vmin, renderer.vmax, 5)) {
                int dec = span >= 20 ? 0 : (span >= 2 ? 1 : 2);
                labelW = Math.max(labelW, fm.stringWidth(String.format(Locale.ROOT, "%." + dec + "f", t)));
            }
        }
        int boxW = bw + 14 + labelW;
        int boxH = height + 20;
        int[] p = colorBarHandle.place(w, h, boxW, boxH);
        int x = p[0];
        int top = p[1] + 16;
        colorBarRect = new int[] {p[0], p[1], boxW, boxH};
        g.setColor(TEXT);
        g.drawString(title, x, p[1] + 12);
        for (int i = 0; i < height; i++) {
            double f = 1.0 - (double) i / (height - 1);
            g.setColor(new Color(renderer.colorOf(renderer.vmin + f * (renderer.vmax - renderer.vmin))));
            g.drawLine(x, top + i, x + bw, top + i);
        }
        g.setColor(BOX);
        g.drawRect(x, top, bw, height - 1);
        g.setColor(TEXT);
        if (span > 0) {
            for (double t : nice(renderer.vmin, renderer.vmax, 5)) {
                int yy = top + (int) Math.round((1.0 - (t - renderer.vmin) / span) * (height - 1));
                g.drawLine(x + bw, yy, x + bw + 3, yy);
                int dec = span >= 20 ? 0 : (span >= 2 ? 1 : 2);
                g.drawString(String.format(Locale.ROOT, "%." + dec + "f", t), x + bw + 6, yy + fm.getAscent() / 2 - 1);
            }
        }
    }

    private void drawCatalogColorBar(Graphics2D g, int w, int h) {
        MapView.ColorBarPanel bar = mapView.getColorBar();
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
            for (double t : nice(min, max, 5)) {
                String tick = time
                    ? TimeFormatConverter.formatColorBarTime(t, min, max)
                    : formatCatalogTick(t, span);
                labelW = Math.max(labelW, fm.stringWidth(tick));
            }
        }
        int boxW = bw + 14 + labelW;
        int boxH = height + 20;
        if (showColorBar && renderer.showVelocity && colorBarHandle.isLaidOut()
            && !catalogColorBarHandle.hasUserPosition()) {
            int avail = Math.max(1, colorBarHandle.x() - 8);
            catalogColorBarHandle.placeIn(0, 0, avail, h, boxW, boxH);
        } else {
            catalogColorBarHandle.place(w, h, boxW, boxH);
        }
        int x = catalogColorBarHandle.x();
        int top = catalogColorBarHandle.y() + 16;
        catalogColorBarRect = new int[] {x, catalogColorBarHandle.y(), boxW, boxH};
        g.setColor(TEXT);
        g.drawString(title, x, catalogColorBarHandle.y() + 12);
        for (int i = 0; i < height; i++) {
            double f = 1.0 - (double) i / (height - 1);
            g.setColor(bar.colorAt(min + f * span));
            g.drawLine(x, top + i, x + bw, top + i);
        }
        g.setColor(BOX);
        g.drawRect(x, top, bw, height - 1);
        g.setColor(TEXT);
        if (span > 0) {
            for (double t : nice(min, max, 5)) {
                int yy = top + (int) Math.round((1.0 - (t - min) / span) * (height - 1));
                g.drawLine(x + bw, yy, x + bw + 3, yy);
                String tick = time
                    ? TimeFormatConverter.formatColorBarTime(t, min, max)
                    : formatCatalogTick(t, span);
                g.drawString(tick, x + bw + 6, yy + fm.getAscent() / 2 - 1);
            }
        }
    }

    private static String formatCatalogTick(double t, double span) {
        int dec = span >= 20 ? 0 : (span >= 2 ? 1 : 2);
        return String.format(Locale.ROOT, "%." + dec + "f", t);
    }

    private boolean hitColorBar(int x, int y) {
        int[] r = colorBarRect;
        return r != null && x >= r[0] && y >= r[1] && x <= r[0] + r[2] && y <= r[1] + r[3];
    }

    private boolean hitCatalogColorBar(int x, int y) {
        int[] r = catalogColorBarRect;
        return r != null && x >= r[0] && y >= r[1] && x <= r[0] + r[2] && y <= r[1] + r[3];
    }

    private static double kmPerDegLon(double lat) {
        return 111.19 * Math.cos(Math.toRadians(lat));
    }

    private static double[] nice(double lo, double hi, int target) {
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
            out.add(v);
        }
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }
        return a;
    }
}
