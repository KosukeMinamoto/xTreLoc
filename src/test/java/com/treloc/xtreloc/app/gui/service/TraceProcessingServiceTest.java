package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TraceProcessingServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void touch(Path p) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[0]);
    }

    @Test
    public void unitsAreDirectoriesDirectlyContainingSac() throws Exception {
        Path root = tmp.newFolder("root").toPath();
        touch(root.resolve("a").resolve("x.sac"));
        touch(root.resolve("a").resolve("b").resolve("y.SAC"));
        touch(root.resolve("c").resolve("notes.txt"));
        touch(root.resolve("d").resolve("e").resolve("z.sac"));

        List<Path> units = TraceProcessingService.findUnitDirectories(root);
        assertEquals(3, units.size());
        assertTrue(units.contains(root.resolve("a")));
        assertTrue(units.contains(root.resolve("a").resolve("b")));
        assertTrue(units.contains(root.resolve("d").resolve("e")));
    }

    @Test
    public void rootItselfIsAUnitWhenItHoldsSac() throws Exception {
        Path root = tmp.newFolder("ev").toPath();
        touch(root.resolve("x.sac"));
        List<Path> units = TraceProcessingService.findUnitDirectories(root);
        assertEquals(1, units.size());
        assertEquals(root, units.get(0));
    }

    @Test
    public void outputMirrorsDirectoryHierarchy() throws Exception {
        Path root = tmp.newFolder("2007").toPath();
        Path out = tmp.newFolder("out").toPath();
        Path unit = root.resolve("1201").resolve("ML01");

        assertEquals(
            out.resolve("CF").resolve("2007").resolve("1201").resolve("ML01"),
            TraceProcessingService.outputDirFor(TraceProcessingService.Operation.CHARACTERISTIC, out, root, unit));
        assertEquals(
            out.resolve("ENV").resolve("2007"),
            TraceProcessingService.outputDirFor(TraceProcessingService.Operation.ENVELOPE, out, root, root));
        assertEquals(
            out.resolve("Correlation").resolve("2007").resolve("1201").resolve("ML01"),
            TraceProcessingService.outputDirFor(TraceProcessingService.Operation.CROSS_CORRELATION, out, root, unit));
    }
}
