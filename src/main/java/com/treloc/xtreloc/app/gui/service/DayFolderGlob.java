package com.treloc.xtreloc.app.gui.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Expands a directory glob ({@code *} / {@code ?} in the last name) to day folders that
 * directly contain waveform files ({@code .sac} / {@code .s}).
 */
public final class DayFolderGlob {

    private DayFolderGlob() {}

    /**
     * @param spec absolute or relative path, optionally with {@code *} / {@code ?} in the last segment
     *             (e.g. {@code /data/sac/2007*} or {@code sac/*})
     * @return matching directories, sorted; empty if none match
     */
    public static List<Path> expand(String spec) throws IOException {
        return expand(spec, null);
    }

    /**
     * @param base if {@code spec} is relative, resolve it against this directory (config parent)
     */
    public static List<Path> expand(String spec, Path base) throws IOException {
        if (spec == null || spec.isBlank()) {
            throw new IOException("Path or glob is required");
        }
        String raw = spec.trim();
        Path resolved = resolveSpec(raw, base);
        String asString = resolved.toString();
        if (!hasMeta(asString) && !hasMeta(raw)) {
            return expandPlainDirectory(resolved);
        }
        return expandGlob(resolved);
    }

    static boolean isWaveformName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".sac") || n.endsWith(".s");
    }

    static boolean containsWaveform(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                Path name = p.getFileName();
                if (Files.isRegularFile(p) && name != null && isWaveformName(name.toString())) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    static List<Path> listWaveforms(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                Path name = p.getFileName();
                if (Files.isRegularFile(p) && name != null && isWaveformName(name.toString())) {
                    out.add(p.toAbsolutePath().normalize());
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    private static Path resolveSpec(String spec, Path base) {
        Path p = Paths.get(spec);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        Path root = base != null ? base : Paths.get(".").toAbsolutePath();
        return root.resolve(spec).normalize();
    }

    private static boolean hasMeta(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
    }

    private static List<Path> expandPlainDirectory(Path dir) throws IOException {
        Path abs = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(abs)) {
            throw new IOException("Not a directory: " + dir);
        }
        if (containsWaveform(abs)) {
            return List.of(abs);
        }
        List<Path> kids = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(abs)) {
            for (Path child : ds) {
                if (Files.isDirectory(child) && containsWaveform(child)) {
                    kids.add(child.toAbsolutePath().normalize());
                }
            }
        }
        Collections.sort(kids);
        return kids;
    }

    private static List<Path> expandGlob(Path resolved) throws IOException {
        Path parent = resolved.getParent();
        Path name = resolved.getFileName();
        if (parent == null || name == null) {
            throw new IOException("Invalid glob: " + resolved);
        }
        if (!Files.isDirectory(parent)) {
            throw new IOException("Glob parent is not a directory: " + parent);
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + name);
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent)) {
            for (Path child : ds) {
                Path fn = child.getFileName();
                if (fn != null && Files.isDirectory(child) && matcher.matches(fn) && containsWaveform(child)) {
                    out.add(child.toAbsolutePath().normalize());
                }
            }
        }
        Collections.sort(out);
        return out;
    }
}
