package com.treloc.xtreloc.solver;

import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;

/**
 * Point class that holds hypocenter data.
 * Implements Clusterable for DBSCAN clustering.
 */
public class Point implements Clusterable {
    private String time;
    private double lat;
    private double lon;
    private double dep;
    private double elat;
    private double elon;
    private double edep;
    private double res;
    private String filePath;
    private String type;
    private int cid;
    private double[][] lagTable;
    private int[] usedIdx;
    private double[] residual; // Residuals for each differential travel time data

    public Point(String time, double lat, double lon, double dep,
                 double elat, double elon, double edep, double res,
                 String filePath, String type, int cid) {
        this.time = time;
        this.lat = lat;
        this.lon = lon;
        this.dep = dep;
        this.elat = elat;
        this.elon = elon;
        this.edep = edep;
        this.res = res;
        this.filePath = filePath;
        this.type = type;
        this.cid = cid;
    }

    public String getTime() { return time; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getDep() { return dep; }
    public double getElat() { return elat; }
    public double getElon() { return elon; }
    public double getEdep() { return edep; }
    public double getRes() { return res; }
    public String getFilePath() { return filePath; }
    public String getType() { return type; }
    public int getCid() { return cid; }
    public double[][] getLagTable() { return lagTable; }
    public int[] getUsedIdx() { return usedIdx; }
    public double[] getResidual() { return residual; }
    
    /**
     * Returns the point coordinates as a double array [lat, lon].
     * Used for distance calculations in clustering.
     * Implements Clusterable interface.
     * 
     * @return array containing [lat, lon]
     */
    @Override
    public double[] getPoint() {
        return new double[]{lat, lon};
    }
    
    /**
     * Returns the hypocenter coordinates as a RealVector [lon, lat, dep].
     * Used for matrix operations in triple difference calculations.
     * 
     * @return RealVector containing [lon, lat, dep]
     */
    public RealVector getVector() {
        return new ArrayRealVector(new double[]{lon, lat, dep});
    }

    public void setTime(String time) { this.time = time; }
    public void setLat(double lat) { this.lat = lat; }
    public void setLon(double lon) { this.lon = lon; }
    public void setDep(double dep) { this.dep = dep; }
    public void setElat(double elat) { this.elat = elat; }
    public void setElon(double elon) { this.elon = elon; }
    public void setEdep(double edep) { this.edep = edep; }
    public void setRes(double res) { this.res = res; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setType(String type) { this.type = type; }
    public void setCid(int cid) { this.cid = cid; }
    public void setLagTable(double[][] lagTable) { this.lagTable = lagTable; }
    public void setUsedIdx(int[] usedIdx) { this.usedIdx = usedIdx; }
    public void setResidual(double[] residual) { this.residual = residual; }
}
