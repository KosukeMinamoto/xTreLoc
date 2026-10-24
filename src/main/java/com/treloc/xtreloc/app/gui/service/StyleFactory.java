package com.treloc.xtreloc.app.gui.service;

import java.awt.Color;

import org.geotools.api.style.*;
import org.geotools.styling.StyleBuilder;

public class StyleFactory {


    /**
     * Creates a style based on RGB color values.
     * @param size symbol size in pixels (used when coloring by column)
     */
    public static Style createColorStyle(int r, int g, int b, int size) {
        return createColorStyle(r, g, b, 255, size);
    }

    /**
     * Hypocenter point style with optional alpha (0 = fully transparent symbol and outline).
     *
     * @param alpha alpha component 0–255
     * @param size  symbol size in pixels
     */
    public static Style createColorStyle(int r, int g, int b, int alpha, int size) {
        StyleBuilder sb = new StyleBuilder();
        Color color = new Color(r, g, b);
        double op = Math.max(0.0, Math.min(1.0, alpha / 255.0));
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(color));
        fill.setOpacity(sb.literalExpression(op));
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.BLACK));
        stroke.setWidth(sb.literalExpression(1.0));
        stroke.setOpacity(sb.literalExpression(op));
        Mark mark = sb.createMark(StyleBuilder.MARK_CIRCLE, fill, stroke);
        Graphic graphic = sb.createGraphic(null, new Mark[] { mark }, null, 1, size, 0);
        return sb.createStyle(sb.createPointSymbolizer(graphic));
    }

    /**
     * Creates a style based on RGB color values (default size 8).
     */
    public static Style createColorStyle(int r, int g, int b) {
        return createColorStyle(r, g, b, 8);
    }

    public static Style selectedPointStyle() {
        StyleBuilder sb = new StyleBuilder();
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(Color.RED));
        fill.setOpacity(sb.literalExpression(1.0));
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.YELLOW));
        stroke.setWidth(sb.literalExpression(3.0));
        stroke.setOpacity(sb.literalExpression(1.0));
        Mark mark = sb.createMark(StyleBuilder.MARK_TRIANGLE, fill, stroke);
        Graphic g = sb.createGraphic(null, new Mark[] { mark }, null, 1, 15, 180);
        return sb.createStyle(sb.createPointSymbolizer(g));
    }
    
    public static Style selectedSymbolStyle(com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType, Color color) {
        if (color == null) {
            color = Color.RED;
        }
        StyleBuilder sb = new StyleBuilder();
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(color));
        fill.setOpacity(sb.literalExpression(1.0));
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.YELLOW));
        stroke.setWidth(sb.literalExpression(3.0));
        stroke.setOpacity(sb.literalExpression(1.0));
        
        Mark mark;
        switch (symbolType) {
            case CIRCLE:
                mark = sb.createMark(StyleBuilder.MARK_CIRCLE, fill, stroke);
                break;
            case SQUARE:
                mark = sb.createMark(StyleBuilder.MARK_SQUARE, fill, stroke);
                break;
            case TRIANGLE:
                mark = sb.createMark(StyleBuilder.MARK_TRIANGLE, fill, stroke);
                break;
            case DIAMOND:
                mark = sb.createMark(StyleBuilder.MARK_STAR, fill, stroke);
                break;
            case CROSS:
                stroke.setWidth(sb.literalExpression(4.0));
                mark = sb.createMark(StyleBuilder.MARK_CROSS, fill, stroke);
                break;
            case STAR:
                mark = sb.createMark(StyleBuilder.MARK_STAR, fill, stroke);
                break;
            default:
                mark = sb.createMark(StyleBuilder.MARK_CIRCLE, fill, stroke);
        }
        
        Graphic graphic = sb.createGraphic(null, new Mark[] { mark }, null, 1, 20, 0);
        return sb.createStyle(sb.createPointSymbolizer(graphic));
    }
    
    /**
     * Creates a colored station style with inverted triangle and station name label.
     * 
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return the style with colored inverted triangle and station code label
     */
    public static Style createColoredStationStyle(int r, int g, int b) {
        return createColoredStationStyle(r, g, b, 255);
    }

    /**
     * Same as {@link #createColoredStationStyle(int, int, int)} but with symbol/label opacity from alpha (0 = invisible).
     */
    public static Style createColoredStationStyle(int r, int g, int b, int alpha) {
        StyleBuilder sb = new StyleBuilder();
        Color color = new Color(r, g, b);
        double op = Math.max(0.0, Math.min(1.0, alpha / 255.0));
        // Colored filled inverted triangle (180 degree rotation)
        // Create fill with explicit literal expressions
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(color));
        fill.setOpacity(sb.literalExpression(op));
        // Create stroke with explicit literal expressions
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.BLACK));
        stroke.setWidth(sb.literalExpression(1.0));
        stroke.setOpacity(sb.literalExpression(op));
        Mark mark = sb.createMark(StyleBuilder.MARK_TRIANGLE, fill, stroke);
        // Rotate 180 degrees to make it inverted
        Graphic graphic = sb.createGraphic(null, new Mark[] { mark }, null, 1, 10, 180);

        // Add station code label
        TextSymbolizer text = sb.createTextSymbolizer();
        text.setLabel(sb.attributeExpression("code"));
        text.setFont(sb.createFont("SansSerif", 10));
        // Create text fill with explicit literal expressions
        org.geotools.api.style.Fill textFill = sb.createFill();
        textFill.setColor(sb.literalExpression(Color.BLACK));
        textFill.setOpacity(sb.literalExpression(op));
        text.setFill(textFill);
        text.setHalo(sb.createHalo(Color.WHITE, 2));
        // Position label above the triangle
        org.geotools.api.style.PointPlacement placement = sb.createPointPlacement();
        placement.setAnchorPoint(sb.createAnchorPoint(0.5, 0.0));
        placement.setDisplacement(sb.createDisplacement(0, -15));
        placement.setRotation(sb.literalExpression(0));
        text.setLabelPlacement(placement);

        Rule rule = sb.createRule(sb.createPointSymbolizer(graphic));
        rule.symbolizers().add(text);

        FeatureTypeStyle fts = sb.createFeatureTypeStyle(rule.symbolizers().get(0));
        fts.rules().add(rule);

        Style style = sb.createStyle();
        style.featureTypeStyles().add(fts);
        return style;
    }
    
    public static Style createGridStyle() {
        StyleBuilder sb = new StyleBuilder();
        org.geotools.api.style.Stroke stroke = sb.createStroke(
            Color.GRAY, 0.5f);
        return sb.createStyle(sb.createLineSymbolizer(stroke));
    }
    
    public static Style createGraticuleStyle() {
        StyleBuilder sb = new StyleBuilder();
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(new Color(200, 200, 200, 150)));
        stroke.setWidth(sb.literalExpression(0.8));
        stroke.setOpacity(sb.literalExpression(0.6));
        return sb.createStyle(sb.createLineSymbolizer(stroke));
    }
    
    /**
     * Error ellipse outline: {@link LineSymbolizer} only (features use the exterior ring as
     * {@code LineString}). Avoids {@link PolygonSymbolizer} entirely so GeoTools never builds a
     * polygon fill {@code Area} (which can NPE with {@code fillSpansLoop} / null {@code loops} on
     * some JDK / geometry combinations).
     */
    public static Style createErrorBarStyle() {
        StyleBuilder sb = new StyleBuilder();
        org.geotools.api.style.Stroke stroke = sb.createStroke(Color.BLACK, 1.0f);
        return sb.createStyle(sb.createLineSymbolizer(stroke));
    }
    
    /**
     * Creates a simple style for shapefiles using only literal values.
     * This avoids function evaluation issues that can cause "Unable to find function" errors.
     * Uses the same pattern as createGridStyle() which works correctly.
     */
    public static Style createShapefileStyle() {
        StyleBuilder sb = new StyleBuilder();
        // Use the same pattern as createGridStyle() which works - pass color and width directly
        org.geotools.api.style.Stroke stroke = sb.createStroke(Color.BLUE, 2.0f);
        return sb.createStyle(sb.createLineSymbolizer(stroke));
    }
    
    /**
     * Creates a style based on symbol type and color.
     */
    public static Style createSymbolStyle(com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType, Color color, int size) {
        return createSymbolStyle(symbolType, color, size, true);
    }

    public static Style createSymbolStyle(com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType, Color color,
            int size, boolean filled) {
        if (color == null) {
            color = new Color(96, 96, 96);
        }
        StyleBuilder sb = new StyleBuilder();
        double op = Math.max(0.0, Math.min(1.0, color.getAlpha() / 255.0));
        Color rgb = new Color(color.getRed(), color.getGreen(), color.getBlue());
        org.geotools.api.filter.expression.Expression fillOp =
            sb.literalExpression(filled ? op : 0.0);
        return buildSymbolStyle(sb, symbolType, size, sb.literalExpression(rgb), fillOp, sb.literalExpression(rgb),
            sb.literalExpression(op));
    }

    /** Feature attribute holding the fill color as {@code #RRGGBB} for {@link #createSymbolStyleByAttribute}. */
    public static final String COLOR_ATTRIBUTE = "color";
    /** Feature attribute holding the opacity (0-1) for {@link #createSymbolStyleByAttribute}. */
    public static final String OPACITY_ATTRIBUTE = "opacity";

    /**
     * Creates a single style whose fill color and opacity are read per feature from the
     * {@link #COLOR_ATTRIBUTE} (String, {@code #RRGGBB}) and {@link #OPACITY_ATTRIBUTE} (Double) attributes.
     * This lets a color-mapped catalog be drawn in <em>one</em> layer instead of one layer per distinct color,
     * which keeps GeoTools from re-rendering the map once per added layer.
     */
    public static Style createSymbolStyleByAttribute(com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType, int size) {
        StyleBuilder sb = new StyleBuilder();
        org.geotools.api.filter.expression.Expression color = sb.attributeExpression(COLOR_ATTRIBUTE);
        org.geotools.api.filter.expression.Expression opacity = sb.attributeExpression(OPACITY_ATTRIBUTE);
        return buildSymbolStyle(sb, symbolType, size, color, opacity, sb.literalExpression(Color.BLACK), opacity);
    }

    private static Style buildSymbolStyle(StyleBuilder sb,
            com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType, int size,
            org.geotools.api.filter.expression.Expression fillColor,
            org.geotools.api.filter.expression.Expression fillOpacity,
            org.geotools.api.filter.expression.Expression strokeColor,
            org.geotools.api.filter.expression.Expression strokeOpacity) {
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(fillColor);
        fill.setOpacity(fillOpacity);
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(strokeColor);
        stroke.setOpacity(strokeOpacity);
        
        Mark mark;
        switch (symbolType) {
            case CIRCLE:
                stroke.setWidth(sb.literalExpression(1.0));
                mark = sb.createMark(StyleBuilder.MARK_CIRCLE, fill, stroke);
                break;
            case SQUARE:
                stroke.setWidth(sb.literalExpression(1.0));
                mark = sb.createMark(StyleBuilder.MARK_SQUARE, fill, stroke);
                break;
            case TRIANGLE:
                stroke.setWidth(sb.literalExpression(1.0));
                mark = sb.createMark(StyleBuilder.MARK_TRIANGLE, fill, stroke);
                break;
            case DIAMOND:
                stroke.setWidth(sb.literalExpression(1.0));
                mark = sb.createMark(StyleBuilder.MARK_STAR, fill, stroke);
                break;
            case CROSS:
                stroke.setWidth(sb.literalExpression(2.0));
                mark = sb.createMark(StyleBuilder.MARK_CROSS, fill, stroke);
                break;
            case STAR:
                stroke.setWidth(sb.literalExpression(1.0));
                mark = sb.createMark(StyleBuilder.MARK_STAR, fill, stroke);
                break;
            default:
                stroke.setWidth(sb.literalExpression(1.0));
                mark = sb.createMark(StyleBuilder.MARK_CIRCLE, fill, stroke);
        }
        
        Graphic graphic = sb.createGraphic(null, new Mark[] { mark }, null, 1, size, 0);
        return sb.createStyle(sb.createPointSymbolizer(graphic));
    }
    
    public static Style createLineStyle(com.treloc.xtreloc.app.gui.model.MapLayerStyle style) {
        if (style == null) {
            return createShapefileStyle();
        }
        return createLineStyle(style.getColor(), style.getSize(), style.getOpacity(), style.dashArray());
    }

    public static Style createLineStyle(Color color, float width, float opacity, float[] dash) {
        StyleBuilder sb = new StyleBuilder();
        Color rgb = color != null ? new Color(color.getRed(), color.getGreen(), color.getBlue()) : Color.BLUE;
        double op = Math.max(0.0, Math.min(1.0, opacity));
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(rgb));
        stroke.setWidth(sb.literalExpression((double) Math.max(0.5f, width)));
        stroke.setOpacity(sb.literalExpression(op));
        if (dash != null && dash.length > 0) {
            try {
                stroke.setDashArray(dash);
            } catch (RuntimeException ignored) {
                // some GeoTools Stroke implementations reject dash arrays
            }
        }
        return sb.createStyle(sb.createLineSymbolizer(stroke));
    }

    public static Style createStationStyle(com.treloc.xtreloc.app.gui.model.MapLayerStyle style) {
        return createStationStyle(style, true);
    }

    public static Style createStationStyle(com.treloc.xtreloc.app.gui.model.MapLayerStyle style, boolean filled) {
        if (style == null) {
            return createSymbolStyle(com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType.TRIANGLE,
                Color.BLACK, 10, filled);
        }
        return createSymbolStyle(style.getSymbol(), style.colorWithOpacity(), Math.round(style.getSize()), filled);
    }
}
