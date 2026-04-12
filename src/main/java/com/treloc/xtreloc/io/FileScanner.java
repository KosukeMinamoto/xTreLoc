package com.treloc.xtreloc.io;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FileScanner {

    private FileScanner() {
    }

    public static Path[] scan(Path dir) throws IOException {
        if (dir == null) {
            throw new IllegalArgumentException("Directory path cannot be null");
        }
        if (!Files.exists(dir)) {
            throw new IOException("Directory does not exist: " + dir);
        }
        if (!Files.isDirectory(dir)) {
            throw new IOException("Path is not a directory: " + dir);
        }
        List<Path> list = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.dat")) {
            for (Path p : ds) {
                if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".dat")) {
                    list.add(p);
                }
            }
        }
        return list.toArray(new Path[0]);
    }
}
