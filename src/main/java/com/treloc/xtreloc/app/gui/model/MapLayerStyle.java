package com.treloc.xtreloc.app.gui.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.Objects;

/**
 * Appearance of one map object: scatter (catalog / station) or line (shapefile / network).
 */
public final class MapLayerStyle {

    public enum Kind {
        SCATTER,
        LINE
    }

    public enum LineStyle {
        SOLID("Solid"),
        DASH("Dash"),
        DOT("Dot"),
        DASH_DOT("Dash-dot");

        private final String label;

        LineStyle(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private Kind kind;
    private Color color;
    private CatalogInfo.SymbolType symbol;
    private LineStyle lineStyle;
    private float size;
    private float opacity;

    public static MapLayerStyle scatter(Color color, CatalogInfo.SymbolType symbol, float size, float opacity) {
        MapLayerStyle s = new MapLayerStyle();
        s.kind = Kind.SCATTER;
        s.color = color != null ? color : Color.BLACK;
        s.symbol = symbol != null ? symbol : CatalogInfo.SymbolType.CIRCLE;
        s.lineStyle = LineStyle.SOLID;
        s.size = clampSize(size);
        s.opacity = clampOpacity(opacity);
        return s;
    }

    public static MapLayerStyle line(Color color, LineStyle lineStyle, float width, float opacity) {
        MapLayerStyle s = new MapLayerStyle();
        s.kind = Kind.LINE;
        s.color = color != null ? color : new Color(0, 90, 180);
        s.symbol = CatalogInfo.SymbolType.CIRCLE;
        s.lineStyle = lineStyle != null ? lineStyle : LineStyle.SOLID;
        s.size = clampSize(width);
        s.opacity = clampOpacity(opacity);
        return s;
    }

    public static MapLayerStyle defaultStation() {
        return scatter(Color.BLACK, CatalogInfo.SymbolType.TRIANGLE, 10f, 1f);
    }

    public static MapLayerStyle defaultShapefile() {
        return line(new Color(0, 90, 180), LineStyle.SOLID, 1.5f, 1f);
    }

    public static MapLayerStyle defaultNetwork() {
        return line(new Color(80, 80, 90), LineStyle.SOLID, 1.0f, 0.55f);
    }

    public static MapLayerStyle defaultGrid() {
        return line(new Color(70, 75, 90), LineStyle.SOLID, 1.0f, 0.28f);
    }

    public MapLayerStyle copy() {
        return kind == Kind.LINE
            ? line(color, lineStyle, size, opacity)
            : scatter(color, symbol, size, opacity);
    }

    public Kind getKind() {
        return kind;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color != null ? color : Color.BLACK;
    }

    public CatalogInfo.SymbolType getSymbol() {
        return symbol;
    }

    public void setSymbol(CatalogInfo.SymbolType symbol) {
        this.symbol = symbol != null ? symbol : CatalogInfo.SymbolType.CIRCLE;
    }

    public LineStyle getLineStyle() {
        return lineStyle;
    }

    public void setLineStyle(LineStyle lineStyle) {
        this.lineStyle = lineStyle != null ? lineStyle : LineStyle.SOLID;
    }

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = clampSize(size);
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = clampOpacity(opacity);
    }

    public int alpha() {
        return Math.round(opacity * 255f);
    }

    public Color colorWithOpacity() {
        Color c = color != null ? color : Color.BLACK;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha());
    }

    public float[] dashArray() {
        if (lineStyle == LineStyle.DASH) {
            return new float[] {7f, 4f};
        }
        if (lineStyle == LineStyle.DOT) {
            return new float[] {1.5f, 3.5f};
        }
        if (lineStyle == LineStyle.DASH_DOT) {
            return new float[] {7f, 3.5f, 1.5f, 3.5f};
        }
        return null;
    }

    public BasicStroke stroke() {
        float w = Math.max(0.5f, size);
        float[] dash = dashArray();
        if (dash == null) {
            return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
        return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MapLayerStyle)) {
            return false;
        }
        MapLayerStyle other = (MapLayerStyle) o;
        return kind == other.kind
            && Objects.equals(color, other.color)
            && symbol == other.symbol
            && lineStyle == other.lineStyle
            && Float.compare(size, other.size) == 0
            && Float.compare(opacity, other.opacity) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, color, symbol, lineStyle, size, opacity);
    }

    private static float clampSize(float size) {
        return Math.max(0.5f, Math.min(50f, size));
    }

    private static float clampOpacity(float opacity) {
        if (!Float.isFinite(opacity)) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, opacity));
    }
}
