package com.treloc.hypotd;

import org.geotools.styling.StyleBuilder;
import org.geotools.api.style.Symbolizer;
import java.awt.Color;
import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

public class EarthquakeStyler {
    private final String propertiesFile = "map.properties";
    private final StyleBuilder styleBuilder;
    private final Properties config;
    private static final Logger LOGGER = Logger.getLogger(EarthquakeStyler.class.getName());

    private enum ColorMode {
        DISCRETE,
        GRADUAL,
        SINGLE
    }

    private final ColorMode colorMode;
    private final Color baseColor;
    private final Color[] depthColors;
    private final double[] depthRanges;

    public EarthquakeStyler() throws IOException {
        this.styleBuilder = new StyleBuilder();
        this.config = loadConfig();
        String colorModeStr = config.getProperty("color.mode", "DISCRETE");

        ColorMode mode;
        Color color;
        try {
            mode = ColorMode.valueOf(colorModeStr.toUpperCase());
            color = null;
        } catch (IllegalArgumentException e) {
            mode = ColorMode.SINGLE;
            color = parseColor(colorModeStr);
        }
        this.colorMode = mode;
        this.baseColor = color;
        
        this.depthColors = initializeDepthColors();
        this.depthRanges = initializeDepthRanges();
    }

    private Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(propertiesFile)) {
                if (input != null) {
                    props.load(input);
                } else {
                    LOGGER.warning(propertiesFile + " not found in resources");
                    setDefaultProperties(props);
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Error loading " + propertiesFile + ": " + e.getMessage());
            setDefaultProperties(props);
        }
        return props;
    }

    private void setDefaultProperties(Properties props) {
        props.setProperty("color.mode", "GRADUAL");
        props.setProperty("point.size", "10");
        props.setProperty("point.opacity", "0.5");
    }

    private Color[] initializeDepthColors() {
        return new Color[] {
            Color.RED,
            Color.ORANGE,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            Color.MAGENTA
        };
    }

    private double[] initializeDepthRanges() {
        return new double[] {0, 100, 200, 300, 500, 1000, 3000, Double.MAX_VALUE};
    }

    private Color parseColor(String colorName) {
        try {
            return (Color) Color.class.getField(colorName.toUpperCase()).get(null);
        } catch (Exception e) {
            LOGGER.warning("Invalid color name: " + colorName + ". Using BLUE as default.");
            return Color.BLUE;
        }
    }

    public Color getColorForDepth(double depth) {
        switch (colorMode) {
            case DISCRETE:
                for (int i = 0; i < depthRanges.length - 1; i++) {
                    if (depth >= depthRanges[i] && depth < depthRanges[i + 1]) {
                        return depthColors[i];
                    }
                }
                return depthColors[depthColors.length - 1];
                
            case GRADUAL:
                return interpolateColor(normalizeDepth(depth));
                
            case SINGLE:
            default:
                return baseColor;
        }
    }

    private double normalizeDepth(double depth) {
        double minDepth = 0;
        double maxDepth = 100;
        return Math.min(Math.max((depth - minDepth) / (maxDepth - minDepth), 0), 1);
    }

    private Color interpolateColor(double t) {
        Color[] colors = {
            new Color(255, 0, 0),
            new Color(255, 165, 0),
            new Color(255, 255, 0),
            new Color(0, 255, 0),
            new Color(0, 255, 255),
            new Color(0, 0, 255)
        };

        double segment = 1.0 / (colors.length - 1);
        int index = (int) (t / segment);
        index = Math.min(index, colors.length - 2);

        double localT = (t - index * segment) / segment;
        return interpolateColors(colors[index], colors[index + 1], localT);
    }

    private Color interpolateColors(Color c1, Color c2, double t) {
        return new Color(
            interpolateChannel(c1.getRed(), c2.getRed(), t),
            interpolateChannel(c1.getGreen(), c2.getGreen(), t),
            interpolateChannel(c1.getBlue(), c2.getBlue(), t)
        );
    }

    private int interpolateChannel(int a, int b, double t) {
        return (int) Math.round(a * (1 - t) + b * t);
    }

    public Symbolizer createSymbolizer(double depth) {
        Color pointColor = getColorForDepth(depth);
        return styleBuilder.createPointSymbolizer(
            styleBuilder.createGraphic(
                null,
                new org.geotools.api.style.Mark[] {
                    styleBuilder.createMark(
                        StyleBuilder.MARK_CIRCLE,
                        pointColor,
                        new Color(0, 0, 0, 0),
                        0.0)
                },
                null,
                styleBuilder.literalExpression(0.5),
                styleBuilder.literalExpression(10),
                styleBuilder.literalExpression(0)
            ));
    }
} 