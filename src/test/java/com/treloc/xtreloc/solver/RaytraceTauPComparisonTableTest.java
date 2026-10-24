package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RaytraceTauPComparisonTableTest {

    @Test
    public void markdownReportContainsExpectedTitle() {
        assertTrue(RaytraceTauPComparisonTable.REPORT_TITLE.contains("# Raytrace1D vs TauP"));
    }
}
