package com.treloc.xtreloc.solver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link PointsHandler}.
 */
public class PointsHandlerTest {

    @Test
    public void readDatFile_validFile_setsMainPoint() throws Exception {
        Path tmp = Files.createTempFile("point", ".dat");
        try {
            String content = "35.0 140.0 10.0\n"
                + "0.01 0.01 0.02 0.5\n"
                + "STA1 STA2 0.15 1.0\n";
            Files.writeString(tmp, content);
            PointsHandler handler = new PointsHandler();
            String[] codes = { "STA1", "STA2" };
            handler.readDatFile(tmp.toString(), codes, 0.0);
            Point main = handler.getMainPoint();
            assertNotNull(main);
            assertEquals(35.0, main.getLat(), 1e-6);
            assertEquals(140.0, main.getLon(), 1e-6);
            assertEquals(10.0, main.getDep(), 1e-6);
            assertNotNull(main.getLagTable());
            assertEquals(1, main.getLagTable().length);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test(expected = IOException.class)
    public void readDatFile_emptyFile_throws() throws Exception {
        Path tmp = Files.createTempFile("point", ".dat");
        try {
            Files.writeString(tmp, "");
            PointsHandler handler = new PointsHandler();
            handler.readDatFile(tmp.toString(), new String[] { "STA1" }, 0.0);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test(expected = IOException.class)
    public void readDatFile_firstLineTooShort_throws() throws Exception {
        Path tmp = Files.createTempFile("point", ".dat");
        try {
            Files.writeString(tmp, "35.0 140.0\n");
            PointsHandler handler = new PointsHandler();
            handler.readDatFile(tmp.toString(), new String[] { "STA1" }, 0.0);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
