package com.treloc.xtreloc.app.gui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class EventSelectionModelTest {

    private static EventKey key(String cat, int i) {
        return new EventKey(cat, "2017-11-15T05:00:0" + i, 36.1 + i, 129.3, 5.0);
    }

    @Test
    public void selectReplacesAndFocuses() {
        EventSelectionModel m = new EventSelectionModel();
        m.select(key("A", 1));
        m.select(key("A", 2));
        assertEquals(1, m.size());
        assertEquals(key("A", 2), m.focus());
        assertFalse(m.isSelected(key("A", 1)));
    }

    @Test
    public void toggleAddsRemovesAndMovesFocus() {
        EventSelectionModel m = new EventSelectionModel();
        m.toggle(key("A", 1));
        m.toggle(key("A", 2));
        assertEquals(2, m.size());
        assertEquals(key("A", 2), m.focus());
        m.toggle(key("A", 2));
        assertEquals(1, m.size());
        assertEquals(key("A", 1), m.focus());
        m.toggle(key("A", 1));
        assertTrue(m.isEmpty());
        assertNull(m.focus());
    }

    @Test
    public void setSelectionKeepsFocusWhenStillSelected() {
        EventSelectionModel m = new EventSelectionModel();
        m.setSelection(List.of(key("A", 1), key("A", 2), key("A", 3)));
        assertEquals(key("A", 1), m.focus());
        m.setFocus(key("A", 3));
        m.setSelection(List.of(key("A", 2), key("A", 3)));
        assertEquals(key("A", 3), m.focus());
        m.setSelection(List.of(key("A", 2)));
        assertEquals(key("A", 2), m.focus());
        m.setSelection(List.of());
        assertNull(m.focus());
    }

    @Test
    public void addAllKeepsFocusAndOrder() {
        EventSelectionModel m = new EventSelectionModel();
        m.select(key("A", 1));
        m.addAll(List.of(key("A", 3), key("A", 2)));
        assertEquals(key("A", 1), m.focus());
        assertEquals(List.of(key("A", 1), key("A", 3), key("A", 2)), List.copyOf(m.selection()));
    }

    @Test
    public void setFocusAddsMissingEvent() {
        EventSelectionModel m = new EventSelectionModel();
        m.setFocus(key("B", 1));
        assertTrue(m.isSelected(key("B", 1)));
        assertEquals(key("B", 1), m.focus());
    }

    @Test
    public void listenersFireOnlyOnRealChange() {
        EventSelectionModel m = new EventSelectionModel();
        AtomicInteger n = new AtomicInteger();
        EventSelectionModel.Listener l = x -> n.incrementAndGet();
        m.addListener(l);
        m.select(key("A", 1));
        m.select(key("A", 1));
        m.clear();
        m.clear();
        assertEquals(2, n.get());
        m.removeListener(l);
        m.select(key("A", 2));
        assertEquals(2, n.get());
    }

    @Test
    public void keysDistinguishSameSecondEventsAndCatalogs() {
        Hypocenter a = new Hypocenter("2017-11-15T05:00:01", 36.1, 129.3, 5.0);
        Hypocenter b = new Hypocenter("2017-11-15T05:00:01", 36.2, 129.3, 5.0);
        assertFalse(EventKey.of("A", a).equals(EventKey.of("A", b)));
        assertFalse(EventKey.of("A", a).equals(EventKey.of("B", a)));
        assertTrue(EventKey.of("A", a).matches("A", a));
        assertFalse(EventKey.of("A", a).matches("B", a));
        assertFalse(EventKey.of("A", a).matches("A", b));
    }
}
