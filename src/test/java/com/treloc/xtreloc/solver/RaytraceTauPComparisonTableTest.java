package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RaytraceTauPComparisonTableTest {

    @Test
    public void markdownReportContainsExpectedTitle() throws Exception {
        String md = RaytraceTauPComparisonTable.buildMarkdownReport();
        assertTrue(md.contains("# Raytrace1D vs TauP"));
    }
}
