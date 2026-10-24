package com.treloc.xtreloc.app.gui.map2d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Map2DRendererTest {

    private static final String RAW =
        "  36.000 138.500   0.000   3.000    0.1    0.2  1\n"
      + "  36.000 139.000   0.000   3.100    0.3    0.4  1\n"
      + "  37.000 138.500   0.000   3.200    0.5    0.6  2\n"
      + "  37.000 139.000   0.000   3.300    0.7    0.8  2\n"
      + "  36.000 138.500  10.000   4.000    1.1    1.2  3\n"
      + "  36.000 139.000  10.000   4.100    1.3    1.4  3\n"
      + "  37.000 138.500  10.000   4.200    1.5    1.6  4\n"
      + "  37.000 139.000  10.000   4.300    1.7    1.8  4\n";

    @Test
    public void slicePosDefaultsToMidDepthOnPlan() throws Exception {
        Path p = Files.createTempFile("map2d", ".out");
        p.toFile().deleteOnExit();
        Files.write(p, RAW.getBytes(StandardCharsets.UTF_8));
        VelocityVolume3D v = VelocityVolume3D.fromNakajimaRaw(p);
        Map2DRenderer r = new Map2DRenderer();
        r.collapse = 2;
        r.reduce = VelocityVolume3D.Reduce.SLICE;
        r.setVolume(v, VelocityVolume3D.CH_VS);
        assertEquals(5.0, r.slicePos, 1e-9);
        assertEquals(3.5, r.plane().sample(138.5, 36.0), 1e-6);
        r.slicePos = 10.0;
        r.rebuildPlane();
        assertEquals(4.0, r.plane().sample(138.5, 36.0), 1e-6);
    }

    @Test
    public void scaleRatioStretchesVerticalPixels() throws Exception {
        Path p = Files.createTempFile("map2d", ".out");
        p.toFile().deleteOnExit();
        Files.write(p, RAW.getBytes(StandardCharsets.UTF_8));
        Map2DRenderer r = new Map2DRenderer();
        r.collapse = 0;
        r.reduce = VelocityVolume3D.Reduce.SLICE;
        r.setVolume(VelocityVolume3D.fromNakajimaRaw(p), VelocityVolume3D.CH_VS);
        r.scaleRatio = 1.0;
        double[] a = r.toPixel(400, 400, 36.0, 0.0);
        double[] b = r.toPixel(400, 400, 36.0, 10.0);
        double d1 = Math.abs(b[1] - a[1]);
        r.scaleRatio = 2.0;
        double[] c = r.toPixel(400, 400, 36.0, 0.0);
        double[] d = r.toPixel(400, 400, 36.0, 10.0);
        assertTrue(Math.abs(d[1] - c[1]) > d1 * 1.2);
    }

    @Test
    public void sectionDepthIncreasesDownward() throws Exception {
        Path p = Files.createTempFile("map2d", ".out");
        p.toFile().deleteOnExit();
        Files.write(p, RAW.getBytes(StandardCharsets.UTF_8));
        Map2DRenderer r = new Map2DRenderer();
        r.collapse = 0;
        r.reduce = VelocityVolume3D.Reduce.SLICE;
        r.setVolume(VelocityVolume3D.fromNakajimaRaw(p), VelocityVolume3D.CH_VS);
        double[] shallow = r.toPixel(400, 400, 36.0, 0.0);
        double[] deep = r.toPixel(400, 400, 36.0, 10.0);
        assertTrue(deep[1] > shallow[1]);
        double[] back = r.toWorld(400, 400, deep[0], deep[1]);
        assertEquals(10.0, back[1], 1e-6);
    }

    @Test
    public void uniformPremPlanSlicePaintsVelocity() {
        VelocityVolume3D v = VelocityVolume3D.fromProfile1D("prem",
            new double[] {0, 20, 50}, new double[] {3.2, 3.9, 4.5},
            138, 145, 34, 42, 100);
        Map2DRenderer r = new Map2DRenderer();
        r.collapse = 2;
        r.reduce = VelocityVolume3D.Reduce.SLICE;
        r.showVelocity = true;
        r.setVolume(v, VelocityVolume3D.CH_VS);
        assertTrue(r.vmax > r.vmin);
        int[] px = new int[400 * 300];
        r.renderBackground(400, 300, px);
        int colored = 0;
        for (int p : px) {
            if ((p & 0x00FFFFFF) != 0x00FFFFFF) {
                colored++;
            }
        }
        assertTrue(colored > 1000);
    }

    @Test
    public void plotFrameSitsInsideCanvasPadding() {
        Map2DRenderer r = new Map2DRenderer();
        r.collapse = 2;
        r.lonMin = 138;
        r.lonMax = 145;
        r.latMin = 34;
        r.latMax = 42;
        int w = 400;
        int h = 300;
        double[] b = r.worldBounds();
        double[] sw = r.toPixel(w, h, b[0], b[2]);
        double[] ne = r.toPixel(w, h, b[1], b[3]);
        assertTrue(Math.min(sw[0], ne[0]) > 8);
        assertTrue(Math.max(sw[0], ne[0]) < w - 8);
        assertTrue(Math.min(sw[1], ne[1]) > 8);
        assertTrue(Math.max(sw[1], ne[1]) < h - 8);
    }

    @Test
    public void planViewIgnoresScaleRatio() {
        Map2DRenderer stretched = new Map2DRenderer();
        stretched.collapse = 2;
        stretched.scaleRatio = 8.0;
        Map2DRenderer unit = new Map2DRenderer();
        unit.collapse = 2;
        unit.scaleRatio = 1.0;
        assertEquals(unit.kmPerV(), stretched.kmPerV(), 1e-9);
    }

    @Test
    public void gridFillUsesNearestNode_gradualInterpolates() throws Exception {
        Path p = Files.createTempFile("map2d", ".out");
        p.toFile().deleteOnExit();
        Files.write(p, RAW.getBytes(StandardCharsets.UTF_8));
        Map2DRenderer r = new Map2DRenderer();
        r.collapse = 2;
        r.reduce = VelocityVolume3D.Reduce.SLICE;
        r.setVolume(VelocityVolume3D.fromNakajimaRaw(p), VelocityVolume3D.CH_VS);
        r.slicePos = 0.0;
        r.rebuildPlane();
        assertEquals(3.05, r.plane().sample(138.75, 36.0), 1e-6);
        assertEquals(3.0, r.plane().sampleNearest(138.75, 36.0), 1e-6);
        r.showVelocity = true;
        r.fillStyle = Map2DRenderer.FillStyle.GRID;
        int[] gridPx = new int[80 * 80];
        r.renderBackground(80, 80, gridPx);
        r.fillStyle = Map2DRenderer.FillStyle.GRADUAL;
        int[] gradPx = new int[80 * 80];
        r.renderBackground(80, 80, gradPx);
        int differ = 0;
        for (int i = 0; i < gridPx.length; i++) {
            if (gridPx[i] != gradPx[i]) {
                differ++;
            }
        }
        assertTrue(differ > 0);
        r.fillStyle = Map2DRenderer.FillStyle.CONTOUR;
        int[] contourPx = new int[80 * 80];
        r.renderBackground(80, 80, contourPx);
        for (int c : contourPx) {
            assertEquals(0xFFFFFFFF, c);
        }
    }
}
