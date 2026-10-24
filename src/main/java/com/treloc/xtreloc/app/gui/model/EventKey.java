package com.treloc.xtreloc.app.gui.model;

import java.util.Objects;

/**
 * Identity of one catalog event: the catalog it belongs to plus its origin time and hypocentre.
 *
 * <p>The time is used exactly as stored in {@link Hypocenter#time} (not rounded to the minute), and
 * the position is part of the key so two events in the same second stay distinct. Two catalogs
 * never share keys; matching the same physical event across catalogs is a separate problem.</p>
 */
public record EventKey(String catalog, String time, double lat, double lon, double depth) {

    public EventKey {
        catalog = catalog == null ? "" : catalog;
        time = time == null ? "" : time;
    }

    public static EventKey of(String catalogName, Hypocenter h) {
        Objects.requireNonNull(h, "hypocenter");
        return new EventKey(catalogName, h.time, h.lat, h.lon, h.depth);
    }

    public static EventKey of(CatalogInfo catalog, Hypocenter h) {
        return of(catalog == null ? "" : catalog.getName(), h);
    }

    /** True if {@code h} (taken from the catalog named {@code catalogName}) is this event. */
    public boolean matches(String catalogName, Hypocenter h) {
        return h != null
            && catalog.equals(catalogName == null ? "" : catalogName)
            && time.equals(h.time == null ? "" : h.time)
            && Double.compare(lat, h.lat) == 0
            && Double.compare(lon, h.lon) == 0
            && Double.compare(depth, h.depth) == 0;
    }
}
