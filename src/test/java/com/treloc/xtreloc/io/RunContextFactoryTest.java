package com.treloc.xtreloc.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link RunContextFactory}.
 */
public class RunContextFactoryTest {

    @Test(expected = ConfigException.class)
    public void fromCLI_nullConfig_throws() throws Exception {
        RunContextFactory.fromCLI("GRD", null);
    }

    @Test(expected = ConfigException.class)
    public void fromCLI_nullModes_throws() throws Exception {
        AppConfig config = new AppConfig();
        config.io = null;
        config.params = null;
        RunContextFactory.fromCLI("GRD", config);
    }

    @Test(expected = ConfigException.class)
    public void fromCLI_emptyModes_throws() throws Exception {
        AppConfig config = new AppConfig();
        config.io = new java.util.HashMap<>();
        config.params = new java.util.HashMap<>();
        RunContextFactory.fromCLI("GRD", config);
    }

    @Test(expected = ConfigException.class)
    public void fromCLI_unknownMode_throws() throws Exception {
        AppConfig config = new AppConfig();
        config.io = new java.util.HashMap<>();
        config.params = new java.util.HashMap<>();
        AppConfig.ModeIOConfig io = new AppConfig.ModeIOConfig();
        io.datDirectory = Paths.get("demo/dat");
        io.outDirectory = Paths.get("demo/out");
        config.io.put("GRD", io);
        RunContextFactory.fromCLI("UNKNOWN", config);
    }

    @Test
    public void fromCLI_validMode_returnsContext() throws Exception {
        Path datDir = Files.createTempDirectory("xtreloc-test-dat");
        Path outDir = Files.createTempDirectory("xtreloc-test-out");
        try {
            AppConfig config = new AppConfig();
            config.io = new java.util.HashMap<>();
            config.params = new java.util.HashMap<>();
            AppConfig.ModeIOConfig io = new AppConfig.ModeIOConfig();
            io.datDirectory = datDir;
            io.outDirectory = outDir;
            config.io.put("GRD", io);
            RunContext ctx = RunContextFactory.fromCLI("GRD", config);
            assertNotNull(ctx);
            assertEquals("GRD", ctx.getMode());
            assertNotNull(ctx.getDatFiles());
            assertEquals(outDir, ctx.getOutDir());
        } finally {
            Files.deleteIfExists(datDir);
            Files.deleteIfExists(outDir);
        }
    }
}
