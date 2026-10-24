package com.treloc.xtreloc.app.gui.volume3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class Volume3DRendererTest {

    /** Vs = 3 + 0.02 * depth, dvs = lon - 140 (percent), layer = 1 above 20 km else 3. */
    private static VelocityVolume3D volume() {
        double[] lons = new double[9];
        double[] lats = new double[9];
        double[] deps = new double[11];
        for (int i = 0; i < 9; i++) {
            lons[i] = 138 + 0.5 * i;
            lats[i] = 36 + 0.5 * i;
        }
        for (int k = 0; k < 11; k++) {
            deps[k] = 10.0 * k;
        }
        int n = 9 * 9 * 11;
        float[] vs = new float[n];
        float[] dvs = new float[n];
        float[] layer = new float[n];
        for (int k = 0; k < 11; k++) {
            for (int j = 0; j < 9; j++) {
                for (int i = 0; i < 9; i++) {
                    int idx = (k * 9 + j) * 9 + i;
                    vs[idx] = (float) (3 + 0.02 * deps[k]);
                    dvs[idx] = (float) (lons[i] - 140);
                    layer[idx] = deps[k] < 20 ? 1 : 3;
                }
            }
        }
        Map<String, float[]> ch = new LinkedHashMap<>();
        ch.put(VelocityVolume3D.CH_VS, vs);
        ch.put(VelocityVolume3D.CH_DVS_MEAN, dvs);
        ch.put(VelocityVolume3D.CH_LAYER, layer);
        return VelocityVolume3D.of("test", lons, lats, deps, ch);
    }

    private static Volume3DRenderer topDownDepthSlice(VelocityVolume3D v, String channel) {
        Volume3DRenderer r = new Volume3DRenderer();
        r.setVolume(v, channel);
        r.vmin = 3.0;
        r.vmax = 5.0;
        r.showLonSlice = false;
        r.showLatSlice = false;
        r.showDepthSlice = true;
        r.depthSlice = 50.0;
        r.azimuthDeg = 0;
        r.elevationDeg = 89.0;
        return r;
    }

    @Test
    public void depthSliceSeenFromAbove_hasSliceColourAtCentre_andBackgroundAtCorner() {
        VelocityVolume3D v = volume();
        Volume3DRenderer r = topDownDepthSlice(v, VelocityVolume3D.CH_VS);
        int w = 200;
        int h = 160;
        int[] px = new int[w * h];
        float[] z = new float[w * h];
        r.render(w, h, px, z);
        int expected = r.colorOf(3 + 0.02 * 50);
        assertEquals(expected, px[(h / 2) * w + w / 2] & 0xFFFFFF);
        assertEquals(0xFFFFFF, px[0] & 0xFFFFFF);
        assertTrue(Float.isInfinite(z[0]));
        assertTrue(z[(h / 2) * w + w / 2] > 0 && Float.isFinite(z[(h / 2) * w + w / 2]));
    }

    @Test
    public void probeAndProjectAreConsistent() {
        VelocityVolume3D v = volume();
        Volume3DRenderer r = topDownDepthSlice(v, VelocityVolume3D.CH_DVS_MEAN);
        r.azimuthDeg = 20;
        r.elevationDeg = 45;
        int w = 400;
        int h = 300;
        Volume3DRenderer.Camera cam = r.camera(w, h);
        double lon = 139.2;
        double lat = 37.7;
        double[] s = r.project(cam, lon, lat, 50.0);
        assertNotNull(s);
        Volume3DRenderer.Probe p = r.probe(w, h, s[0], s[1]);
        assertNotNull(p);
        assertEquals(lon, p.lon, 1e-6);
        assertEquals(lat, p.lat, 1e-6);
        assertEquals(50.0, p.depthKm, 1e-6);
        // channel order: vs, dvs_mean, layer
        assertEquals(3 + 0.02 * 50, p.values[0], 1e-4);
        assertEquals(lon - 140, p.values[1], 1e-4);
        assertEquals(3.0, p.values[2], 0.0);
    }

    @Test
    public void probeOutsideTheSlicesReturnsNull() {
        VelocityVolume3D v = volume();
        Volume3DRenderer r = topDownDepthSlice(v, VelocityVolume3D.CH_VS);
        assertNull(r.probe(200, 160, 1.5, 1.5));
    }

    @Test
    public void layerMask_removesHiddenLayers() {
        VelocityVolume3D v = volume();
        Volume3DRenderer r = topDownDepthSlice(v, VelocityVolume3D.CH_VS);
        r.layerVisible = new boolean[] {true, true, false, true}; // layer 3 hidden; slice at 50 km is layer 3
        int w = 120;
        int h = 100;
        int[] px = new int[w * h];
        r.render(w, h, px, null);
        assertEquals(0xFFFFFF, px[(h / 2) * w + w / 2] & 0xFFFFFF);
        assertNull(r.probe(w, h, w / 2.0, h / 2.0));
    }

    @Test
    public void opacityBlendsWithBackground() {
        VelocityVolume3D v = volume();
        Volume3DRenderer r = topDownDepthSlice(v, VelocityVolume3D.CH_VS);
        r.alpha = 0.5;
        int w = 100;
        int h = 80;
        int[] px = new int[w * h];
        r.render(w, h, px, null);
        int c = r.colorOf(4.0);
        int got = px[(h / 2) * w + w / 2];
        int expR = (int) Math.round(0.5 * ((c >> 16) & 255) + 0.5 * 255);
        assertEquals(expR, (got >> 16) & 255, 1);
    }

    @Test
    public void nearerSliceHidesFartherOne() {
        VelocityVolume3D v = volume();
        Volume3DRenderer r = topDownDepthSlice(v, VelocityVolume3D.CH_VS);
        r.depthSlice = 100.0; // deep
        // second slice at shallow depth is not available (one depth slice only); use lon slice vertical plane
        r.showLonSlice = true;
        r.lonSlice = 140.0;
        r.elevationDeg = 0.0; // horizontal view from the south: the lon plane is edge-on-ish only at az=90
        r.azimuthDeg = 90.0;  // looking east: the lon plane faces the camera
        int w = 200;
        int h = 160;
        int[] px = new int[w * h];
        r.render(w, h, px, null);
        Volume3DRenderer.Probe p = r.probe(w, h, w / 2.0, h / 2.0);
        assertNotNull(p);
        assertEquals(140.0, p.lon, 1e-6);
    }

    @Test
    public void colorMaps_endpointsAndLayerPalette() {
        assertEquals(0x67001F, ColorMaps.SEISMIC.rgb(0.0));
        assertEquals(0x053061, ColorMaps.SEISMIC.rgb(1.0));
        int mid = ColorMaps.SEISMIC.rgb(0.5);
        assertTrue(((mid >> 16) & 255) > 0xF0 && ((mid >> 8) & 255) > 0xF0 && (mid & 255) > 0xF0);
        assertEquals(ColorMaps.GRAY.rgb(0.0), 0x000000);
        assertEquals(ColorMaps.GRAY.rgb(1.0), 0xFFFFFF);
        assertEquals(ColorMaps.GRAY.rgb(5.0), 0xFFFFFF);
        assertEquals(ColorMaps.GRAY.rgb(Double.NaN), 0x000000);
        assertEquals(ColorMaps.LAYER_RGB[3], ColorMaps.layerRgb(4));
        assertEquals(0x808080, ColorMaps.layerRgb(9));
    }
}
