package com.treloc.xtreloc.app.gui.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.swing.tool.CursorTool;
import org.geotools.swing.tool.InfoTool;
import org.geotools.swing.tool.PanTool;
import org.geotools.swing.tool.ZoomInTool;
import org.geotools.swing.tool.ZoomOutTool;

/**
 * Cursors for the map tool modes (pointer, zoom in, zoom out, pan, info). They are drawn with Java2D so they
 * are clearly visible on any platform (the bundled GeoTools cursor icons are tiny); each falls back to a standard
 * AWT cursor when custom cursors are not supported.
 */
public final class MapCursors {

    private static final Logger LOGGER = Logger.getLogger(MapCursors.class.getName());

    private static final int DRAW_SIZE = 32;
    private static final Color OUTLINE = Color.WHITE;
    private static final Color INK = new Color(20, 20, 20);

    private static Cursor zoomIn;
    private static Cursor zoomOut;
    private static Cursor info;

    private MapCursors() {}

    /** Cursor for the given map tool ({@code null} = pointer mode). */
    public static Cursor forTool(CursorTool tool) {
        if (tool == null) {
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
        if (tool instanceof ZoomInTool) {
            return zoomIn();
        }
        if (tool instanceof ZoomOutTool) {
            return zoomOut();
        }
        if (tool instanceof PanTool) {
            return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        }
        if (tool instanceof InfoTool) {
            return info();
        }
        return tool.getCursor();
    }

    public static synchronized Cursor zoomIn() {
        if (zoomIn == null) {
            zoomIn = createMagnifier(true, "Zoom in");
        }
        return zoomIn;
    }

    public static synchronized Cursor zoomOut() {
        if (zoomOut == null) {
            zoomOut = createMagnifier(false, "Zoom out");
        }
        return zoomOut;
    }

    public static synchronized Cursor info() {
        if (info == null) {
            info = createInfo();
        }
        return info;
    }

    private static Cursor createMagnifier(boolean plus, String name) {
        BufferedImage img = newCanvas();
        if (img == null) {
            return Cursor.getPredefinedCursor(plus ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
        }
        Graphics2D g = prepare(img);
        // lens centered at (12, 12), radius 8; handle toward the lower right
        drawBoth(g, () -> {
            g.drawOval(4, 4, 16, 16);
            g.drawLine(18, 18, 28, 28);
            g.drawLine(8, 12, 16, 12);
            if (plus) {
                g.drawLine(12, 8, 12, 16);
            }
        }, 3.2f, 1.8f);
        g.dispose();
        return toCursor(img, new Point(12, 12), name, plus ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
    }

    private static Cursor createInfo() {
        BufferedImage img = newCanvas();
        if (img == null) {
            return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        }
        Graphics2D g = prepare(img);
        drawBoth(g, () -> g.drawOval(5, 5, 22, 22), 3.2f, 1.8f);
        g.setFont(new Font(Font.SERIF, Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        int x = 16 - fm.stringWidth("i") / 2;
        int y = 16 + (fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(OUTLINE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                g.drawString("i", x + dx, y + dy);
            }
        }
        g.setColor(INK);
        g.drawString("i", x, y);
        g.dispose();
        return toCursor(img, new Point(16, 16), "Info", Cursor.HAND_CURSOR);
    }

    /** Strokes the shape twice: a wide white halo, then the dark ink line on top. */
    private static void drawBoth(Graphics2D g, Runnable shape, float haloWidth, float inkWidth) {
        g.setStroke(new BasicStroke(haloWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(OUTLINE);
        shape.run();
        g.setStroke(new BasicStroke(inkWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(INK);
        shape.run();
    }

    private static BufferedImage newCanvas() {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        Dimension best = Toolkit.getDefaultToolkit().getBestCursorSize(DRAW_SIZE, DRAW_SIZE);
        if (best.width < DRAW_SIZE || best.height < DRAW_SIZE) {
            return null;
        }
        return new BufferedImage(best.width, best.height, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D prepare(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static Cursor toCursor(BufferedImage img, Point hotSpot, String name, int fallbackType) {
        try {
            return Toolkit.getDefaultToolkit().createCustomCursor(img, hotSpot, name);
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Custom cursor not available: " + e.getMessage());
            return Cursor.getPredefinedCursor(fallbackType);
        }
    }
}
