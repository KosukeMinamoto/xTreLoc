package com.treloc.xtreloc.app.gui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverlayHandleTest {

    @Test
    public void northEastIsTopRightUntilDragged() {
        OverlayHandle h = new OverlayHandle(OverlayHandle.Corner.NE, 8);
        int[] p = h.place(400, 300, 80, 40);
        assertEquals(400 - 80 - 8, p[0]);
        assertEquals(8, p[1]);
        assertTrue(h.contains(p[0] + 10, p[1] + 10));
        h.moveTo(20, 30, 400, 300);
        assertEquals(20, h.x());
        assertEquals(30, h.y());
        int[] q = h.place(400, 300, 80, 40);
        assertEquals(20, q[0]);
        assertEquals(30, q[1]);
    }

    @Test
    public void placeInKeepsOverlayInsidePlotFrame() {
        OverlayHandle h = new OverlayHandle(OverlayHandle.Corner.NE, 8);
        int[] p = h.placeIn(40, 20, 200, 160, 50, 30);
        assertEquals(40 + 200 - 50 - 8, p[0]);
        assertEquals(20 + 8, p[1]);
        OverlayHandle se = new OverlayHandle(OverlayHandle.Corner.SE, 10);
        int[] q = se.placeIn(40, 20, 200, 160, 36, 36);
        assertEquals(40 + 200 - 36 - 10, q[0]);
        assertEquals(20 + 160 - 36 - 10, q[1]);
        assertTrue(q[0] >= 40);
        assertTrue(q[1] >= 20);
        assertTrue(q[0] + 36 <= 40 + 200);
        assertTrue(q[1] + 36 <= 20 + 160);
    }
}
