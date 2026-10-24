package com.treloc.xtreloc.app.gui.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.treloc.xtreloc.util.JsonMapperHolder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
/**
 * JSON-serializable settings for hypotd-style batch processing (per date-directory SAC roots).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HypotdBatchConfig {

    /** Parent directory whose immediate subfolders are event roots (each containing SAC recursively). */
    private String inputRoot = "";
    /** Parent directory for {@code CF/<event>/} and correlation lag tables ({@code *.dat}). */
    private String outputParent = "";
    private String stationCoordPath = "";
    /**
     * Station list file (e.g. solver format: code lat lon dep pc sc per line). Used for CC pair distance
     * filtering and optional channel mapping. When set, takes precedence over {@link #stationCoordPath} for coordinates.
     */
    private String stationFile = "";
    /** {@code RAW}, {@code ENVELOPE} or {@code CHARACTERISTIC}. */
    private String signalKind = "CHARACTERISTIC";

    private Double filterFreqMin;
    private Double filterFreqMax;

    private double eigWindowSec = 2.0;
    private double staWindowSec = 0.5;
    private double ltaWindowSec = 10.0;
    private double ccWindowSec = 10.0;
    private double ccOverlapRate = 0.25;
    private double ccMaxDistKm = 300.0;
    /** Minimum |coef|; null → 0 (no gate). */
    private Double ccCoefThreshold;
    /** Minimum qualifying pairs per window to emit .dat; null → no minimum (all windows). */
    private Integer ccMinPairCount;

    private String cfSubdir = "CF";
    private String correlationSubdir = CrossCorrelationPickingService.DEFAULT_CORRELATION_SUBDIR;
    /** Skip a day when CF already has {@code *_CF.sac} or Correlation already has {@code *.dat}. */
    private boolean skipExisting = true;

    public static HypotdBatchConfig readJson(Path path) throws IOException {
        return JsonMapperHolder.getMapper().readValue(path.toFile(), HypotdBatchConfig.class);
    }

    public void writeJson(Path path) throws IOException {
        JsonMapperHolder.getMapper().writeValue(path.toFile(), this);
    }

    public CrossCorrelationPickingService.Params toServiceParams() {
        CrossCorrelationPickingService.Params p = new CrossCorrelationPickingService.Params();
        if ("ENVELOPE".equalsIgnoreCase(signalKind)) {
            p.signalKind = CrossCorrelationPickingService.SignalKind.ENVELOPE;
        } else if ("RAW".equalsIgnoreCase(signalKind)) {
            p.signalKind = CrossCorrelationPickingService.SignalKind.RAW;
        } else {
            p.signalKind = CrossCorrelationPickingService.SignalKind.CHARACTERISTIC;
        }
        p.filterFreqMin = filterFreqMin == null ? null : filterFreqMin.floatValue();
        p.filterFreqMax = filterFreqMax == null ? null : filterFreqMax.floatValue();
        p.eigWindowSec = eigWindowSec;
        p.staWindowSec = staWindowSec;
        p.ltaWindowSec = ltaWindowSec;
        p.ccWindowSec = ccWindowSec;
        p.ccOverlapRate = ccOverlapRate;
        p.ccMaxDistKm = ccMaxDistKm;
        p.ccCoefThreshold = ccCoefThreshold != null ? ccCoefThreshold : 0.0;
        p.ccMinPairCount = ccMinPairCount;
        return p;
    }

    public void validatePaths() throws IOException {
        if (inputRoot == null || inputRoot.isBlank()) {
            throw new IOException("inputRoot is required");
        }
        if (outputParent == null || outputParent.isBlank()) {
            throw new IOException("outputParent is required");
        }
        Path in = Paths.get(inputRoot);
        if (!java.nio.file.Files.isDirectory(in)) {
            throw new IOException("inputRoot is not a directory: " + inputRoot);
        }
    }

    public String getInputRoot() {
        return inputRoot;
    }

    public void setInputRoot(String inputRoot) {
        this.inputRoot = inputRoot;
    }

    public String getOutputParent() {
        return outputParent;
    }

    public void setOutputParent(String outputParent) {
        this.outputParent = outputParent;
    }

    public String getStationCoordPath() {
        return stationCoordPath;
    }

    public void setStationCoordPath(String stationCoordPath) {
        this.stationCoordPath = stationCoordPath;
    }

    public String getStationFile() {
        return stationFile;
    }

    public void setStationFile(String stationFile) {
        this.stationFile = stationFile;
    }

    public String getSignalKind() {
        return signalKind;
    }

    public void setSignalKind(String signalKind) {
        this.signalKind = signalKind;
    }

    public Double getFilterFreqMin() {
        return filterFreqMin;
    }

    public void setFilterFreqMin(Double filterFreqMin) {
        this.filterFreqMin = filterFreqMin;
    }

    public Double getFilterFreqMax() {
        return filterFreqMax;
    }

    public void setFilterFreqMax(Double filterFreqMax) {
        this.filterFreqMax = filterFreqMax;
    }

    public double getEigWindowSec() {
        return eigWindowSec;
    }

    public void setEigWindowSec(double eigWindowSec) {
        this.eigWindowSec = eigWindowSec;
    }

    public double getStaWindowSec() {
        return staWindowSec;
    }

    public void setStaWindowSec(double staWindowSec) {
        this.staWindowSec = staWindowSec;
    }

    public double getLtaWindowSec() {
        return ltaWindowSec;
    }

    public void setLtaWindowSec(double ltaWindowSec) {
        this.ltaWindowSec = ltaWindowSec;
    }

    public double getCcWindowSec() {
        return ccWindowSec;
    }

    public void setCcWindowSec(double ccWindowSec) {
        this.ccWindowSec = ccWindowSec;
    }

    public double getCcOverlapRate() {
        return ccOverlapRate;
    }

    public void setCcOverlapRate(double ccOverlapRate) {
        this.ccOverlapRate = ccOverlapRate;
    }

    public double getCcMaxDistKm() {
        return ccMaxDistKm;
    }

    public void setCcMaxDistKm(double ccMaxDistKm) {
        this.ccMaxDistKm = ccMaxDistKm;
    }

    public Double getCcCoefThreshold() {
        return ccCoefThreshold;
    }

    public void setCcCoefThreshold(Double ccCoefThreshold) {
        this.ccCoefThreshold = ccCoefThreshold;
    }

    public Integer getCcMinPairCount() {
        return ccMinPairCount;
    }

    public void setCcMinPairCount(Integer ccMinPairCount) {
        this.ccMinPairCount = ccMinPairCount;
    }

    public String getCfSubdir() {
        return cfSubdir;
    }

    public void setCfSubdir(String cfSubdir) {
        this.cfSubdir = cfSubdir;
    }

    public String getCorrelationSubdir() {
        return correlationSubdir;
    }

    public void setCorrelationSubdir(String correlationSubdir) {
        this.correlationSubdir = correlationSubdir;
    }

    public boolean isSkipExisting() {
        return skipExisting;
    }

    public void setSkipExisting(boolean skipExisting) {
        this.skipExisting = skipExisting;
    }
}
