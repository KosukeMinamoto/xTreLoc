package com.treloc.xtreloc.app.gui.service;

import java.util.Date;

/**
 * Represents an arrival time with associated metadata.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class ArrivalTime {
    private String stationName;
    private String instrument;
    private String component;
    private String pPhaseOnset;
    private String phaseDescriptor;
    private String firstMotion;
    private Date arrivalTime;
    private String err;
    private float errMag;
    private float codaDuration;
    private float amplitude;
    private float period;
    private float priorWt;

    public ArrivalTime(String stationName, String component, String phase, Date arrivalTime) {
        this.stationName = stationName;
        this.component = component;
        this.phaseDescriptor = phase;
        this.arrivalTime = arrivalTime;
        this.instrument = "?";
        this.pPhaseOnset = "?";
        this.firstMotion = "?";
        this.err = "GAU";
        this.errMag = -1.0f;
        this.codaDuration = -1.0f;
        this.amplitude = -1.0f;
        this.period = -1.0f;
        this.priorWt = -1.0f;
    }

    public String getInstrument() { return instrument; }
    public void setInstrument(String instrument) { this.instrument = instrument; }

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public String getPPhaseOnset() { return pPhaseOnset; }
    public void setPPhaseOnset(String pPhaseOnset) { this.pPhaseOnset = pPhaseOnset; }

    public String getPhaseDescriptor() { return phaseDescriptor; }
    public void setPhaseDescriptor(String phaseDescriptor) { this.phaseDescriptor = phaseDescriptor; }

    public String getFirstMotion() { return firstMotion; }
    public void setFirstMotion(String firstMotion) { this.firstMotion = firstMotion; }

    public String getErr() { return err; }
    public void setErr(String err) { this.err = err; }

    public float getErrMag() { return errMag; }
    public void setErrMag(float errMag) { this.errMag = errMag; }

    public float getCodaDuration() { return codaDuration; }
    public void setCodaDuration(float codaDuration) { this.codaDuration = codaDuration; }

    public float getAmplitude() { return amplitude; }
    public void setAmplitude(float amplitude) { this.amplitude = amplitude; }

    public float getPeriod() { return period; }
    public void setPeriod(float period) { this.period = period; }

    public float getPriorWt() { return priorWt; }
    public void setPriorWt(float priorWt) { this.priorWt = priorWt; }

    public String getStationName() { return stationName; }

    public Date getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(Date arrivalTime) { this.arrivalTime = arrivalTime; }

    @Override
    public String toString() {
        return "Station: " + stationName + ", Phase: " + phaseDescriptor + ", Arrival Time: " + arrivalTime;
    }
}
