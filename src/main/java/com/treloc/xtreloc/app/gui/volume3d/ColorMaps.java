package com.treloc.xtreloc.app.gui.volume3d;

/** Colour maps (256-entry look-up tables) and the categorical palette of the model layers. */
public enum ColorMaps {

    /** Tomography convention: low velocity red, high velocity blue. */
    SEISMIC("Seismic (red = slow, blue = fast)", new int[][] {
        {103, 0, 31}, {178, 24, 43}, {214, 96, 77}, {244, 165, 130}, {253, 219, 199}, {247, 247, 247},
        {209, 229, 240}, {146, 197, 222}, {67, 147, 195}, {33, 102, 172}, {5, 48, 97}}),
    VIRIDIS("Viridis", new int[][] {
        {68, 1, 84}, {71, 44, 122}, {59, 81, 139}, {44, 113, 142}, {33, 144, 141}, {39, 173, 129},
        {92, 200, 99}, {170, 220, 50}, {253, 231, 37}}),
    RAINBOW("Rainbow (blue = low, red = high)", new int[][] {
        {0, 0, 128}, {0, 0, 255}, {0, 200, 255}, {0, 230, 120}, {255, 255, 0}, {255, 128, 0}, {200, 0, 0}}),
    GRAY("Grayscale", new int[][] {{0, 0, 0}, {255, 255, 255}});

    /** Colours of the model layers 1..4 (upper crust, lower crust, mantle, slab). */
    public static final int[] LAYER_RGB = {0xD6B478, 0xC87850, 0x5A96C8, 0x5ABE6E};
    public static final String[] LAYER_NAMES = {"Upper crust", "Lower crust", "Mantle", "Slab"};

    private final String label;
    private final int[] lut;

    ColorMaps(String label, int[][] stops) {
        this.label = label;
        this.lut = buildLut(stops, 256);
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    /** Copy of the 256-entry 0xRRGGBB look-up table. */
    public int[] lut() {
        return lut.clone();
    }

    /** 0xRRGGBB for {@code t} in [0,1] (clamped). */
    public int rgb(double t) {
        if (!(t > 0.0)) {
            return lut[0];
        }
        if (t >= 1.0) {
            return lut[lut.length - 1];
        }
        return lut[(int) (t * (lut.length - 1) + 0.5)];
    }

    /** 0xRRGGBB for layer id 1..4; mid-gray for other values. */
    public static int layerRgb(double layer) {
        int id = (int) Math.round(layer);
        return id >= 1 && id <= LAYER_RGB.length ? LAYER_RGB[id - 1] : 0x808080;
    }

    private static int[] buildLut(int[][] stops, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            double x = (double) i / (n - 1) * (stops.length - 1);
            int a = Math.min((int) x, stops.length - 2);
            double f = x - a;
            int r = (int) Math.round(stops[a][0] + (stops[a + 1][0] - stops[a][0]) * f);
            int g = (int) Math.round(stops[a][1] + (stops[a + 1][1] - stops[a][1]) * f);
            int b = (int) Math.round(stops[a][2] + (stops[a + 1][2] - stops[a][2]) * f);
            out[i] = (r << 16) | (g << 8) | b;
        }
        return out;
    }
}
