package com.treloc.xtreloc.app.gui.model;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Detects a catalog that would redraw events already on the map.
 */
public final class CatalogOverlap {

    public static final double OVERLAP_THRESHOLD = 0.5;

    private CatalogOverlap() {
    }

    public static String canonicalPath(File file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    public static boolean sameFile(File a, File b) {
        String pa = canonicalPath(a);
        String pb = canonicalPath(b);
        return pa != null && pa.equals(pb);
    }

    public static String uniqueName(String base, Collection<String> existing) {
        String name = base == null || base.isBlank() ? "catalog" : base.trim();
        if (existing == null || !containsIgnoreCase(existing, name)) {
            return name;
        }
        int n = 2;
        String candidate;
        do {
            candidate = name + " (" + n + ")";
            n++;
        } while (containsIgnoreCase(existing, candidate));
        return candidate;
    }

    public static String eventKey(Hypocenter h) {
        if (h == null) {
            return null;
        }
        String time = CatalogInfo.normalizeTime(h.time);
        if (time == null || time.isBlank()) {
            time = "";
        }
        if (!Double.isFinite(h.lat) || !Double.isFinite(h.lon)) {
            return null;
        }
        return time + "|" + String.format(Locale.ROOT, "%.4f|%.4f", h.lat, h.lon);
    }

    /**
     * Fraction of {@code incoming} events that already appear in {@code existing}
     * (time + rounded lat/lon). {@code 0} if either list is empty.
     */
    public static double overlapRatio(List<Hypocenter> existing, List<Hypocenter> incoming) {
        if (existing == null || incoming == null || existing.isEmpty() || incoming.isEmpty()) {
            return 0.0;
        }
        Set<String> have = new HashSet<>();
        for (Hypocenter h : existing) {
            String k = eventKey(h);
            if (k != null) {
                have.add(k);
            }
        }
        if (have.isEmpty()) {
            return 0.0;
        }
        int hit = 0;
        int total = 0;
        for (Hypocenter h : incoming) {
            String k = eventKey(h);
            if (k == null) {
                continue;
            }
            total++;
            if (have.contains(k)) {
                hit++;
            }
        }
        return total == 0 ? 0.0 : hit / (double) total;
    }

    private static boolean containsIgnoreCase(Collection<String> names, String name) {
        for (String n : names) {
            if (n != null && n.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
