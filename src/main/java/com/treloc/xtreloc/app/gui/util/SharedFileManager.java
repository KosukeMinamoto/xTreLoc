package com.treloc.xtreloc.app.gui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared selection for the Model / Solver / Map tabs: station file, travel-time reference
 * ({@code taupFile}: a 1-D model or a travel-time table) and the geographic range of the layer cake.
 */
public class SharedFileManager {
    private static SharedFileManager instance;

    private File stationFile;
    private String taupFile;
    private Geometry geometry = Geometry.unset();
    private final List<StationFileListener> stationFileListeners = new ArrayList<>();
    private final List<TaupFileListener> taupFileListeners = new ArrayList<>();
    private final List<Runnable> geometryListeners = new ArrayList<>();
    private final List<Consumer<Boolean>> computationBusyListeners = new ArrayList<>();
    private final List<Runnable> plotFilterListeners = new ArrayList<>();
    private final Set<String> travelTimeGridLabels = new LinkedHashSet<>();
    private boolean plotOnlyGridStations = true;
    private boolean computationBusy;

    private SharedFileManager() {
    }

    public static SharedFileManager getInstance() {
        if (instance == null) {
            instance = new SharedFileManager();
        }
        return instance;
    }

    public File getStationFile() {
        return stationFile;
    }

    public void setStationFile(File file) {
        this.stationFile = file;
        for (StationFileListener listener : stationFileListeners) {
            listener.onStationFileChanged(file);
        }
    }

    public void addStationFileListener(StationFileListener listener) {
        stationFileListeners.add(listener);
    }

    public void removeStationFileListener(StationFileListener listener) {
        stationFileListeners.remove(listener);
    }

    public String getTaupFile() {
        return taupFile;
    }

    public void setTaupFile(String file) {
        this.taupFile = file;
        for (TaupFileListener listener : taupFileListeners) {
            listener.onTaupFileChanged(file);
        }
    }

    public void addTaupFileListener(TaupFileListener listener) {
        taupFileListeners.add(listener);
    }

    public void removeTaupFileListener(TaupFileListener listener) {
        taupFileListeners.remove(listener);
    }

    public Geometry getGeometry() {
        return geometry.copy();
    }

    public void setGeometry(Geometry g) {
        this.geometry = g == null ? Geometry.unset() : g.copy();
        for (Runnable r : new ArrayList<>(geometryListeners)) {
            r.run();
        }
    }

    public void addGeometryListener(Runnable listener) {
        if (listener != null) {
            geometryListeners.add(listener);
        }
    }

    public boolean isComputationBusy() {
        return computationBusy;
    }

    /** Locks Model / Solver inputs while travel-time build or location is running. */
    public void setComputationBusy(boolean busy) {
        if (this.computationBusy == busy) {
            return;
        }
        this.computationBusy = busy;
        for (Consumer<Boolean> listener : new ArrayList<>(computationBusyListeners)) {
            listener.accept(busy);
        }
    }

    public void addComputationBusyListener(Consumer<Boolean> listener) {
        if (listener != null) {
            computationBusyListeners.add(listener);
        }
    }

    /**
     * Station codes (uppercase) that have a complete travel-time grid in the loaded table.
     * Empty when no table is loaded.
     */
    public Set<String> getTravelTimeGridLabels() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(travelTimeGridLabels));
    }

    public void setTravelTimeGridLabels(Collection<String> labels) {
        travelTimeGridLabels.clear();
        if (labels != null) {
            for (String s : labels) {
                if (s != null && !s.isBlank()) {
                    travelTimeGridLabels.add(s.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        firePlotFilter();
    }

    public boolean isPlotOnlyGridStations() {
        return plotOnlyGridStations;
    }

    public void setPlotOnlyGridStations(boolean on) {
        if (this.plotOnlyGridStations == on) {
            return;
        }
        this.plotOnlyGridStations = on;
        firePlotFilter();
    }

    /** When a table is loaded and the filter is on, only those stations are drawn. */
    public boolean shouldPlotStation(String code) {
        if (!plotOnlyGridStations || travelTimeGridLabels.isEmpty()) {
            return true;
        }
        return code != null && travelTimeGridLabels.contains(code.trim().toUpperCase(Locale.ROOT));
    }

    public void addPlotFilterListener(Runnable listener) {
        if (listener != null) {
            plotFilterListeners.add(listener);
        }
    }

    private void firePlotFilter() {
        for (Runnable r : new ArrayList<>(plotFilterListeners)) {
            r.run();
        }
    }

    public interface StationFileListener {
        void onStationFileChanged(File file);
    }

    public interface TaupFileListener {
        void onTaupFileChanged(String file);
    }

    /** Layer-cake / location region. {@link #maxDepthKm} is also the Solver depth limit ({@code hypBottom}).
     * {@link #spacingKm} is the horizontal travel-time grid spacing (NonLinLoc {@code dx = dy});
     * {@link #spacingDepthKm} is {@code dz}. */
    public static final class Geometry {
        public double lonMin;
        public double lonMax;
        public double latMin;
        public double latMax;
        public double minDepthKm;
        public double maxDepthKm;
        public double spacingKm;
        public double spacingDepthKm;
        public double strikeDeg;
        public double dipDeg;

        public static Geometry defaults() {
            Geometry g = new Geometry();
            g.lonMin = 138.0;
            g.lonMax = 145.0;
            g.latMin = 34.0;
            g.latMax = 42.0;
            g.minDepthKm = 0.0;
            g.maxDepthKm = 100.0;
            g.spacingKm = 2.0;
            g.spacingDepthKm = 2.0;
            return g;
        }

        /** No lon/lat box yet. Depth / spacing keep solver-friendly defaults until auto-fill. */
        public static Geometry unset() {
            Geometry g = new Geometry();
            g.minDepthKm = 0.0;
            g.maxDepthKm = 100.0;
            g.spacingKm = 2.0;
            g.spacingDepthKm = 2.0;
            return g;
        }

        public boolean hasRegion() {
            return lonMax > lonMin && latMax > latMin && maxDepthKm > minDepthKm;
        }

        /** Horizontal spacing (km); falls back to 2 km. */
        public double spacingHoriz() {
            return spacingKm > 0 ? spacingKm : 2.0;
        }

        /** Depth spacing (km); falls back to the horizontal spacing (legacy single-spacing sessions). */
        public double spacingDepth() {
            return spacingDepthKm > 0 ? spacingDepthKm : spacingHoriz();
        }

        public Geometry copy() {
            Geometry g = new Geometry();
            g.lonMin = lonMin;
            g.lonMax = lonMax;
            g.latMin = latMin;
            g.latMax = latMax;
            g.minDepthKm = minDepthKm;
            g.maxDepthKm = maxDepthKm;
            g.spacingKm = spacingKm;
            g.spacingDepthKm = spacingDepthKm;
            g.strikeDeg = strikeDeg;
            g.dipDeg = dipDeg;
            return g;
        }
    }
}
