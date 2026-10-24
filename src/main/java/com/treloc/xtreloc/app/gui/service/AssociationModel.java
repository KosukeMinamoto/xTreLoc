package com.treloc.xtreloc.app.gui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The events of the last association run, shared by the Associate and Export steps. */
public final class AssociationModel {

    /** Notified after the events changed (on the thread that changed them). */
    public interface Listener {
        void eventsChanged(AssociationModel model);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private List<RealAssociator.Event> events = List.of();

    public synchronized List<RealAssociator.Event> events() {
        return events;
    }

    public synchronized void setEvents(List<RealAssociator.Event> next) {
        events = List.copyOf(next == null ? new ArrayList<>() : next);
        for (Listener l : listeners) {
            l.eventsChanged(this);
        }
    }

    public void addListener(Listener l) {
        if (l != null) {
            listeners.add(l);
        }
    }
}
