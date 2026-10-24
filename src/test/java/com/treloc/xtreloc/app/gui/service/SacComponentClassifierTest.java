package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SacComponentClassifierTest {

    @Test
    public void classifiesVerticalAliases() {
        assertEquals(SacComponentClassifier.Role.VERTICAL, SacComponentClassifier.classify("HHZ"));
        assertEquals(SacComponentClassifier.Role.VERTICAL, SacComponentClassifier.classify("UD"));
        assertEquals(SacComponentClassifier.Role.VERTICAL, SacComponentClassifier.classify("V"));
        assertEquals(SacComponentClassifier.Role.VERTICAL, SacComponentClassifier.classify("BHZ"));
        assertEquals(SacComponentClassifier.Role.VERTICAL, SacComponentClassifier.classify("u"));
    }

    @Test
    public void classifiesHorizontalAliases() {
        assertEquals(SacComponentClassifier.Role.H1, SacComponentClassifier.classify("N"));
        assertEquals(SacComponentClassifier.Role.H1, SacComponentClassifier.classify("HHN"));
        assertEquals(SacComponentClassifier.Role.H1, SacComponentClassifier.classify("H1"));
        assertEquals(SacComponentClassifier.Role.H1, SacComponentClassifier.classify("HA"));
        assertEquals(SacComponentClassifier.Role.H1, SacComponentClassifier.classify("Ha"));
        assertEquals(SacComponentClassifier.Role.H1, SacComponentClassifier.classify("HX"));
        assertEquals(SacComponentClassifier.Role.H2, SacComponentClassifier.classify("E"));
        assertEquals(SacComponentClassifier.Role.H2, SacComponentClassifier.classify("HHE"));
        assertEquals(SacComponentClassifier.Role.H2, SacComponentClassifier.classify("H2"));
        assertEquals(SacComponentClassifier.Role.H2, SacComponentClassifier.classify("HB"));
        assertEquals(SacComponentClassifier.Role.H2, SacComponentClassifier.classify("Hb"));
        assertEquals(SacComponentClassifier.Role.H2, SacComponentClassifier.classify("HY"));
    }

    @Test
    public void verticalIsNotHorizontal() {
        assertFalse(SacComponentClassifier.isHorizontal("HHZ"));
        assertFalse(SacComponentClassifier.isHorizontal("UD"));
        assertTrue(SacComponentClassifier.isHorizontal("HA"));
    }
}
