package com.treloc.hypotd;

import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
// import org.apache.commons.math3.ml.clustering.Cluster;

public class CalcTripleDiff {
    private final AppConfig appConfig;
    
    public CalcTripleDiff(AppConfig config) {
        appConfig = config;
    }
}