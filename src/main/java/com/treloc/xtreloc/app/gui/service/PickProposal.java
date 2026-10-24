package com.treloc.xtreloc.app.gui.service;

import java.util.Date;

/**
 * An automatic pick that has not been accepted yet. It is shown over the waveforms and only becomes a
 * pick when the user applies it.
 *
 * @param station   station code (without spaces)
 * @param component component the time was measured on
 * @param phase     {@code "P"} or {@code "S"}
 * @param time      proposed arrival time
 * @param method    name of the method that produced it (for example "AIC")
 * @param score     confidence in 0..1 when the method provides one, otherwise {@code NaN}
 */
public record PickProposal(String station, String component, String phase, Date time, String method,
                           double score) {

    public PickProposal(String station, String component, String phase, Date time, String method) {
        this(station, component, phase, time, method, Double.NaN);
    }
}
