package com.treloc.xtreloc.io;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Resolves per-mode configuration with fallback from another mode.
 * Used so that each mode has its own I/O and parameters; when a value is missing,
 * it is copied from a fallback mode (e.g. GRD) for consistent behaviour on mode switch.
 */
public final class ModeConfigResolver {

    private static final String DEFAULT_FALLBACK_MODE = "GRD";

    private ModeConfigResolver() {
    }

    /**
     * Returns the effective ModeConfig for the given mode, with null fields filled
     * from the fallback mode. If the mode has no entry, returns a copy of the fallback
     * mode's config. If both are missing, returns null.
     *
     * @param config       root config
     * @param mode         target mode (e.g. "LMO")
     * @param fallbackMode fallback mode for missing values (e.g. "GRD"); if null uses {@link #DEFAULT_FALLBACK_MODE}
     * @return merged ModeConfig, or null if neither mode nor fallback exists
     */
    public static AppConfig.ModeConfig getModeConfigWithFallback(
            AppConfig config,
            String mode,
            String fallbackMode) {
        if (config == null || config.getModes() == null) {
            return null;
        }
        Map<String, AppConfig.ModeConfig> modes = config.getModes();
        String fb = fallbackMode != null ? fallbackMode : DEFAULT_FALLBACK_MODE;
        AppConfig.ModeConfig primary = modes.get(mode);
        AppConfig.ModeConfig fallback = modes.get(fb);

        if (primary == null && fallback == null) {
            return null;
        }
        if (primary == null) {
            return copyModeConfig(fallback);
        }
        if (fallback == null) {
            return copyModeConfig(primary);
        }
        return mergeWithFallback(primary, fallback);
    }

    /**
     * Same as {@link #getModeConfigWithFallback(AppConfig, String, String)} with fallback = GRD.
     */
    public static AppConfig.ModeConfig getModeConfigWithFallback(AppConfig config, String mode) {
        return getModeConfigWithFallback(config, mode, DEFAULT_FALLBACK_MODE);
    }

    /**
     * Returns the solver JsonNode for the given mode, or from fallback mode if missing.
     */
    public static JsonNode getSolverWithFallback(
            AppConfig config,
            String mode,
            String fallbackMode) {
        if (config == null || config.getParams() == null) {
            return null;
        }
        String fb = fallbackMode != null ? fallbackMode : DEFAULT_FALLBACK_MODE;
        JsonNode node = config.getParams().get(mode);
        if (node != null) {
            return node;
        }
        return config.getParams().get(fb);
    }

    public static JsonNode getSolverWithFallback(AppConfig config, String mode) {
        return getSolverWithFallback(config, mode, DEFAULT_FALLBACK_MODE);
    }

    private static AppConfig.ModeConfig copyModeConfig(AppConfig.ModeConfig src) {
        if (src == null) return null;
        AppConfig.ModeConfig copy = new AppConfig.ModeConfig();
        copy.datDirectory = src.datDirectory;
        copy.outDirectory = src.outDirectory;
        copy.catalogFile = src.catalogFile;
        copy.randomSeed = src.randomSeed;
        copy.phsErr = src.phsErr;
        copy.locErr = src.locErr;
        copy.minSelectRate = src.minSelectRate;
        copy.maxSelectRate = src.maxSelectRate;
        copy.minPts = src.minPts;
        copy.eps = src.eps;
        copy.epsPercentile = src.epsPercentile;
        copy.useBinaryFormat = src.useBinaryFormat;
        copy.rmsThreshold = src.rmsThreshold;
        copy.locErrThreshold = src.locErrThreshold;
        copy.doClustering = src.doClustering;
        copy.calcTripleDiff = src.calcTripleDiff;
        copy.maxTripleDiffCount = src.maxTripleDiffCount;
        return copy;
    }

    private static AppConfig.ModeConfig mergeWithFallback(
            AppConfig.ModeConfig primary,
            AppConfig.ModeConfig fallback) {
        AppConfig.ModeConfig out = copyModeConfig(primary);
        if (out.datDirectory == null) out.datDirectory = fallback.datDirectory;
        if (out.outDirectory == null) out.outDirectory = fallback.outDirectory;
        if (out.catalogFile == null) out.catalogFile = fallback.catalogFile;
        if (out.randomSeed == null) out.randomSeed = fallback.randomSeed;
        if (out.phsErr == null) out.phsErr = fallback.phsErr;
        if (out.locErr == null) out.locErr = fallback.locErr;
        if (out.minSelectRate == null) out.minSelectRate = fallback.minSelectRate;
        if (out.maxSelectRate == null) out.maxSelectRate = fallback.maxSelectRate;
        if (out.minPts == null) out.minPts = fallback.minPts;
        if (out.eps == null) out.eps = fallback.eps;
        if (out.epsPercentile == null) out.epsPercentile = fallback.epsPercentile;
        if (out.useBinaryFormat == null) out.useBinaryFormat = fallback.useBinaryFormat;
        if (out.rmsThreshold == null) out.rmsThreshold = fallback.rmsThreshold;
        if (out.locErrThreshold == null) out.locErrThreshold = fallback.locErrThreshold;
        if (out.doClustering == null) out.doClustering = fallback.doClustering;
        if (out.calcTripleDiff == null) out.calcTripleDiff = fallback.calcTripleDiff;
        if (out.maxTripleDiffCount == null) out.maxTripleDiffCount = fallback.maxTripleDiffCount;
        return out;
    }
}
