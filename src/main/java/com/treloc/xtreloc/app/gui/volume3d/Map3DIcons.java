package com.treloc.xtreloc.app.gui.volume3d;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/** Small vector icons for the 3D map tool strip (drawn, so they follow the theme text colour). */
public final class Map3DIcons implements Icon {

    public enum Kind { MODEL, PARAMETER, RANGE, SLICE, VIEW, EXPORT, TABLE, TOOLS, PAN, EDIT, RESET, REFRESH }

    private final Kind kind;
    private final int size;
    private final Color color;

    public Map3DIcons(Kind kind, int size, Color color) {
        this.kind = kind;
        this.size = size;
        this.color = color;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x0, int y0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x0, y0);
            double s = size / 24.0;
            g.scale(s, s);
            Color col = c != null && !c.isEnabled() ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 90)
                : color;
            g.setColor(col);
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case MODEL:
                    cube(g);
                    break;
                case PARAMETER:
                    sliders(g);
                    break;
                case RANGE:
                    range(g, col);
                    break;
                case SLICE:
                    slices(g);
                    break;
                case VIEW:
                    eye(g);
                    break;
                case EXPORT:
                    export(g);
                    break;
                case TABLE:
                    table(g);
                    break;
                case TOOLS:
                    tools(g);
                    break;
                case PAN:
                    pan(g);
                    break;
                case EDIT:
                    edit(g);
                    break;
                case RESET:
                    reset(g);
                    break;
                case REFRESH:
                    refresh(g);
                    break;
                default:
                    break;
            }
        } finally {
            g.dispose();
        }
    }

    private static void cube(Graphics2D g) {
        Path2D p = new Path2D.Double();
        p.moveTo(12, 3);
        p.lineTo(20, 7.5);
        p.lineTo(20, 16.5);
        p.lineTo(12, 21);
        p.lineTo(4, 16.5);
        p.lineTo(4, 7.5);
        p.closePath();
        g.draw(p);
        g.drawLine(4, 7, 12, 12);
        g.drawLine(20, 7, 12, 12);
        g.drawLine(12, 12, 12, 21);
    }

    private static void sliders(Graphics2D g) {
        int[] ys = {6, 12, 18};
        double[] ks = {8, 16, 11};
        for (int i = 0; i < 3; i++) {
            g.drawLine(3, ys[i], 21, ys[i]);
            g.fill(new Ellipse2D.Double(ks[i] - 2.6, ys[i] - 2.6, 5.2, 5.2));
        }
    }

    private static void range(Graphics2D g, Color col) {
        g.setPaint(new GradientPaint(3, 0, new Color(40, 90, 200), 21, 0, new Color(210, 50, 40)));
        g.fillRoundRect(3, 8, 18, 8, 3, 3);
        g.setColor(col);
        g.drawRoundRect(3, 8, 18, 8, 3, 3);
        g.fill(new Polygon(new int[] {3, 7, 5}, new int[] {1, 1, 6}, 3));
        g.fill(new Polygon(new int[] {17, 21, 19}, new int[] {1, 1, 6}, 3));
        g.drawLine(3, 20, 3, 22);
        g.drawLine(21, 20, 21, 22);
        g.drawLine(12, 19, 12, 22);
    }

    private static void slices(Graphics2D g) {
        for (int i = 0; i < 3; i++) {
            int dy = i * 6;
            Path2D p = new Path2D.Double();
            p.moveTo(2, 8 + dy - 3);
            p.lineTo(16, 8 + dy - 3);
            p.lineTo(22, 8 + dy + 1);
            p.lineTo(8, 8 + dy + 1);
            p.closePath();
            if (i == 1) {
                g.fill(p);
            } else {
                g.draw(p);
            }
        }
    }

    private static void eye(Graphics2D g) {
        Path2D p = new Path2D.Double();
        p.moveTo(2, 12);
        p.curveTo(6, 5, 18, 5, 22, 12);
        p.curveTo(18, 19, 6, 19, 2, 12);
        p.closePath();
        g.draw(p);
        g.fill(new Ellipse2D.Double(8.5, 8.5, 7, 7));
    }

    private static void table(Graphics2D g) {
        g.drawRoundRect(3, 4, 18, 16, 3, 3);
        g.fillRect(3, 4, 18, 4);
        g.drawLine(3, 12, 21, 12);
        g.drawLine(3, 16, 21, 16);
        g.drawLine(9, 8, 9, 20);
        g.drawLine(15, 8, 15, 20);
    }

    private static void export(Graphics2D g) {
        g.drawRoundRect(3, 3, 18, 18, 3, 3);
        Path2D m = new Path2D.Double();
        m.moveTo(3, 17);
        m.lineTo(9, 10);
        m.lineTo(13, 14);
        m.lineTo(16, 11);
        m.lineTo(21, 17);
        g.draw(m);
        g.fill(new Ellipse2D.Double(14.5, 5.5, 3.5, 3.5));
    }

    private static void tools(Graphics2D g) {
        g.drawOval(3, 3, 8, 8);
        g.drawLine(10, 10, 20, 20);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(17, 17, 21, 21);
        g.drawLine(16, 20, 20, 16);
    }

    private static void pan(Graphics2D g) {
        g.drawLine(5, 12, 19, 12);
        g.drawLine(12, 5, 12, 19);
        g.fill(new Polygon(new int[] {5, 9, 9}, new int[] {12, 9, 15}, 3));
        g.fill(new Polygon(new int[] {19, 15, 15}, new int[] {12, 9, 15}, 3));
        g.fill(new Polygon(new int[] {12, 9, 15}, new int[] {5, 9, 9}, 3));
        g.fill(new Polygon(new int[] {12, 9, 15}, new int[] {19, 15, 15}, 3));
    }

    private static void edit(Graphics2D g) {
        Path2D p = new Path2D.Double();
        p.moveTo(6, 18);
        p.lineTo(7.5, 13);
        p.lineTo(16, 4.5);
        p.lineTo(19.5, 8);
        p.lineTo(11, 16.5);
        p.closePath();
        g.draw(p);
        g.drawLine(15, 6, 18, 10);
        g.drawLine(6, 18, 5, 20);
    }

    private static void reset(Graphics2D g) {
        g.drawArc(4, 4, 16, 16, 50, 260);
        g.fill(new Polygon(new int[] {18, 22, 16}, new int[] {4, 7, 9}, 3));
    }

    private static void refresh(Graphics2D g) {
        g.drawArc(4, 5, 16, 14, 40, 200);
        g.fill(new Polygon(new int[] {18, 22, 16}, new int[] {5, 8, 10}, 3));
        g.drawArc(4, 5, 16, 14, 220, 200);
        g.fill(new Polygon(new int[] {6, 2, 8}, new int[] {19, 16, 14}, 3));
    }
}
