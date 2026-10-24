package com.treloc.xtreloc.io;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.treloc.xtreloc.util.JsonMapperHolder;

public class AppConfigIoTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void exportOmitsLegacyModesAndSolverBlocks() throws Exception {
        AppConfig cfg = new AppConfig();
        cfg.stationFile = "station.tbl";
        cfg.taupFile = "prem";
        cfg.io = new java.util.HashMap<>();
        AppConfig.ModeIOConfig io = new AppConfig.ModeIOConfig();
        io.datDirectory = Path.of("dat");
        io.outDirectory = Path.of("dat-lmo");
        cfg.io.put("LMO", io);
        String json = JsonMapperHolder.getMapper().writeValueAsString(cfg);
        assertFalse(json.contains("\"modes\""));
        assertFalse(json.contains("\"solver\""));
        assertFalse(json.contains("takeoffFile"));
        assertTrue(json.contains("\"taupFile\""));
    }

    @Test
    public void loadIgnoresLegacyModesAndSolver() throws Exception {
        Path f = tmp.newFolder("cfg").toPath().resolve("config.json");
        Files.writeString(f, """
            {
              "stationFile": "station.tbl",
              "taupFile": "prem",
              "modes": { "LMO": { "datDirectory": "dat" } },
              "solver": { "LMO": { "maxIterations": 10 } },
              "io": { "LMO": { "datDirectory": "dat", "outDirectory": "out" } }
            }
            """);
        AppConfig cfg = new ConfigLoader(f).getConfig();
        assertTrue(cfg.getModes().containsKey("LMO"));
        assertTrue(cfg.taupFile.endsWith("prem") || "prem".equals(cfg.taupFile));
    }

    @Test
    public void bundledShortNameIsNotResolvedAsARelativeFile() throws Exception {
        Path dir = tmp.newFolder("cfg").toPath();
        Path f = dir.resolve("config.json");
        Files.writeString(f, "{ \"taupFile\": \"prem\" }");
        AppConfig cfg = new ConfigLoader(f).getConfig();
        org.junit.Assert.assertEquals("prem", cfg.taupFile);
        assertTrue(VelocityModelCatalog.isBundledModelName("prem"));
        org.junit.Assert.assertEquals("velocity-models/prem.nd", VelocityModelCatalog.toResourcePath("prem"));
    }
}
