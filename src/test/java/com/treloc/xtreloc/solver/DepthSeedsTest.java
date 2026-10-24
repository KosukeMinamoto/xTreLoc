package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DepthSeedsTest {

    @Test
    public void defaultThreeSeedsWhenConfigOmitsDepth() {
        double[] seeds = HypoUtils.readDepthSeeds(null, 0.0, 0.0, 100.0);
        assertArrayEquals(new double[] { 1.0, 5.0, 10.0 }, seeds, 1e-12);
    }

    @Test
    public void commaTextAndDeepSeedClampedToHypBottom() {
        ObjectNode solver = JsonNodeFactory.instance.objectNode();
        solver.put("depthSeeds", "1,5,10,100");
        double[] seeds = HypoUtils.readDepthSeeds(solver, 0.0, 0.0, 40.0);
        assertArrayEquals(new double[] { 1.0, 5.0, 10.0, 40.0 }, seeds, 1e-12);
    }

    @Test
    public void catalogHeaderIsIncludedAndDeduped() {
        ObjectNode solver = JsonNodeFactory.instance.objectNode();
        solver.put("depthSeeds", "1,5,10");
        double[] seeds = HypoUtils.readDepthSeeds(solver, 4.2, 0.0, 100.0);
        assertArrayEquals(new double[] { 1.0, 4.2, 5.0, 10.0 }, seeds, 1e-12);
    }

    @Test
    public void legacyDefaultDepthKmIsASingleSeed() {
        ObjectNode solver = JsonNodeFactory.instance.objectNode();
        solver.put("defaultDepthKm", 8.0);
        double[] seeds = HypoUtils.readDepthSeeds(solver, 0.0, 0.0, 100.0);
        assertArrayEquals(new double[] { 8.0 }, seeds, 1e-12);
    }

    @Test
    public void invalidTokensAreSkipped() {
        List<Double> raw = new ArrayList<>();
        HypoUtils.addDepthSeedTokens("1, foo, 5", raw);
        assertEquals(Arrays.asList(1.0, 5.0), raw);
    }
}
