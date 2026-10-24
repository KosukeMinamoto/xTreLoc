package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class WaveformCanvasTest {

    @Test
    public void pickIsIgnoredWhenManualEditIsOff() throws Exception {
        System.setProperty("java.awt.headless", "true");
        AtomicInteger picks = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            WaveformCanvas c = new WaveformCanvas();
            c.setTraces(Collections.singletonList(sampleTrace()));
            c.setPickListener((s, i, phase, t) -> picks.incrementAndGet());
            c.setManualEditEnabled(false);
            assertFalse(c.applyPickAt(0, 10, "P"));
            c.setManualEditEnabled(true);
            assertTrue(c.applyPickAt(0, 10, "P"));
            assertTrue(c.applyPickAt(0, 20, "S"));
        });
        assertEquals(2, picks.get());
    }

    @Test
    public void panModeDisablesManualEdit() throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            WaveformCanvas c = new WaveformCanvas();
            c.setManualEditEnabled(true);
            c.setPanMode(true);
            assertTrue(c.isPanMode());
            assertFalse(c.isManualEditEnabled());
        });
    }

    @Test
    public void visibleAbsMaxUsesWindowOnly() {
        float[] y = new float[20];
        for (int i = 0; i < 20; i++) {
            y[i] = i == 2 ? 10f : 1f;
        }
        assertEquals(10.0, WaveformCanvas.visibleAbsMax(y, 0, 19), 1e-9);
        assertEquals(1.0, WaveformCanvas.visibleAbsMax(y, 5, 15), 1e-9);
    }

    private static WaveformCanvas.Trace sampleTrace() {
        float[] y = new float[64];
        for (int i = 0; i < y.length; i++) {
            y[i] = (float) Math.sin(i * 0.2);
        }
        return new WaveformCanvas.Trace("STA", "HHZ", Color.BLUE, 1_000_000L, 0.01, y);
    }
}
