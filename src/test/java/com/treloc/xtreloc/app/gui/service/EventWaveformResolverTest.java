package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EventWaveformResolverTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static CatalogInfo catalog(File src, Hypocenter... hs) {
        return new CatalogInfo("Cat", Color.RED, CatalogInfo.SymbolType.CIRCLE, List.of(hs), src);
    }

    private Path eventFolder(String name) throws Exception {
        Path dir = tmp.getRoot().toPath().resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("ST01.U.sac"), "x");
        return dir;
    }

    @Test
    public void patternFillsPlaceholdersFromOriginTime() throws Exception {
        Path dir = eventFolder("171115.052001");
        Hypocenter h = new Hypocenter("2017-11-15T05:20:01", 36.1, 129.3, 5.0);
        String pattern = tmp.getRoot() + "/{yyMMdd.HHmmss}";
        EventWaveformResolver.Resolution r = EventWaveformResolver.resolve(pattern, catalog(null, h), h);
        assertTrue(r.reason(), r.found());
        assertEquals(dir.toRealPath(), r.first().toRealPath());
    }

    @Test
    public void individualPlaceholdersAndFractionalSeconds() {
        String s = EventWaveformResolver.expandPattern("/d/{yyyy}/{MM}{dd}/{HH}{mm}{ss}/{catalog}",
            "Woo", EventWaveformResolver.parseTime("2017-11-15T05:20:01.37"));
        assertEquals("/d/2017/1115/052001/Woo", s);
    }

    @Test
    public void missingFolderExplainsWhy() {
        Hypocenter h = new Hypocenter("2017-11-15T05:20:01", 36.1, 129.3, 5.0);
        EventWaveformResolver.Resolution r =
            EventWaveformResolver.resolve(tmp.getRoot() + "/none/{yyMMdd.HHmmss}", catalog(null, h), h);
        assertFalse(r.found());
        assertTrue(r.reason().contains("171115.052001"));
        EventWaveformResolver.Resolution blank = EventWaveformResolver.resolve("", catalog(null, h), h);
        assertFalse(blank.found());
        assertTrue(blank.reason().contains("Settings"));
    }

    @Test
    public void falls_back_to_folder_next_to_dat_file() throws Exception {
        Path dir = eventFolder("ev1");
        File catalogFile = tmp.newFile("catalog.csv");
        Hypocenter h = new Hypocenter("2017-11-15T05:20:01", 36.1, 129.3, 5.0, 0, 0, 0, 0, null, "ev1.dat", "LMO");
        EventWaveformResolver.Resolution r = EventWaveformResolver.resolve("", catalog(catalogFile, h), h);
        assertTrue(r.reason(), r.found());
        assertEquals(dir.toRealPath(), r.first().toRealPath());
    }

    @Test
    public void folderNameGivesOriginTime() {
        assertEquals(LocalDateTime.of(2017, 11, 15, 5, 20, 1),
            EventWaveformResolver.timeFromFolderName("171115.052001"));
        assertNull(EventWaveformResolver.timeFromFolderName("sac_day"));
    }

    @Test
    public void findEventPicksClosestWithinTolerance() {
        Hypocenter a = new Hypocenter("2017-11-15T05:20:01", 36.1, 129.3, 5.0);
        Hypocenter b = new Hypocenter("2017-11-15T05:20:04", 36.2, 129.3, 5.0);
        CatalogInfo c = catalog(null, a, b);
        EventKey k = EventWaveformResolver.findEvent(List.of(c), LocalDateTime.of(2017, 11, 15, 5, 20, 3), 2.0);
        assertNotNull(k);
        assertEquals(EventKey.of(c, b), k);
        assertNull(EventWaveformResolver.findEvent(List.of(c), LocalDateTime.of(2017, 11, 15, 5, 30, 0), 2.0));
        c.setVisible(false);
        assertNull(EventWaveformResolver.findEvent(List.of(c), LocalDateTime.of(2017, 11, 15, 5, 20, 1), 2.0));
    }
}
