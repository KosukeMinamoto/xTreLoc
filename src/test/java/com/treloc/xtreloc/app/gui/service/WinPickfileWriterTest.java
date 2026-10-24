package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WinPickfileWriterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writesHashPAndHashSBlocks() throws Exception {
        LocalDateTime start = LocalDateTime.of(1998, 2, 17, 14, 2, 42);
        List<ArrivalTime> picks = new ArrayList<>();
        ArrivalTime p = new ArrivalTime("ASO", "U", "P",
            Date.from(start.plusSeconds(20).plusNanos(755_000_000).atZone(
                java.time.ZoneId.systemDefault()).toInstant()));
        p.setFirstMotion("U");
        picks.add(p);
        picks.add(new ArrivalTime("ASO", "E", "S",
            Date.from(start.plusSeconds(21).plusNanos(917_000_000).atZone(
                java.time.ZoneId.systemDefault()).toInstant())));

        HashMap<String, CrossCorrelationPickingService.StationCoord> coords = new HashMap<>();
        coords.put("ASO", new CrossCorrelationPickingService.StationCoord(
            "ASO", 36.64934, 139.45970, -0.720, true));

        Path out = tmp.newFile("980217.140302.000").toPath();
        WinPickfileWriter.write(out, picks, "980217.140302", "Nikko", start, coords);
        List<String> lines = Files.readAllLines(out);
        assertTrue(lines.get(0).startsWith("#p 980217.140302 Nikko xtreloc"));
        assertTrue(lines.get(1).startsWith("#p 98 02 17 14 02 42"));
        assertTrue(lines.stream().anyMatch(s -> s.startsWith("#p 0000 0 ")));
        assertTrue(lines.stream().anyMatch(s -> s.startsWith("#p 0000 1 ")));
        String s = lines.stream().filter(x -> x.startsWith("#s ASO")).findFirst().orElse("");
        assertTrue(s, s.contains("U"));
        assertTrue(s, s.contains("36.64934"));
        assertEquals("#s", lines.get(lines.size() - 1));
        assertTrue(lines.stream().noneMatch(x -> x.startsWith("#f")));
    }
}
