package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Test;

public class TravelTimeTableCardTest {

    @Test
    public void defaultTableDir_isUnderSettingsHome() {
        File dir = TravelTimeTableCard.defaultTableDir("/tmp/xtreloc-home", "prem");
        assertEquals(new File(new File("/tmp/xtreloc-home", "time"), "prem").getAbsolutePath(),
            dir.getAbsolutePath());
        assertTrue(dir.getPath().replace('\\', '/').endsWith("time/prem"));
    }

    @Test
    public void defaultTableDir_fallsBackWhenHomeOrRootBlank() {
        File dir = TravelTimeTableCard.defaultTableDir("  ", "");
        assertEquals("time", dir.getName());
    }
}
