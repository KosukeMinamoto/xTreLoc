package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ResidualPlotPanelTest {

    @Test
    public void polylineKeepsOnlyTheMostRecentPoints() {
        ResidualPlotPanel.Polyline p = new ResidualPlotPanel.Polyline("r", java.awt.Color.BLUE, false);
        for (int i = 0; i < 20; i++) {
            p.add(i, i * 0.1, 8);
        }
        assertEquals(8, p.n);
        float[] x = new float[16];
        float[] y = new float[16];
        int n = p.copy(x, y);
        assertEquals(8, n);
        assertEquals(12f, x[0], 0f);
        assertEquals(19f, x[7], 0f);
    }

    @Test
    public void setModeDoesNotRequireThePanelToBeShowing() {
        ResidualPlotPanel panel = new ResidualPlotPanel();
        panel.setMode("GRD");
        panel.setMode("MCMC");
        panel.setMode("CLS");
        panel.setMode("LMO");
    }
}
