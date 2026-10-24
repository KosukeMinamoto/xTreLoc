package com.treloc.xtreloc.app.gui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class WorkingLayerTest {

    private final Hypocenter before = new Hypocenter("2017-11-15T05:20:01", 36.00, 129.30, 5.0);
    private final EventKey key = EventKey.of("Woo", before);

    @Test
    public void putReplacesTheEntryOfTheSameOriginalAndNotifies() {
        WorkingLayer layer = new WorkingLayer();
        AtomicInteger n = new AtomicInteger();
        layer.addListener(l -> n.incrementAndGet());
        Hypocenter a1 = new Hypocenter("2017-11-15T05:20:01", 36.01, 129.30, 6.0);
        Hypocenter a2 = new Hypocenter("2017-11-15T05:20:01", 36.02, 129.30, 7.0);
        layer.put(key, before, a1, "LMO", 10);
        layer.put(key, before, a2, "GRD", 12);
        assertEquals(1, layer.size());
        assertSame(a2, layer.get(key).after);
        assertEquals(java.util.List.of(a2), layer.hypocenters());
        assertEquals(2, n.get());
    }

    @Test
    public void shiftIsMeasuredFromTheCatalogPosition() {
        WorkingLayer layer = new WorkingLayer();
        Hypocenter after = new Hypocenter("2017-11-15T05:20:01", 36.01, 129.30, 8.5);
        WorkingLayer.Entry e = layer.put(key, before, after, "LMO", 6);
        assertEquals(1.1119, e.horizontalShiftKm(), 0.001);
        assertEquals(3.5, e.depthShiftKm(), 1e-9);
    }

    @Test
    public void removeAndClearOnlyNotifyOnChange() {
        WorkingLayer layer = new WorkingLayer();
        AtomicInteger n = new AtomicInteger();
        layer.put(key, before, before, "LMO", 6);
        layer.addListener(l -> n.incrementAndGet());
        layer.remove(EventKey.of("Other", before));
        assertEquals(0, n.get());
        layer.remove(key);
        assertNull(layer.get(key));
        layer.clear();
        assertEquals(1, n.get());
        assertTrue(layer.hypocenters().isEmpty());
    }
}
