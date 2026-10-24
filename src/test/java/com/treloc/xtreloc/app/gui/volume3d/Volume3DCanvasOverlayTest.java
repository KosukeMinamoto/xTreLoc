package com.treloc.xtreloc.app.gui.volume3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Layer-cake volume with the shared overlays (shapefiles, stations, catalog-coloured hypocentres). */
public class Volume3DCanvasOverlayTest {

    private static int count(BufferedImage img, int rgb, int tol) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                if (Math.abs(((p >> 16) & 255) - ((rgb >> 16) & 255)) <= tol
                    && Math.abs(((p >> 8) & 255) - ((rgb >> 8) & 255)) <= tol
                    && Math.abs((p & 255) - (rgb & 255)) <= tol) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    public void overlaysAreDrawnOnALayerCake() {
        System.setProperty("java.awt.headless", "true");
        VelocityVolume3D vol = VelocityVolume3D.fromProfile1D("lc", new double[] {0, 20, 20, 100},
            new double[] {3.2, 3.6, 4.4, 4.6}, 139, 142, 37, 40, 100);
        Volume3DCanvas c = new Volume3DCanvas();
        c.setSize(700, 500);
        c.renderer().setVolume(vol, null);
        c.renderer().channel = VelocityVolume3D.CH_VS;
        c.renderer().vmin = 3.0;
        c.renderer().vmax = 5.0;
        c.renderer().showLonSlice = false;
        c.renderer().showLatSlice = false;
        c.renderer().showDepthSlice = false;
        c.setShowBox(false);
        c.setShowAxes(false);
        c.setViewPreset("top");

        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {140.5, 38.5, 10});
        c.setHypocenterGroups(Arrays.asList(new Volume3DCanvas.PointGroup(new Color(0, 200, 0), pts)));
        c.setStations(Arrays.asList(new Volume3DCanvas.StationPoint("A", 141, 39, 0)));
        c.setShapeLines(Arrays.asList(new double[] {139, 37, 142, 40}));
        assertEquals(1, c.hypocenterCount());
        assertEquals(1, c.stationCount());
        assertEquals(1, c.shapeLineCount());

        BufferedImage img = c.snapshot();
        assertTrue("hypocenter in catalog colour", count(img, 0x00C800, 30) > 3);
        assertTrue("station triangle", count(img, 0x000000, 5) > 10);

        c.setShowHypocenters(false);
        c.setShowStations(false);
        BufferedImage off = c.snapshot();
        assertEquals(0, count(off, 0x00C800, 30));
    }
}
