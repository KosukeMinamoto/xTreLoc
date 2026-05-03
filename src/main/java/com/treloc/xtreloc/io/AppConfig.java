package com.treloc.xtreloc.io;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Root configuration mapped from config.json.
 * Structure: "io" (I/O paths per mode), "params" (parameters per mode).
 * Use getModes() for a merged view (io + params) as ModeConfig.
 */
public class AppConfig {

    /** Default SYN mode: random seed */
    public static final int DEFAULT_SYN_RANDOM_SEED = 100;
    /** Default SYN mode: phase error (s) */
    public static final double DEFAULT_SYN_PHS_ERR = 0.1;
    /** Default SYN mode: location error (km) */
    public static final double DEFAULT_SYN_LOC_ERR = 0.03;
    /** Default SYN mode: min select rate */
    public static final double DEFAULT_SYN_MIN_SELECT_RATE = 0.2;
    /** Default SYN mode: max select rate */
    public static final double DEFAULT_SYN_MAX_SELECT_RATE = 0.4;

    /* ---------- general ---------- */
    public String logLevel = "INFO";
    public int numJobs = 1;

    /* ---------- common ---------- */
    public String stationFile;
    public String taupFile;
    /**
     * Travel-time engine: {@code layered} (default, {@link com.treloc.xtreloc.solver.Raytrace1D}) or
     * {@code taup} (spherical TauP, same velocity file when supported by TauModelLoader).
     * The synonym {@code legacy} is accepted when reading configs for compatibility.
     */
    public String raytraceMethod = "layered";
    public double hypBottom = 100.0;
    public double threshold = 0.0;

    /** I/O paths per mode (datDirectory, outDirectory, catalogFile). */
    public Map<String, ModeIOConfig> io;

    /** Parameters per mode (solver and mode-specific params). */
    public Map<String, JsonNode> params;

    public String getLogLevel() { return logLevel; }
    public int getNumJobs() { return numJobs; }
    public String getStationFile() { return stationFile; }
    public String getTaupFile() { return taupFile; }
    public String getRaytraceMethod() { return raytraceMethod; }
    public double getHypBottom() { return hypBottom; }
    public double getThreshold() { return threshold; }
    public Map<String, ModeIOConfig> getIO() { return io; }
    public Map<String, JsonNode> getParams() { return params; }

    /**
     * Returns merged mode config (io + params) per mode for backward compatibility.
     * Builds ModeConfig from io and params on each call.
     */
    public Map<String, ModeConfig> getModes() {
        Map<String, ModeConfig> merged = new HashMap<>();
        Set<String> modes = new HashSet<>();
        if (io != null) modes.addAll(io.keySet());
        if (params != null) modes.addAll(params.keySet());
        for (String mode : modes) {
            ModeConfig m = new ModeConfig();
            if (io != null && io.containsKey(mode)) {
                ModeIOConfig i = io.get(mode);
                m.datDirectory = i != null ? i.datDirectory : null;
                m.outDirectory = i != null ? i.outDirectory : null;
                m.catalogFile = i != null ? i.catalogFile : null;
            }
            if (params != null && params.containsKey(mode)) {
                applyParamsToModeConfig(params.get(mode), m, mode);
            }
            merged.put(mode, m);
        }
        return merged;
    }

    /** @deprecated Use getParams() */
    @Deprecated
    public Map<String, JsonNode> getSolver() { return params; }

    /**
     * Resolves relative paths in this config against the given base directory.
     * Paths that are already absolute are left unchanged.
     * Call this after loading config from a file, with the config file's parent directory as base.
     *
     * @param baseDir base directory (e.g. config file's parent); if null, no resolution is performed
     */
    public void resolveRelativePaths(Path baseDir) {
        if (baseDir == null) return;
        Path base = baseDir.normalize();

        if (stationFile != null && !stationFile.isEmpty() && !Paths.get(stationFile).isAbsolute()) {
            stationFile = base.resolve(stationFile).normalize().toString();
        }
        if (io != null) {
            for (Map.Entry<String, ModeIOConfig> e : io.entrySet()) {
                ModeIOConfig m = e.getValue();
                if (m == null) continue;
                if (m.datDirectory != null && !m.datDirectory.isAbsolute()) {
                    m.datDirectory = base.resolve(m.datDirectory).normalize();
                }
                if (m.outDirectory != null && !m.outDirectory.isAbsolute()) {
                    m.outDirectory = base.resolve(m.outDirectory).normalize();
                }
                if (m.catalogFile != null && !m.catalogFile.isEmpty() && !Paths.get(m.catalogFile).isAbsolute()) {
                    m.catalogFile = base.resolve(m.catalogFile).normalize().toString();
                }
            }
        }
    }

    private static void applyParamsToModeConfig(JsonNode p, ModeConfig m, String mode) {
        if (p == null) return;
        if ("SYN".equals(mode)) {
            if (p.has("randomSeed")) m.randomSeed = p.get("randomSeed").asInt();
            if (p.has("phsErr")) m.phsErr = p.get("phsErr").asDouble();
            if (p.has("locErr")) m.locErr = p.get("locErr").asDouble();
            if (p.has("minSelectRate")) m.minSelectRate = p.get("minSelectRate").asDouble();
            if (p.has("maxSelectRate")) m.maxSelectRate = p.get("maxSelectRate").asDouble();
        }
        if ("CLS".equals(mode)) {
            if (p.has("minPts")) m.minPts = p.get("minPts").asInt();
            if (p.has("eps")) m.eps = p.get("eps").asDouble();
            if (p.has("epsPercentile")) m.epsPercentile = p.get("epsPercentile").asDouble();
            if (p.has("rmsThreshold")) m.rmsThreshold = p.get("rmsThreshold").asDouble();
            if (p.has("locErrThreshold")) m.locErrThreshold = p.get("locErrThreshold").asDouble();
            if (p.has("useBinaryFormat")) m.useBinaryFormat = p.get("useBinaryFormat").asBoolean();
            if (p.has("doClustering")) m.doClustering = p.get("doClustering").asBoolean();
            if (p.has("calcTripleDiff")) m.calcTripleDiff = p.get("calcTripleDiff").asBoolean();
        }
        if ("TRD".equals(mode)) {
            if (p.has("maxTripleDiffCount")) m.maxTripleDiffCount = p.get("maxTripleDiffCount").asInt();
        }
    }

    /** I/O paths for one mode (used in config "io" section). */
    public static class ModeIOConfig {
        @JsonDeserialize(using = PathDeserializer.class)
        public Path datDirectory;
        @JsonDeserialize(using = PathDeserializer.class)
        public Path outDirectory;
        public String catalogFile;
    }

    /** Merged view: I/O + params for one mode (built from io + params). */
    public static class ModeConfig {
        public Path datDirectory;
        public Path outDirectory;
        public String catalogFile;

        // SYN (from params.SYN)
        public Integer randomSeed;
        public Double phsErr;
        public Double locErr;
        public Double minSelectRate;
        public Double maxSelectRate;

        // CLS (from params.CLS)
        public Integer minPts;
        public Double eps;
        public Double epsPercentile;
        public Boolean useBinaryFormat;
        public Double rmsThreshold;
        public Double locErrThreshold;
        public Boolean doClustering;
        public Boolean calcTripleDiff;

        // TRD (from params.TRD)
        /** Maximum number of triple-difference data to use per cluster (residual smallest first). Null = no limit. */
        public Integer maxTripleDiffCount;
    }
}
