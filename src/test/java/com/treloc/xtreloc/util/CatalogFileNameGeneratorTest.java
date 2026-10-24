package com.treloc.xtreloc.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class CatalogFileNameGeneratorTest {

    @Test
    public void uniqueIfExistsAppendsN() throws Exception {
        File dir = Files.createTempDirectory("catn").toFile();
        File first = new File(dir, "catalog_lmo.csv");
        Files.writeString(first.toPath(), "x");
        File n1 = CatalogFileNameGenerator.uniqueIfExists(first);
        assertEquals("catalog_lmo_1.csv", n1.getName());
        Files.writeString(n1.toPath(), "y");
        File n2 = CatalogFileNameGenerator.uniqueIfExists(first);
        assertEquals("catalog_lmo_2.csv", n2.getName());
        assertFalse(n2.exists());
    }

    @Test
    public void generateCatalogFileNameSkipsExisting() throws Exception {
        File dir = Files.createTempDirectory("catg").toFile();
        Files.writeString(new File(dir, "catalog_syn.csv").toPath(), "x");
        File out = CatalogFileNameGenerator.generateCatalogFileName(null, "SYN", dir);
        assertEquals("catalog_syn_1.csv", out.getName());
        assertTrue(out.getParentFile().equals(dir));
    }
}
