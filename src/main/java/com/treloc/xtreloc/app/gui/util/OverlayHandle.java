package com.treloc.xtreloc.app.gui.util;

/**
 * Screen position of a map overlay (legend, colour bar, scale, compass). Defaults to a corner;
 * the user can drag it. Coordinates are the top-left of the last laid-out box.
 */
public final class OverlayHandle {

    public enum Corner { NW, NE, SW, SE }

    private final Corner home;
    private final int margin;
    private Integer userX;
    private Integer userY;
    private int originX;
    private int originY;
    private int areaW = 1;
    private int areaH = 1;
    private int x;
    private int y;
    private int w;
    private int h;
    private boolean laidOut;

    public OverlayHandle(Corner home, int margin) {
        this.home = home;
        this.margin = Math.max(0, margin);
    }

    public int[] place(int canvasW, int canvasH, int boxW, int boxH) {
        return placeIn(0, 0, canvasW, canvasH, boxW, boxH);
    }

    /**
     * Places the overlay inside {@code (originX, originY, areaW, areaH)} so it stays inside a
     * map frame rather than the full canvas.
     */
    public int[] placeIn(int originX, int originY, int areaW, int areaH, int boxW, int boxH) {
        this.originX = originX;
        this.originY = originY;
        this.areaW = Math.max(1, areaW);
        this.areaH = Math.max(1, areaH);
        w = Math.max(1, boxW);
        h = Math.max(1, boxH);
        int minX = originX + margin;
        int minY = originY + margin;
        int maxX = Math.max(minX, originX + this.areaW - w - margin);
        int maxY = Math.max(minY, originY + this.areaH - h - margin);
        if (userX != null && userY != null) {
            x = clamp(userX, minX, maxX);
            y = clamp(userY, minY, maxY);
        } else {
            switch (home) {
                case NE:
                    x = maxX;
                    y = minY;
                    break;
                case SW:
                    x = minX;
                    y = maxY;
                    break;
                case SE:
                    x = maxX;
                    y = maxY;
                    break;
                case NW:
                default:
                    x = minX;
                    y = minY;
                    break;
            }
        }
        laidOut = true;
        return new int[] {x, y};
    }

    public boolean contains(int px, int py) {
        if (!laidOut) {
            return false;
        }
        int pad = 3;
        return px >= x - pad && py >= y - pad && px <= x + w + pad && py <= y + h + pad;
    }

    public void moveTo(int topLeftX, int topLeftY, int canvasW, int canvasH) {
        int minX = originX + margin;
        int minY = originY + margin;
        int maxX = Math.max(minX, originX + Math.max(1, areaW) - Math.max(1, w) - margin);
        int maxY = Math.max(minY, originY + Math.max(1, areaH) - Math.max(1, h) - margin);
        if (areaW <= 1 && areaH <= 1) {
            maxX = Math.max(margin, canvasW - Math.max(1, w) - margin);
            maxY = Math.max(margin, canvasH - Math.max(1, h) - margin);
            minX = margin;
            minY = margin;
        }
        userX = clamp(topLeftX, minX, maxX);
        userY = clamp(topLeftY, minY, maxY);
        x = userX;
        y = userY;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    public boolean isLaidOut() {
        return laidOut;
    }

    /** True after the user has dragged this overlay away from its default corner. */
    public boolean hasUserPosition() {
        return userX != null;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
