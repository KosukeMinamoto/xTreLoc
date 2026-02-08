package com.treloc.xtreloc.app.gui.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;

/**
 * Settings for JFreeChart appearance.
 * Used to maintain consistent appearance across all charts (residual plots, k-distance plots, etc.).
 * 
 * @author xTreLoc Development Team
 */
public class ChartAppearanceSettings {
    // Title font
    private String titleFontName = "SansSerif";
    private int titleFontSize = 14;
    private int titleFontStyle = Font.BOLD;
    
    // Axis label font
    private String axisLabelFontName = "SansSerif";
    private int axisLabelFontSize = 12;
    private int axisLabelFontStyle = Font.PLAIN;
    
    // Tick label font
    private String tickLabelFontName = "SansSerif";
    private int tickLabelFontSize = 10;
    private int tickLabelFontStyle = Font.PLAIN;
    
    // Legend font
    private String legendFontName = "SansSerif";
    private int legendFontSize = 11;
    private int legendFontStyle = Font.PLAIN;
    
    // Colors
    private String backgroundColor = "#FFFFFF"; // White
    private String gridlineColor = "#E0E0E0"; // Light gray
    private String axisLineColor = "#000000"; // Black
    
    // Line settings
    private float lineWidth = 2.0f;
    private float gridlineWidth = 1.0f;
    private String gridlineStyle = "solid"; // "solid", "dash", "dot"
    
    // Default constructor
    public ChartAppearanceSettings() {
    }
    
    // Getters and setters
    public String getTitleFontName() {
        return titleFontName;
    }
    
    public void setTitleFontName(String titleFontName) {
        this.titleFontName = titleFontName;
    }
    
    public int getTitleFontSize() {
        return titleFontSize;
    }
    
    public void setTitleFontSize(int titleFontSize) {
        this.titleFontSize = titleFontSize;
    }
    
    public int getTitleFontStyle() {
        return titleFontStyle;
    }
    
    public void setTitleFontStyle(int titleFontStyle) {
        this.titleFontStyle = titleFontStyle;
    }
    
    public Font getTitleFont() {
        return new Font(titleFontName, titleFontStyle, titleFontSize);
    }
    
    public String getAxisLabelFontName() {
        return axisLabelFontName;
    }
    
    public void setAxisLabelFontName(String axisLabelFontName) {
        this.axisLabelFontName = axisLabelFontName;
    }
    
    public int getAxisLabelFontSize() {
        return axisLabelFontSize;
    }
    
    public void setAxisLabelFontSize(int axisLabelFontSize) {
        this.axisLabelFontSize = axisLabelFontSize;
    }
    
    public int getAxisLabelFontStyle() {
        return axisLabelFontStyle;
    }
    
    public void setAxisLabelFontStyle(int axisLabelFontStyle) {
        this.axisLabelFontStyle = axisLabelFontStyle;
    }
    
    public Font getAxisLabelFont() {
        return new Font(axisLabelFontName, axisLabelFontStyle, axisLabelFontSize);
    }
    
    public String getTickLabelFontName() {
        return tickLabelFontName;
    }
    
    public void setTickLabelFontName(String tickLabelFontName) {
        this.tickLabelFontName = tickLabelFontName;
    }
    
    public int getTickLabelFontSize() {
        return tickLabelFontSize;
    }
    
    public void setTickLabelFontSize(int tickLabelFontSize) {
        this.tickLabelFontSize = tickLabelFontSize;
    }
    
    public int getTickLabelFontStyle() {
        return tickLabelFontStyle;
    }
    
    public void setTickLabelFontStyle(int tickLabelFontStyle) {
        this.tickLabelFontStyle = tickLabelFontStyle;
    }
    
    public Font getTickLabelFont() {
        return new Font(tickLabelFontName, tickLabelFontStyle, tickLabelFontSize);
    }
    
    public String getLegendFontName() {
        return legendFontName;
    }
    
    public void setLegendFontName(String legendFontName) {
        this.legendFontName = legendFontName;
    }
    
    public int getLegendFontSize() {
        return legendFontSize;
    }
    
    public void setLegendFontSize(int legendFontSize) {
        this.legendFontSize = legendFontSize;
    }
    
    public int getLegendFontStyle() {
        return legendFontStyle;
    }
    
    public void setLegendFontStyle(int legendFontStyle) {
        this.legendFontStyle = legendFontStyle;
    }
    
    public Font getLegendFont() {
        return new Font(legendFontName, legendFontStyle, legendFontSize);
    }
    
    public String getBackgroundColor() {
        return backgroundColor;
    }
    
    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
    
    public Color getBackgroundColorAsColor() {
        return Color.decode(backgroundColor);
    }
    
    public String getGridlineColor() {
        return gridlineColor;
    }
    
    public void setGridlineColor(String gridlineColor) {
        this.gridlineColor = gridlineColor;
    }
    
    public Color getGridlineColorAsColor() {
        return Color.decode(gridlineColor);
    }
    
    public String getAxisLineColor() {
        return axisLineColor;
    }
    
    public void setAxisLineColor(String axisLineColor) {
        this.axisLineColor = axisLineColor;
    }
    
    public Color getAxisLineColorAsColor() {
        return Color.decode(axisLineColor);
    }
    
    public float getLineWidth() {
        return lineWidth;
    }
    
    public void setLineWidth(float lineWidth) {
        this.lineWidth = lineWidth;
    }
    
    public float getGridlineWidth() {
        return gridlineWidth;
    }
    
    public void setGridlineWidth(float gridlineWidth) {
        this.gridlineWidth = gridlineWidth;
    }
    
    public String getGridlineStyle() {
        return gridlineStyle;
    }
    
    public void setGridlineStyle(String gridlineStyle) {
        this.gridlineStyle = gridlineStyle != null ? gridlineStyle : "solid";
    }
    
    /**
     * Returns the stroke for grid lines based on width and style.
     * Used by charts (e.g. convergence log, k-distance) and custom-drawn plots.
     */
    public Stroke getGridlineStroke() {
        float w = gridlineWidth;
        String s = gridlineStyle != null ? gridlineStyle : "solid";
        switch (s.toLowerCase()) {
            case "dash":
                return new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 4f}, 0f);
            case "dot":
                return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 4f}, 0f);
            default:
                return new BasicStroke(w);
        }
    }
}
