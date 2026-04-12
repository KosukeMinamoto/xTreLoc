package com.treloc.xtreloc.io;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link StationRepository}.
 */
public class StationRepositoryTest {

    @Test
    public void load_validFile_returnsRepository() throws Exception {
        Path tmp = Files.createTempFile("station", ".tbl");
        try {
            String content = "STA1  35.0  140.0  -100  0.5  0.5\n"
                + "STA2  35.1  140.1  -200  0.5  0.5\n";
            Files.writeString(tmp, content);
            StationRepository repo = StationRepository.load(tmp);
            assertNotNull(repo);
            assertNotNull(repo.getCodes());
            assertEquals(2, repo.getCodes().length);
            assertEquals("STA1", repo.getCodes()[0]);
            assertEquals("STA2", repo.getCodes()[1]);
            assertTrue(repo.getBottomDepth() > 0);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test(expected = ConfigException.class)
    public void load_tooFewColumns_throws() throws Exception {
        Path tmp = Files.createTempFile("station", ".tbl");
        try {
            Files.writeString(tmp, "STA1  35.0  140.0\n");
            StationRepository.load(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test(expected = ConfigException.class)
    public void load_invalidNumber_throws() throws Exception {
        Path tmp = Files.createTempFile("station", ".tbl");
        try {
            Files.writeString(tmp, "STA1  abc  140.0  -100  0.5  0.5\n");
            StationRepository.load(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void load_skipsEmptyLines() throws Exception {
        Path tmp = Files.createTempFile("station", ".tbl");
        try {
            String content = "STA1  35.0  140.0  -100  0.5  0.5\n\n  \nSTA2  35.1  140.1  -200  0.5  0.5\n";
            Files.writeString(tmp, content);
            StationRepository repo = StationRepository.load(tmp);
            assertNotNull(repo);
            assertEquals(2, repo.getCodes().length);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
