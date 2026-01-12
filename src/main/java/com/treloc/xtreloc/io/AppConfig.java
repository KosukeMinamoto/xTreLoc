package com.treloc.xtreloc.io;

import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Root configuration mapped from config.json
 * No logic here.
 */
public class AppConfig {

    /* ---------- general ---------- */
    public String logLevel = "INFO";
    public int numJobs = 1;

    /* ---------- common ---------- */
    public String stationFile;
    public String taupFile;
    public double hypBottom = 100.0;
    public double threshold = 0.0;

    /* ---------- per mode ---------- */
    public Map<String, ModeConfig> modes;

    /* ---------- solver specific ---------- */
    public Map<String, JsonNode> solver;

    /* ---------- nested ---------- */
    public static class ModeConfig {
        @JsonDeserialize(using = PathDeserializer.class)
        public Path datDirectory;
        @JsonDeserialize(using = PathDeserializer.class)
        public Path outDirectory;
        public String catalogFile;
        
        // SYN mode specific parameters
        public Integer randomSeed;
        public Double phsErr;
        public Double locErr;
        public Double minSelectRate;
        public Double maxSelectRate;
        
        // CLS mode specific parameters
        public Integer minPts;
        public Double eps;
        public Double epsPercentile; // Data inclusion rate when eps < 0 (0-1, empty means use elbow method)
        public Boolean useBinaryFormat;
        public Double rmsThreshold; // RMS threshold for catalog filtering (null means no filtering)
        public Double locErrThreshold; // Location error threshold for catalog filtering (xerr and yerr must both be <= this value, null means no filtering)
    }
    
    /* ---------- solver specific parameters ---------- */
    // LMO mode LM optimization parameters are stored in solver.LMO JsonNode
}
