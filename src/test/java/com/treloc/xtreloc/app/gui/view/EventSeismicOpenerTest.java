package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.util.EventSelectionModel;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EventSeismicOpenerTest {

    private final Hypocenter h1 = new Hypocenter("2017-11-15T05:00:01", 36.10, 129.30, 5.0, 0.1, 0.2, 0.3, 0.05);
    private final Hypocenter h2 = new Hypocenter("2017-11-15T05:00:02", 36.20, 129.40, 6.0);
    private final CatalogInfo cat = new CatalogInfo("Cat", Color.RED, CatalogInfo.SymbolType.CIRCLE,
        List.of(h1, h2), null);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path eventFolder(String name) throws Exception {
        Path dir = tmp.getRoot().toPath().resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("ST01.U.sac"), "x");
        return dir;
    }

    @Test
    public void passesFocusAndOtherSelectedFolders() throws Exception {
        Path d1 = eventFolder("171115.050001");
        Path d2 = eventFolder("171115.050002");
        EventSelectionModel m = new EventSelectionModel();
        EventSeismicOpener o = new EventSeismicOpener(m, () -> List.of(cat));
        o.setPatternSupplier(() -> tmp.getRoot() + "/{yyMMdd.HHmmss}");
        List<Object[]> calls = new ArrayList<>();
        o.setSeismicOpener((focus, folders, others) -> calls.add(new Object[] {focus, folders, others}));
        m.setSelection(List.of(EventKey.of(cat, h1), EventKey.of(cat, h2)));

        o.open(EventKey.of(cat, h1), true);

        assertEquals(1, calls.size());
        assertEquals(EventKey.of(cat, h1), calls.get(0)[0]);
        assertEquals(d1.toRealPath(), ((List<Path>) calls.get(0)[1]).get(0).toRealPath());
        assertEquals(d2.toRealPath(), ((List<Path>) calls.get(0)[2]).get(0).toRealPath());
    }

    @Test
    public void doubleClickStyleOpenSendsOnlyThatEvent() throws Exception {
        eventFolder("171115.050001");
        eventFolder("171115.050002");
        EventSelectionModel m = new EventSelectionModel();
        EventSeismicOpener o = new EventSeismicOpener(m, () -> List.of(cat));
        o.setPatternSupplier(() -> tmp.getRoot() + "/{yyMMdd.HHmmss}");
        List<Object[]> calls = new ArrayList<>();
        o.setSeismicOpener((focus, folders, others) -> calls.add(new Object[] {focus, folders, others}));
        m.setSelection(List.of(EventKey.of(cat, h1), EventKey.of(cat, h2)));

        o.open(EventKey.of(cat, h2), false);

        assertEquals(1, calls.size());
        assertEquals(EventKey.of(cat, h2), calls.get(0)[0]);
        assertEquals(0, ((List<?>) calls.get(0)[2]).size());
    }

    @Test
    public void reportsTheReasonWhenNoFolderIsFound() {
        EventSeismicOpener o = new EventSeismicOpener(new EventSelectionModel(), () -> List.of(cat));
        o.setPatternSupplier(() -> tmp.getRoot() + "/missing/{yyMMdd.HHmmss}");
        List<Object[]> calls = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        o.setSeismicOpener((focus, folders, others) -> calls.add(new Object[0]));
        o.setMessageListener(messages::add);

        o.open(EventKey.of(cat, h1), false);

        assertEquals(0, calls.size());
        assertEquals(1, messages.size());
        assertTrue(messages.get(0), messages.get(0).contains("171115.050001"));
    }

    @Test
    public void doesNothingWithoutAnOpener() {
        EventSeismicOpener o = new EventSeismicOpener(new EventSelectionModel(), () -> List.of(cat));
        List<String> messages = new ArrayList<>();
        o.setMessageListener(messages::add);
        o.open(EventKey.of(cat, h1), false);
        assertTrue(messages.isEmpty());
    }
}
