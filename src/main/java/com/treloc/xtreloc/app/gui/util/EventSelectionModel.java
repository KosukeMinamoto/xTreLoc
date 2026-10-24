package com.treloc.xtreloc.app.gui.util;

import com.treloc.xtreloc.app.gui.model.EventKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared selection of catalog events: a set of selected events and one focus event (the one shown
 * in detail). The map, the event table and, later, the Seismic tab all read and write this model, so
 * they never need to know about each other.
 *
 * <p>Not thread-safe by itself; call from the EDT. Listeners run synchronously after a change and
 * only when something actually changed.</p>
 */
public final class EventSelectionModel {

    /** Notified after the selection or the focus changed. */
    public interface Listener {
        void selectionChanged(EventSelectionModel model);
    }

    private static final EventSelectionModel INSTANCE = new EventSelectionModel();

    private final Set<EventKey> selected = new LinkedHashSet<>();
    private EventKey focus;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public EventSelectionModel() {
    }

    /** Application-wide model shared by all views. */
    public static EventSelectionModel getInstance() {
        return INSTANCE;
    }

    public void addListener(Listener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Selected events in selection order (a copy). */
    public Set<EventKey> selection() {
        return new LinkedHashSet<>(selected);
    }

    public int size() {
        return selected.size();
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public boolean isSelected(EventKey key) {
        return key != null && selected.contains(key);
    }

    /** The focus event, or {@code null}. The focus is always part of the selection. */
    public EventKey focus() {
        return focus;
    }

    /** Replaces the selection by {@code keys}; the focus becomes the first key when it is not kept. */
    public void setSelection(Collection<EventKey> keys) {
        Set<EventKey> next = keys == null ? new LinkedHashSet<>() : new LinkedHashSet<>(keys);
        next.remove(null);
        EventKey nextFocus = focus != null && next.contains(focus) ? focus
            : (next.isEmpty() ? null : next.iterator().next());
        apply(next, nextFocus);
    }

    /** Selects exactly one event and focuses it. */
    public void select(EventKey key) {
        if (key == null) {
            clear();
            return;
        }
        Set<EventKey> next = new LinkedHashSet<>();
        next.add(key);
        apply(next, key);
    }

    /** Adds {@code keys} to the selection; the focus is kept, or set to the first added event. */
    public void addAll(Collection<EventKey> keys) {
        if (keys == null) {
            return;
        }
        Set<EventKey> next = new LinkedHashSet<>(selected);
        for (EventKey k : keys) {
            if (k != null) {
                next.add(k);
            }
        }
        EventKey nextFocus = focus != null ? focus : (next.isEmpty() ? null : next.iterator().next());
        apply(next, nextFocus);
    }

    /** Adds the event when absent (and focuses it), removes it when present. */
    public void toggle(EventKey key) {
        if (key == null) {
            return;
        }
        Set<EventKey> next = new LinkedHashSet<>(selected);
        EventKey nextFocus = focus;
        if (!next.remove(key)) {
            next.add(key);
            nextFocus = key;
        } else if (key.equals(focus)) {
            nextFocus = next.isEmpty() ? null : next.iterator().next();
        }
        apply(next, nextFocus);
    }

    /** Moves the focus to {@code key}, adding it to the selection if needed. */
    public void setFocus(EventKey key) {
        if (key == null) {
            return;
        }
        Set<EventKey> next = new LinkedHashSet<>(selected);
        next.add(key);
        apply(next, key);
    }

    public void clear() {
        apply(new LinkedHashSet<>(), null);
    }

    private void apply(Set<EventKey> nextSelected, EventKey nextFocus) {
        boolean same = nextSelected.equals(selected)
            && (nextFocus == null ? focus == null : nextFocus.equals(focus));
        if (same) {
            return;
        }
        selected.clear();
        selected.addAll(nextSelected);
        focus = nextFocus;
        for (Listener l : new ArrayList<>(listeners)) {
            l.selectionChanged(this);
        }
    }
}
