package com.treloc.xtreloc.app.gui.service;

import java.awt.Color;
import org.geotools.api.style.*;
import org.geotools.styling.StyleBuilder;

public class StyleFactory {

    public static Style hypocenterStyle() {
        StyleBuilder sb = new StyleBuilder();
        Mark mark = sb.createMark(StyleBuilder.MARK_CIRCLE,
                sb.createFill(Color.BLUE),
                sb.createStroke(Color.BLACK, 1));
        Graphic g = sb.createGraphic(null, new Mark[] { mark }, null, 1, 8, 0);
        return sb.createStyle(sb.createPointSymbolizer(g));
    }
    
    /**
     * 数値に基づいて色付けする震源スタイル
     * 実際の色はMapViewで各Featureごとに設定されるため、
     * ここではデフォルトのスタイルを返す
     */
    public static Style hypocenterColorStyle() {
        StyleBuilder sb = new StyleBuilder();
        // デフォルトのマークを作成（実際の色はMapViewで各レイヤーごとに設定される）
        Mark mark = sb.createMark(StyleBuilder.MARK_CIRCLE,
                sb.createFill(Color.BLUE), // デフォルト色
                sb.createStroke(Color.BLACK, 1));
        Graphic g = sb.createGraphic(null, new Mark[] { mark }, null, 1, 8, 0);
        
        return sb.createStyle(sb.createPointSymbolizer(g));
    }
    
    /**
     * 色情報（R, G, B）に基づいてスタイルを作成
     */
    public static Style createColorStyle(int r, int g, int b) {
        StyleBuilder sb = new StyleBuilder();
        Color color = new Color(r, g, b);
        Mark mark = sb.createMark(StyleBuilder.MARK_CIRCLE,
                sb.createFill(color),
                sb.createStroke(Color.BLACK, 1));
        Graphic graphic = sb.createGraphic(null, new Mark[] { mark }, null, 1, 8, 0);
        return sb.createStyle(sb.createPointSymbolizer(graphic));
    }

    public static Style stationStyle() {
        StyleBuilder sb = new StyleBuilder();
        // Black filled inverted triangle (180 degree rotation)
        Mark mark = sb.createMark(StyleBuilder.MARK_TRIANGLE,
                sb.createFill(Color.BLACK), // Filled black
                sb.createStroke(Color.BLACK, 1));
        // Rotate 180 degrees to make it inverted
        Graphic g = sb.createGraphic(null, new Mark[] { mark }, null, 1, 10, 180);

        TextSymbolizer text = sb.createTextSymbolizer();
        text.setLabel(sb.attributeExpression("code"));
        text.setFont(sb.createFont("SansSerif", 10));
        text.setFill(sb.createFill(Color.BLACK));
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
    
    /**
     * 選択されたポイントを強調表示するスタイル
     */
    public static Style selectedPointStyle() {
        StyleBuilder sb = new StyleBuilder();
        // 大きな赤い円で強調表示
        Mark mark = sb.createMark(StyleBuilder.MARK_CIRCLE,
                sb.createFill(Color.RED),
                sb.createStroke(Color.YELLOW, 3));
        Graphic g = sb.createGraphic(null, new Mark[] { mark }, null, 1, 15, 0);
        return sb.createStyle(sb.createPointSymbolizer(g));
    }
    
    public static Style createGridStyle() {
        StyleBuilder sb = new StyleBuilder();
        // グリッド線用のストローク（グレー、細い線、点線パターン）
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
     * Shapefile style with thicker line width.
     */
    public static Style createShapefileStyle() {
        StyleBuilder sb = new StyleBuilder();
        // Thicker stroke for shapefile lines (2.0f instead of default 1.0f)
        org.geotools.api.style.Stroke stroke = sb.createStroke(
            Color.BLUE, // Default blue color
            2.0f); // Thicker line
        return sb.createStyle(sb.createLineSymbolizer(stroke));
    }
}
