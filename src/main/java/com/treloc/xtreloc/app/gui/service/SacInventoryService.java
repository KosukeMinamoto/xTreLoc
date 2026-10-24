package com.treloc.xtreloc.app.gui.service;

import edu.sc.seis.seisFile.sac.SacHeader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads SAC files in one directory and builds a per-station inventory
 * (components, sampling rate, gaps) so CF / envelope can pick H1 / H2 explicitly.
 */
public final class SacInventoryService {

    private static final Logger LOG = Logger.getLogger(SacInventoryService.class.getName());
    private static final float UNDEF = -12345f;

    private SacInventoryService() {}

    public static final class ComponentInfo {
        public final String name;
        public final Path path;
        public final double deltaSec;
        public final int npts;
        public final boolean gaps;
        public final SacComponentClassifier.Role role;

        public ComponentInfo(String name, Path path, double deltaSec, int npts, boolean gaps,
                             SacComponentClassifier.Role role) {
            this.name = name;
            this.path = path;
            this.deltaSec = deltaSec;
            this.npts = npts;
            this.gaps = gaps;
            this.role = role;
        }
    }

    public static final class StationRow {
        public final String station;
        public final List<ComponentInfo> components;
        public final Set<String> selected = new LinkedHashSet<>();
        public String ud = "";
        public String h1 = "";
        public String h2 = "";
        public boolean use = true;

        public StationRow(String station, List<ComponentInfo> components) {
            this.station = station;
            this.components = components;
            assignDefaults();
        }

        public boolean hasComponent(String name) {
            return find(name) != null;
        }

        public boolean usesComponent(String name, Set<String> disabledColumns) {
            if (name == null || name.isEmpty() || !selected.contains(name)) {
                return false;
            }
            return disabledColumns == null || !disabledColumns.contains(name);
        }

        public Path pathForRole(SacComponentClassifier.Role role, Set<String> disabledColumns) {
            if (role == null) {
                return null;
            }
            for (ComponentInfo c : components) {
                if (c.role == role && usesComponent(c.name, disabledColumns)) {
                    return c.path;
                }
            }
            return null;
        }

        public List<String> componentNames() {
            List<String> names = new ArrayList<>();
            for (ComponentInfo c : components) {
                names.add(c.name);
            }
            return names;
        }

        public String componentList() {
            return String.join(" ", componentNames());
        }

        public boolean mixedRate() {
            if (components.size() < 2) {
                return false;
            }
            double d0 = components.get(0).deltaSec;
            for (ComponentInfo c : components) {
                if (Math.abs(c.deltaSec - d0) > 1e-8) {
                    return true;
                }
            }
            return false;
        }

        public String rateText() {
            if (components.isEmpty()) {
                return "";
            }
            if (mixedRate()) {
                return "mixed";
            }
            double dt = components.get(0).deltaSec;
            if (dt <= 0) {
                return "?";
            }
            return String.format(Locale.US, "%.3f Hz", 1.0 / dt);
        }

        public String gapsText() {
            int n = 0;
            for (ComponentInfo c : components) {
                if (c.gaps) {
                    n++;
                }
            }
            if (n == 0) {
                return "";
            }
            return n + "/" + components.size();
        }

        public Path pathFor(String componentName) {
            if (componentName == null || componentName.isEmpty()) {
                return null;
            }
            for (ComponentInfo c : components) {
                if (c.name.equalsIgnoreCase(componentName.trim())) {
                    return c.path;
                }
            }
            return null;
        }

        private ComponentInfo find(String name) {
            if (name == null) {
                return null;
            }
            for (ComponentInfo c : components) {
                if (c.name.equalsIgnoreCase(name.trim())) {
                    return c;
                }
            }
            return null;
        }

