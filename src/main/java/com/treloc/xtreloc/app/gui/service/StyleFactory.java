package com.treloc.xtreloc.app.gui.service;

import java.awt.Color;
import org.geotools.api.style.*;
import org.geotools.styling.StyleBuilder;

public class StyleFactory {

    public static Style hypocenterStyle() {
        StyleBuilder sb = new StyleBuilder();
        // Create fill with explicit literal expressions
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(Color.BLUE));
        fill.setOpacity(sb.literalExpression(1.0));
        // Create stroke with explicit literal expressions
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.BLACK));
        stroke.setWidth(sb.literalExpression(1.0));
        stroke.setOpacity(sb.literalExpression(1.0));
        Mark mark = sb.createMark(StyleBuilder.MARK_CIRCLE, fill, stroke);
        Graphic g = sb.createGraphic(null, new Mark[] { mark }, null, 1, 8, 0);
        return sb.createStyle(sb.createPointSymbolizer(g));
    }
    
    /**
     * Creates a hypocenter style for color mapping.
     * The actual color is set per feature in MapView, so this returns a default style.
     */
    public static Style hypocenterColorStyle() {
        StyleBuilder sb = new StyleBuilder();
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(Color.BLUE));
        fill.setOpacity(sb.literalExpression(1.0));
        // Create stroke with explicit literal expressions
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.BLACK));
        stroke.setWidth(sb.literalExpression(1.0));
        stroke.setOpacity(sb.literalExpression(1.0));
        Mark mark = sb.createMark(StyleBuilder.MARK_CIRCLE, fill, stroke);
        Graphic g = sb.createGraphic(null, new Mark[] { mark }, null, 1, 8, 0);
        
        return sb.createStyle(sb.createPointSymbolizer(g));
    }
    
    /**
     * Creates a style based on RGB color values.
     */
    public static Style createColorStyle(int r, int g, int b) {
        StyleBuilder sb = new StyleBuilder();
        Color color = new Color(r, g, b);
        // Create fill with explicit literal expressions
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(color));
        fill.setOpacity(sb.literalExpression(1.0));
        // Create stroke with explicit literal expressions
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.BLACK));
        stroke.setWidth(sb.literalExpression(1.0));
        stroke.setOpacity(sb.literalExpression(1.0));
        Mark mark = sb.createMark(StyleBuilder.MARK_CIRCLE, fill, stroke);
        Graphic graphic = sb.createGraphic(null, new Mark[] { mark }, null, 1, 8, 0);
        return sb.createStyle(sb.createPointSymbolizer(graphic));
    }

    public static Style stationStyle() {
        StyleBuilder sb = new StyleBuilder();
        // Black filled inverted triangle (180 degree rotation)
        // Create fill with explicit literal expressions
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(Color.BLACK)); // Filled black
        fill.setOpacity(sb.literalExpression(1.0));
        // Create stroke with explicit literal expressions
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.BLACK));
        stroke.setWidth(sb.literalExpression(1.0));
        stroke.setOpacity(sb.literalExpression(1.0));
        Mark mark = sb.createMark(StyleBuilder.MARK_TRIANGLE, fill, stroke);
        // Rotate 180 degrees to make it inverted
        Graphic g = sb.createGraphic(null, new Mark[] { mark }, null, 1, 10, 180);

        TextSymbolizer text = sb.createTextSymbolizer();
        text.setLabel(sb.attributeExpression("code"));
        text.setFont(sb.createFont("SansSerif", 10));
        // Create text fill with explicit literal expressions
        org.geotools.api.style.Fill textFill = sb.createFill();
        textFill.setColor(sb.literalExpression(Color.BLACK));
        textFill.setOpacity(sb.literalExpression(1.0));
        text.setFill(textFill);
        text.setHalo(sb.createHalo(Color.WHITE, 2)); // Larger halo for better visibility
        // Position label above the triangle (offset upward)
        // Create point placement - anchor at bottom center, displace upward
        org.geotools.api.style.PointPlacement placement = sb.createPointPlacement();
        placement.setAnchorPoint(sb.createAnchorPoint(0.5, 0.0)); // Center horizontally, at bottom of point
        placement.setDisplacement(sb.createDisplacement(0, -15)); // Move 15 pixels upward
        placement.setRotation(sb.literalExpression(0)); // No rotation
        text.setLabelPlacement(placement);

        Rule r = sb.createRule(sb.createPointSymbolizer(g));
        r.symbolizers().add(text);

        FeatureTypeStyle fts = sb.createFeatureTypeStyle(r.symbolizers().get(0));
        fts.rules().add(r);

        Style s = sb.createStyle();
        s.featureTypeStyles().add(fts);
        return s;
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
        StyleBuilder sb = new StyleBuilder();
        Color color = new Color(r, g, b);
        // Colored filled inverted triangle (180 degree rotation)
        // Create fill with explicit literal expressions
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(color));
        fill.setOpacity(sb.literalExpression(1.0));
        // Create stroke with explicit literal expressions
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.BLACK));
        stroke.setWidth(sb.literalExpression(1.0));
        stroke.setOpacity(sb.literalExpression(1.0));
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
        textFill.setOpacity(sb.literalExpression(1.0));
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
    
    /**
     * Error bar style (black outline only, no fill).
     */
    public static Style createErrorBarStyle() {
        StyleBuilder sb = new StyleBuilder();
        // Black stroke only (no fill)
        org.geotools.api.style.Stroke stroke = sb.createStroke(
            Color.BLACK, // Black outline
            1.0f);
        // Create polygon symbolizer with stroke only, no fill
        // Use Fill with opacity 0 to completely disable filling
        org.geotools.api.style.Fill noFill = sb.createFill();
        noFill.setOpacity(sb.literalExpression(0.0)); // Completely transparent (no fill)
        org.geotools.api.style.PolygonSymbolizer polygonSymbolizer = sb.createPolygonSymbolizer(stroke, noFill);
        return sb.createStyle(polygonSymbolizer);
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
     * Creates a style for shapefiles based on geometry type.
     * This method determines the geometry type and creates an appropriate style.
     * 
     * @param featureSource the feature source to determine geometry type from
     * @return a style appropriate for the geometry type
     */
    public static Style createShapefileStyleForGeometry(org.geotools.api.data.FeatureSource featureSource) {
        try {
            org.geotools.api.feature.type.FeatureType featureType = featureSource.getSchema();
            org.geotools.api.feature.type.GeometryDescriptor geomDesc = featureType.getGeometryDescriptor();
            if (geomDesc != null) {
                Class<?> geomClass = geomDesc.getType().getBinding();
                
                System.out.println("[StyleFactory] Geometry type detected: " + geomClass.getName());
                
                if (org.locationtech.jts.geom.Point.class.isAssignableFrom(geomClass) ||
                    org.locationtech.jts.geom.MultiPoint.class.isAssignableFrom(geomClass)) {
                    // Point geometry - use point symbolizer with explicit literal expressions
                    System.out.println("[StyleFactory] Creating point style");
                    StyleBuilder sb = new StyleBuilder();
                    
                    // Create fill with explicit literal expressions
                    org.geotools.api.style.Fill fill = sb.createFill();
                    fill.setColor(sb.literalExpression(Color.BLUE));
                    fill.setOpacity(sb.literalExpression(1.0));
                    
                    // Create stroke with explicit literal expressions
                    org.geotools.api.style.Stroke stroke = sb.createStroke();
                    stroke.setColor(sb.literalExpression(Color.BLUE));
                    stroke.setWidth(sb.literalExpression(1.0));
                    stroke.setOpacity(sb.literalExpression(1.0));
                    
                    Mark mark = sb.createMark(StyleBuilder.MARK_CIRCLE, fill, stroke);
                    // Use the same pattern as other working methods - StyleBuilder handles conversion
                    Graphic graphic = sb.createGraphic(null, new Mark[] { mark }, null, 1, 8, 0);
                    Style style = sb.createStyle(sb.createPointSymbolizer(graphic));
                    System.out.println("[StyleFactory] Point style created successfully");
                    return style;
                } else if (org.locationtech.jts.geom.Polygon.class.isAssignableFrom(geomClass) ||
                          org.locationtech.jts.geom.MultiPolygon.class.isAssignableFrom(geomClass)) {
                    // Polygon geometry - use polygon symbolizer with explicit literal expressions
                    System.out.println("[StyleFactory] Creating polygon style");
                    StyleBuilder sb = new StyleBuilder();
                    
                    // Create fill with explicit literal expressions
                    org.geotools.api.style.Fill fill = sb.createFill();
                    fill.setColor(sb.literalExpression(Color.BLUE));
                    fill.setOpacity(sb.literalExpression(0.3));
                    
                    // Create stroke with explicit literal expressions
                    org.geotools.api.style.Stroke stroke = sb.createStroke();
                    stroke.setColor(sb.literalExpression(Color.BLUE));
                    stroke.setWidth(sb.literalExpression(2.0));
                    stroke.setOpacity(sb.literalExpression(1.0));
                    
                    Style style = sb.createStyle(sb.createPolygonSymbolizer(stroke, fill));
                    System.out.println("[StyleFactory] Polygon style created successfully");
                    return style;
                } else {
                    System.out.println("[StyleFactory] Geometry type not Point or Polygon, using line style");
                }
            } else {
                System.out.println("[StyleFactory] No geometry descriptor found, using line style");
            }
        } catch (Exception e) {
            // If we can't determine geometry type, log error and fall back to simple line style
            System.err.println("[StyleFactory] Error determining geometry type: " + e.getMessage());
            e.printStackTrace(System.err);
            java.util.logging.Logger.getLogger(StyleFactory.class.getName())
                .warning("Error determining geometry type: " + e.getMessage());
        }
        
        // Default: line style (works for LineString, and GeoTools handles others)
        System.out.println("[StyleFactory] Creating default line style");
        Style style = createShapefileStyle();
        System.out.println("[StyleFactory] Default line style created successfully");
        return style;
    }
    
    /**
     * Creates a style based on symbol type and color.
     */
    public static Style createSymbolStyle(com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType, Color color, int size) {
        StyleBuilder sb = new StyleBuilder();
        // Create fill with explicit literal expressions
        org.geotools.api.style.Fill fill = sb.createFill();
        fill.setColor(sb.literalExpression(color));
        fill.setOpacity(sb.literalExpression(1.0));
        // Create stroke with explicit literal expressions
        org.geotools.api.style.Stroke stroke = sb.createStroke();
        stroke.setColor(sb.literalExpression(Color.BLACK));
        stroke.setOpacity(sb.literalExpression(1.0));
        
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
    
    /**
     * Creates a style for connection lines.
     */
    public static Style createConnectionLineStyle(Color color, float width) {
        StyleBuilder sb = new StyleBuilder();
        org.geotools.api.style.Stroke stroke = sb.createStroke(color, width);
        return sb.createStyle(sb.createLineSymbolizer(stroke));
    }
}
