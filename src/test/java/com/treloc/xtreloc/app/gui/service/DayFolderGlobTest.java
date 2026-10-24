package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DayFolderGlobTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void globMatchesTwoOfThreeDays() throws Exception {
        Path root = tmp.newFolder("sac").toPath();
        touchSac(root.resolve("20071201"));
        touchSac(root.resolve("20071202"));
        touchSac(root.resolve("20080101"));
        Files.createDirectories(root.resolve("empty"));

        List<String> names = names(DayFolderGlob.expand(root.resolve("200712*").toString()));
        assertEquals(List.of("20071201", "20071202"), names);
    }

    @Test
    public void starListsImmediateDayFolders() throws Exception {
        Path root = tmp.newFolder("all").toPath();
        touchSac(root.resolve("20071201"));
        touchSac(root.resolve("20080101"));
        Files.createDirectories(root.resolve("nodata"));

        List<String> names = names(DayFolderGlob.expand(root.resolve("*").toString()));
        assertEquals(List.of("20071201", "20080101"), names);
    }

    @Test
    public void plainDirectoryWithoutSacWalksChildren() throws Exception {
        Path root = tmp.newFolder("parent").toPath();
        touchSac(root.resolve("20071215"));
        List<Path> days = DayFolderGlob.expand(root.toString());
        assertEquals(1, days.size());
        assertEquals("20071215", days.get(0).getFileName().toString());
    }

    @Test
    public void acceptsDotS() throws Exception {
        Path day = tmp.newFolder("20071299").toPath();
        Files.write(day.resolve("STA.s"), new byte[] { 1, 2, 3 });
        List<Path> days = DayFolderGlob.expand(day.getParent().resolve("20071299").toString());
        assertEquals(1, days.size());
        assertTrue(DayFolderGlob.containsWaveform(day));
    }

    private static void touchSac(Path day) throws Exception {
        Files.createDirectories(day);
        Files.write(day.resolve("dummy.sac"), new byte[] { 1 });
    }

    private static List<String> names(List<Path> days) {
        return days.stream().map(p -> p.getFileName().toString()).collect(Collectors.toList());
    }
}