        private void assignDefaults() {
            selected.clear();
            String firstH = "";
            String secondH = "";
            for (ComponentInfo c : components) {
                selected.add(c.name);
                if (c.role == SacComponentClassifier.Role.VERTICAL && ud.isEmpty()) {
                    ud = c.name;
                } else if (c.role == SacComponentClassifier.Role.H1 && h1.isEmpty()) {
                    h1 = c.name;
                } else if (c.role == SacComponentClassifier.Role.H2 && h2.isEmpty()) {
                    h2 = c.name;
                } else if (c.role == SacComponentClassifier.Role.HORIZONTAL
                    || c.role == SacComponentClassifier.Role.H1
                    || c.role == SacComponentClassifier.Role.H2) {
                    if (firstH.isEmpty()) {
                        firstH = c.name;
                    } else if (secondH.isEmpty() && !c.name.equals(firstH)) {
                        secondH = c.name;
                    }
                }
            }
            if (h1.isEmpty()) {
                h1 = firstH;
            }
            if (h2.isEmpty()) {
                h2 = secondH;
            }
            use = !components.isEmpty();
        }
    }

    /**
     * Unique {@code KCMPNM} values across {@code rows}, vertical first, then H1, H2.
     */
    public static List<String> uniqueComponentNames(List<StationRow> rows) {
        Map<String, Integer> rank = new LinkedHashMap<>();
        if (rows != null) {
            for (StationRow row : rows) {
                for (ComponentInfo c : row.components) {
                    rank.putIfAbsent(c.name, roleRank(c.role));
                }
            }
        }
        List<String> names = new ArrayList<>(rank.keySet());
        names.sort(Comparator
            .comparingInt((String n) -> rank.getOrDefault(n, 9))
            .thenComparing(String::compareToIgnoreCase));
        return names;
    }

    private static int roleRank(SacComponentClassifier.Role role) {
        if (role == SacComponentClassifier.Role.VERTICAL) {
            return 0;
        }
        if (role == SacComponentClassifier.Role.H1) {
            return 1;
        }
        if (role == SacComponentClassifier.Role.H2) {
            return 2;
        }
        if (role == SacComponentClassifier.Role.HORIZONTAL) {
            return 3;
        }
        return 4;
    }

    /**
     * Direct {@code .sac} children of {@code dir} (no recursion).
     */
    public static List<StationRow> scan(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(p) && (name.endsWith(".sac") || name.endsWith(".s"))) {
                    files.add(p);
                }
            }
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

        Map<String, List<ComponentInfo>> byStation = new LinkedHashMap<>();
        for (Path p : files) {
            try {
                SacHeader h = WaveProcessor.readHeader(p.toString());
                String st = h.getKstnm() == null ? "" : h.getKstnm().trim().replace(" ", "");
                if (st.isEmpty()) {
                    st = p.getFileName().toString();
                }
                String cmp = h.getKcmpnm() == null ? "?" : h.getKcmpnm().trim();
                if (cmp.isEmpty()) {
                    cmp = "?";
                }
                double dt = h.getDelta();
                if (dt <= 0) {
                    dt = 0.01;
                }
                int npts = h.getNpts();
                boolean gaps = headerLengthMismatch(h, npts, dt);
                ComponentInfo info = new ComponentInfo(cmp, p, dt, npts, gaps,
                    SacComponentClassifier.classify(cmp));
                byStation.computeIfAbsent(st, k -> new ArrayList<>()).add(info);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Skip SAC " + p + ": " + ex.getMessage(), ex);
            }
        }
        List<StationRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<ComponentInfo>> e : byStation.entrySet()) {
            rows.add(new StationRow(e.getKey(), e.getValue()));
        }
        return rows;
    }

    /**
     * Header-only length check ({@code NPTS} vs {@code (E-B)/delta}).
     * Sample-value gaps are not scanned here so day-long continuous SAC stay cheap to list.
     */
    static boolean headerLengthMismatch(SacHeader h, int npts, double deltaSec) {
        if (npts <= 0) {
            return true;
        }
        float b = h.getB();
        float e = h.getE();
        if (!isUndef(b) && !isUndef(e) && e > b && deltaSec > 0) {
            int expected = (int) Math.round((e - b) / deltaSec) + 1;
            return Math.abs(expected - npts) > 2;
        }
        return false;
    }

    private static boolean isUndef(float v) {
        return Float.isNaN(v) || Math.abs(v + 12345f) < 1f || v == UNDEF;
    }
}
