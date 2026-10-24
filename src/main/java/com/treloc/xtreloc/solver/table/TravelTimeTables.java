package com.treloc.xtreloc.solver.table;

import com.treloc.xtreloc.solver.grid.GridTravelTimeTable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/** Detection and loading of NonLinLoc {@code *.time.hdr} travel-time grids. */
public final class TravelTimeTables {

    private static final ConcurrentHashMap<String, TravelTimeTable> CACHE = new ConcurrentHashMap<>();

    private TravelTimeTables() {
    }

    /** True if {@code spec} names a travel-time grid instead of a 1-D velocity model. */
    public static boolean isTableSpec(String spec) {
        return GridTravelTimeTable.isGridSpec(spec);
    }

    /**
     * Loads a grid set. The same specification reuses one in-memory table for the process.
     */
    public static TravelTimeTable load(String spec) throws IOException {
        String key = canon(spec);
        TravelTimeTable cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (!GridTravelTimeTable.isGridSpec(spec)) {
            throw new IOException("Not a travel-time grid specification: " + spec);
        }
        TravelTimeTable loaded = GridTravelTimeTable.load(spec);
        TravelTimeTable raced = CACHE.putIfAbsent(key, loaded);
        return raced != null ? raced : loaded;
    }

    private static String canon(String spec) {
        if (spec == null || spec.isBlank()) {
            return "";
        }
        String t = spec.trim();
        try {
            Path p = Paths.get(t);
            if (Files.exists(p)) {
                return p.toRealPath().toString();
            }
        } catch (Exception ignored) {
            // keep the trimmed spec
        }
        return t;
    }
}
