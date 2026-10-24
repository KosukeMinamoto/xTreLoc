package com.treloc.xtreloc.app.gui.controller;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.service.ArrivalTime;
import com.treloc.xtreloc.app.gui.service.EventRelocator;
import com.treloc.xtreloc.app.gui.util.WorkingLayer;
import com.treloc.xtreloc.app.gui.view.PickViewerPanel;
import com.treloc.xtreloc.io.AppConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingWorker;

/**
 * Relocates the event shown in the Seismic tab from its current picks and records the result in the
 * {@link WorkingLayer}. The solver and its parameters are those selected in the Solver panel.
 */
public final class WorkingRelocationController implements PickViewerPanel.RelocateHandler {

    private final Supplier<AppConfig> configSupplier;
    private final Supplier<String> modeSupplier;
    private final Supplier<List<CatalogInfo>> catalogs;
    private final WorkingLayer layer;

    /**
     * @param configSupplier solver configuration (called on the EDT)
     * @param modeSupplier   selected solver mode, e.g. {@code "LMO"} (called on the EDT)
     * @param catalogs       loaded catalogs, used to find the starting location of the event
     */
    public WorkingRelocationController(Supplier<AppConfig> configSupplier, Supplier<String> modeSupplier,
                                       Supplier<List<CatalogInfo>> catalogs, WorkingLayer layer) {
        this.configSupplier = configSupplier;
        this.modeSupplier = modeSupplier;
        this.catalogs = catalogs;
        this.layer = layer;
    }

    @Override
    public void relocate(EventKey event, Collection<ArrivalTime> picks, Consumer<String> done) {
        // A Working event is relocated again from where it is now, but still replaces the same original.
        EventKey original = event;
        Hypocenter before = null;
        Hypocenter start = null;
        if (WorkingLayer.CATALOG_NAME.equals(event.catalog())) {
            for (WorkingLayer.Entry e : layer.entries()) {
                if (event.matches(WorkingLayer.CATALOG_NAME, e.after)) {
                    original = e.original;
                    before = e.before;
                    start = e.after;
                    break;
                }
            }
        } else {
            start = find(event);
            before = start;
        }
        if (start == null) {
            done.accept("Event is not in the loaded catalogs.");
            return;
        }
        String mode = modeSupplier.get();
        if (!EventRelocator.isSupported(mode)) {
            done.accept("Select " + String.join(", ", EventRelocator.SUPPORTED_MODES)
                + " in the Solver panel (now: " + mode + ").");
            return;
        }
        final AppConfig cfg;
        try {
            cfg = configSupplier.get();
        } catch (RuntimeException ex) {
            done.accept("Solver settings are incomplete: " + message(ex));
            return;
        }
        final EventKey key = original;
        final Hypocenter was = before;
        final Hypocenter from = start;
        final List<ArrivalTime> pickList = new ArrayList<>(picks);
        new SwingWorker<EventRelocator.Result, Void>() {
            @Override
            protected EventRelocator.Result doInBackground() throws Exception {
                return EventRelocator.relocate(cfg, mode, from, pickList, Collections.emptyMap(), 0);
            }

            @Override
            protected void done() {
                try {
                    EventRelocator.Result r = get();
                    WorkingLayer.Entry e = layer.put(key, was, r.hypocenter, r.mode, r.pairRows);
                    done.accept(String.format(Locale.ROOT, "%s: \u0394h %.2f km, \u0394z %+.2f km (%d pairs)",
                        r.mode, e.horizontalShiftKm(), e.depthShiftKm(), r.pairRows));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    done.accept("Interrupted.");
                } catch (ExecutionException ex) {
                    done.accept("Failed: " + message(ex.getCause() != null ? ex.getCause() : ex));
                }
            }
        }.execute();
    }

    private Hypocenter find(EventKey key) {
        for (CatalogInfo c : catalogs.get()) {
            if (!key.catalog().equals(c.getName()) || c.getHypocenters() == null) {
                continue;
            }
            for (Hypocenter h : c.getHypocenters()) {
                if (key.matches(c.getName(), h)) {
                    return h;
                }
            }
        }
        return null;
    }

    private static String message(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }
}
