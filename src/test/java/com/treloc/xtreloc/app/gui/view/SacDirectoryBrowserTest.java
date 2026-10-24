package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SacDirectoryBrowserTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void acceptsSacAndShortSExtension() {
        assertTrue(SacDirectoryBrowser.isSacFileName("ML19.Ha.sac"));
        assertTrue(SacDirectoryBrowser.isSacFileName("trace.S"));
        assertTrue(SacDirectoryBrowser.isSacFileName("trace.s"));
        assertFalse(SacDirectoryBrowser.isSacFileName("readme.txt"));
        assertFalse(SacDirectoryBrowser.isSacFileName(""));
    }

    @Test
    public void listsSacFilesInFolder() throws Exception {
        File dir = tmp.newFolder("raw");
        File a = new File(dir, "a.sac");
        File b = new File(dir, "b.s");
        assertTrue(a.createNewFile());
        assertTrue(b.createNewFile());
        assertTrue(new File(dir, "notes.txt").createNewFile());
        File[] listed = SacDirectoryBrowser.listSacFiles(dir);
        assertEquals(2, listed.length);
        assertEquals("a.sac", listed[0].getName());
        assertEquals("b.s", listed[1].getName());
    }
}
