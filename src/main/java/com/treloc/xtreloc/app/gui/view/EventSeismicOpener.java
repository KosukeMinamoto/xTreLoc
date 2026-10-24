package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.service.EventWaveformResolver;
import com.treloc.xtreloc.app.gui.util.AppSettingsCache;
import com.treloc.xtreloc.app.gui.util.EventSelectionModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sends the waveform folders of a map event to the Seismic page: resolves them from the event time
 * and the folder pattern of the settings, and reports the reason when nothing is found.
 */
public final class EventSeismicOpener {

    /** Receives the waveform folders of the events to show in the Seismic page. */
    public interface SeismicOpener {
        /**
         * @param focus        the event to display
         * @param focusFolders its waveform folders (never empty)
         * @param otherFolders folders of the other selected events, to be added to the folder tree
         */
        void open(EventKey focus, List<Path> focusFolders, List<Path> otherFolders);
    }

    /** Most selected events whose folders are looked up when the selection is included. */
    static final int MAX_OPEN_EVENTS = 50;

    private final EventSelectionModel model;
    private final Supplier<List<CatalogInfo>> catalogs;
    private Supplier<String> patternSupplier = () -> AppSettingsCache.snapshot().getEventWaveformPattern();
    private SeismicOpener opener;
    private Consumer<String> messages = m -> { };

    public EventSeismicOpener(EventSelectionModel model, Supplier<List<CatalogInfo>> catalogs) {
        this.model = model;
        this.catalogs = catalogs != null ? catalogs : List::of;
    }

    /** Where the folders are sent; without it {@link #open} does nothing. */
    public void setSeismicOpener(SeismicOpener opener) {
        this.opener = opener;
    }

    /** Receives a short message when an event cannot be opened (and a blank one on success). */
    public void setMessageListener(Consumer<String> listener) {
        this.messages = listener != null ? listener : m -> { };
    }

    void setPatternSupplier(Supplier<String> supplier) {
        this.patternSupplier = supplier != null ? supplier : () -> "";
    }

    /**
     * Resolves the waveform folders of {@code focus} (and, with {@code includeSelection}, of the other
     * selected events) and hands them to the opener.
     */
    public void open(EventKey focus, boolean includeSelection) {
        if (opener == null || focus == null) {
            return;
        }
        List<CatalogInfo> cats = catalogs.get();
        String pattern = patternSupplier.get();
        EventWaveformResolver.Resolution main = EventWaveformResolver.resolve(pattern, cats, focus);
        if (!main.found()) {
            messages.accept(main.reason());
            return;
        }
        List<EventWaveformResolver.Resolution> others = new ArrayList<>();
        if (includeSelection) {
            int n = 0;
            for (EventKey k : model.selection()) {
                if (k.equals(focus)) {
                    continue;
                }
                if (++n > MAX_OPEN_EVENTS) {
                    break;
                }
                others.add(EventWaveformResolver.resolve(pattern, cats, k));
            }
        }
        List<Path> otherFolders = EventWaveformResolver.allFolders(others);
        otherFolders.removeAll(main.folders());
        messages.accept(" ");
        opener.open(focus, main.folders(), otherFolders);
    }
}
