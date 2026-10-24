package com.treloc.xtreloc.solver.grid;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Travel times from one station and phase on a regular 3-D grid, in the layout of a NonLinLoc {@code TIME} grid:
 * node {@code (ix, iy, iz)} is at {@code (x0 + ix dx, y0 + iy dy, z0 + iz dz)} km (x east, y north, z depth
 * down) relative to the grid origin, and values are stored with {@code z} varying fastest, then {@code y},
 * then {@code x}.
 *
 * <p>Files: {@code <root>.<PHASE>.<label>.time.hdr} (text) and {@code <root>.<PHASE>.<label>.time.buf} (raw
 * 32-bit floats). {@code TRANSFORM SIMPLE} and {@code LAMBERT} are supported; {@code TIME2D} grids
 * (one collapsed horizontal axis) interpolate in the remaining plane.</p>
 *
 * <p>Instances are immutable (the value array is shared, do not modify) and thread-safe.</p>
 */
public final class StationTimeGrid {

    private final String label;
    private final String phase;
    private final int nx;
    private final int ny;
    private final int nz;
    private final double x0;
    private final double y0;
    private final double z0;
    private final double dx;
    private final double dy;
    private final double dz;
    private final double srcX;
    private final double srcY;
    private final double srcZ;
    private final NllGeographicTransform transform;
    private final float[] t;

    public StationTimeGrid(String label, String phase, int nx, int ny, int nz,
                           double x0, double y0, double z0, double dx, double dy, double dz,
                           double srcX, double srcY, double srcZ, NllGeographicTransform transform, float[] t) {
        if (nx < 1 || ny < 1 || nz < 2 || !(dz > 0)) {
            throw new IllegalArgumentException("Grid needs >= 2 depth nodes, at least 1 node on x/y, and positive dz");
        }
        if ((nx >= 2 && !(dx > 0)) || (ny >= 2 && !(dy > 0))) {
            throw new IllegalArgumentException("Grid needs positive spacing on axes with >= 2 nodes");
        }
        if (nx < 2 && ny < 2) {
            throw new IllegalArgumentException("Grid needs >= 2 nodes on at least one horizontal axis");
        }
        if ((long) nx * ny * nz != t.length) {
            throw new IllegalArgumentException("Value array length " + t.length + " != " + nx + "x" + ny + "x" + nz);
        }
        this.label = label;
        this.phase = phase;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.srcX = srcX;
        this.srcY = srcY;
        this.srcZ = srcZ;
        this.transform = transform;
        this.t = t;
    }

    // ------------------------------------------------------------------ accessors

    public String label() { return label; }
    public String phase() { return phase; }
    public int nx() { return nx; }
    public int ny() { return ny; }
    public int nz() { return nz; }
    public double x0() { return x0; }
    public double y0() { return y0; }
    public double z0() { return z0; }
    public double dx() { return dx; }
    public double dy() { return dy; }
    public double dz() { return dz; }
    public double sourceX() { return srcX; }
    public double sourceY() { return srcY; }
    public double sourceZ() { return srcZ; }
    public NllGeographicTransform transform() { return transform; }
    public boolean time2d() { return nx < 2 || ny < 2; }
    public double xMax() { return x0 + (nx - 1) * dx; }
    public double yMax() { return y0 + (ny - 1) * dy; }
    public double zMax() { return z0 + (nz - 1) * dz; }
    public long nodeCount() { return (long) nx * ny * nz; }

    public float value(int ix, int iy, int iz) {
        return t[(ix * ny + iy) * nz + iz];
    }

    // ------------------------------------------------------------------ interpolation

    /** True if the point (grid km, depth km) is inside the grid. A collapsed TIME2D axis is ignored. */
    public boolean containsXYZ(double x, double y, double z) {
        double e = 1e-9;
        boolean xok = nx < 2 || (x >= x0 - e && x <= xMax() + e);
        boolean yok = ny < 2 || (y >= y0 - e && y <= yMax() + e);
        return xok && yok && z >= z0 - e && z <= zMax() + e;
    }

    /** True if the geographic point is inside the grid (needs a transform). */
    public boolean contains(double lat, double lon, double depthKm) {
        double[] xy = requireTransform().toXY(lat, lon);
        return containsXYZ(xy[0], xy[1], depthKm);
    }

    /** Trilinear travel time (s) at a geographic position, or NaN outside the grid. */
    public double time(double lat, double lon, double depthKm) {
        double[] xy = requireTransform().toXY(lat, lon);
        return timeXYZ(xy[0], xy[1], depthKm);
    }

    /** Trilinear travel time (s) at grid coordinates (km), or NaN outside the grid. */
    public double timeXYZ(double x, double y, double z) {
        double[] out = new double[4];
        return evaluate(x, y, z, out) ? out[0] : Double.NaN;
    }

    /**
     * Travel time and gradient at grid coordinates. {@code out = {T, dT/dx, dT/dy, dT/dz}} (s, s/km; x east,
     * y north, z down).
     *
     * @return false if the point is outside the grid
     */
    public boolean evaluate(double x, double y, double z, double[] out) {
        if (!containsXYZ(x, y, z) || Double.isNaN(x + y + z)) {
            return false;
        }
        double u = nx < 2 || !(dx > 0) ? 0.0 : (x - x0) / dx;
        double v = ny < 2 || !(dy > 0) ? 0.0 : (y - y0) / dy;
        double w = (z - z0) / dz;
        int i = nx < 2 ? 0 : Math.max(0, Math.min(nx - 2, (int) Math.floor(u)));
        int j = ny < 2 ? 0 : Math.max(0, Math.min(ny - 2, (int) Math.floor(v)));
        int k = Math.max(0, Math.min(nz - 2, (int) Math.floor(w)));
        double fx = nx < 2 ? 0.0 : Math.max(0.0, Math.min(1.0, u - i));
        double fy = ny < 2 ? 0.0 : Math.max(0.0, Math.min(1.0, v - j));
        double fz = Math.max(0.0, Math.min(1.0, w - k));
        int b = (i * ny + j) * nz + k;
        int sx = ny * nz;
        int sy = nz;
        double c000 = t[b];
        double c001 = t[b + 1];
        double c010 = ny < 2 ? c000 : t[b + sy];
        double c011 = ny < 2 ? c001 : t[b + sy + 1];
        double c100 = nx < 2 ? c000 : t[b + sx];
        double c101 = nx < 2 ? c001 : t[b + sx + 1];
        double c110 = nx < 2 || ny < 2 ? (nx < 2 ? c010 : c100) : t[b + sx + sy];
        double c111 = nx < 2 || ny < 2 ? (nx < 2 ? c011 : c101) : t[b + sx + sy + 1];
        double c00 = c000 + (c001 - c000) * fz;
        double c01 = c010 + (c011 - c010) * fz;
        double c10 = c100 + (c101 - c100) * fz;
        double c11 = c110 + (c111 - c110) * fz;
        double c0 = c00 + (c01 - c00) * fy;
        double c1 = c10 + (c11 - c10) * fy;
        out[0] = c0 + (c1 - c0) * fx;
        out[1] = nx < 2 || !(dx > 0) ? 0.0 : (c1 - c0) / dx;
        out[2] = ny < 2 || !(dy > 0) ? 0.0 : ((c01 - c00) * (1 - fx) + (c11 - c10) * fx) / dy;
        double dz0 = (c001 - c000) * (1 - fy) + (c011 - c010) * fy;
        double dz1 = (c101 - c100) * (1 - fy) + (c111 - c110) * fy;
        out[3] = (dz0 * (1 - fx) + dz1 * fx) / dz;
        return true;
    }

    /**
     * The grid as a geographic volume for display (channel {@code travel_time_s}); needs a SIMPLE transform
     * (longitude depends on x only and latitude on y only, so the axes stay rectilinear).
     */
    public com.treloc.xtreloc.solver.eikonal.VelocityVolume3D toVolume(String name) {
        NllGeographicTransform tr = requireTransform();
        double[] lons = new double[nx];
        double[] lats = new double[ny];
        double[] deps = new double[nz];
        for (int i = 0; i < nx; i++) {
            lons[i] = tr.toLatLon(x0 + i * dx, 0.0)[1];
        }
        for (int j = 0; j < ny; j++) {
            lats[j] = tr.toLatLon(0.0, y0 + j * dy)[0];
        }
        for (int k = 0; k < nz; k++) {
            deps[k] = z0 + k * dz;
        }
        float[] v = new float[nx * ny * nz];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    v[(k * ny + j) * nx + i] = t[(i * ny + j) * nz + k];
                }
            }
        }
        java.util.Map<String, float[]> ch = new java.util.LinkedHashMap<>();
        ch.put(CH_TIME, v);
        return com.treloc.xtreloc.solver.eikonal.VelocityVolume3D.of(name, lons, lats, deps, ch);
    }

    /** Channel name of {@link #toVolume}. */
    public static final String CH_TIME = "travel_time_s";

    private NllGeographicTransform requireTransform() {
        if (transform == null) {
            throw new IllegalStateException("Grid " + label + " has no geographic transform");
        }
        return transform;
    }

    // ------------------------------------------------------------------ files

    /** File stem {@code <root>.<PHASE>.<label>.time} (without extension). */
    public static String stem(String root, String phase, String label) {
        return root + "." + phase + "." + sanitize(label) + ".time";
    }

    static String sanitize(String label) {
        return label.trim().replaceAll("[\\s/\\\\]+", "_");
    }

    /**
     * Writes {@code <root>.<PHASE>.<label>.time.hdr} / {@code .buf} into {@code dir}.
     *
     * @param order byte order of the {@code .buf} file; NonLinLoc itself writes the byte order of the machine,
     *              which is little endian on current hardware
     */
    public void write(Path dir, String root, ByteOrder order) throws IOException {
        Files.createDirectories(dir);
        String stem = stem(root, phase, label);
        List<String> hdrLines = new ArrayList<>();
        hdrLines.add(String.format(Locale.ROOT, "%d %d %d  %.6f %.6f %.6f  %.6f %.6f %.6f TIME FLOAT",
            nx, ny, nz, x0, y0, z0, dx, dy, dz));
        hdrLines.add(String.format(Locale.ROOT, "%s %.6f %.6f %.6f", sanitize(label), srcX, srcY, srcZ));
        if (transform != null) {
            hdrLines.add(transform.headerLine());
        } else {
            hdrLines.add("TRANSFORM  NONE");
        }
        Path hdrFile = dir.resolve(stem + ".hdr");
        Path bufFile = dir.resolve(stem + ".buf");
        Path hdrTmp = dir.resolve(stem + ".hdr.tmp");
        Path bufTmp = dir.resolve(stem + ".buf.tmp");
        try {
            try (RandomAccessFile raf = new RandomAccessFile(bufTmp.toFile(), "rw");
                 FileChannel ch = raf.getChannel()) {
                raf.setLength(0);
                ByteBuffer bb = ByteBuffer.allocate(1 << 20).order(order);
                int pos = 0;
                while (pos < t.length) {
                    bb.clear();
                    int n = Math.min(t.length - pos, bb.capacity() / 4);
                    bb.asFloatBuffer().put(t, pos, n);
                    bb.limit(n * 4);
                    while (bb.hasRemaining()) {
                        ch.write(bb);
                    }
                    pos += n;
                }
            }
            Files.write(hdrTmp, hdrLines, StandardCharsets.US_ASCII);
            moveReplace(bufTmp, bufFile);
            moveReplace(hdrTmp, hdrFile);
        } catch (IOException e) {
            Files.deleteIfExists(bufTmp);
            Files.deleteIfExists(hdrTmp);
            throw e;
        }
    }

    private static void moveReplace(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * True when the header exists and its sibling {@code .buf} has the expected number of float32 values.
     * Incomplete pairs (empty or truncated {@code .buf} after a cancelled write) return false.
     */
    public static boolean isComplete(Path header) {
        try {
            if (header == null || !Files.isRegularFile(header)) {
                return false;
            }
            List<String> lines = Files.readAllLines(header, StandardCharsets.US_ASCII);
            String first = null;
            for (String l : lines) {
                if (!l.trim().isEmpty()) {
                    first = l.trim();
                    break;
                }
            }
            if (first == null) {
                return false;
            }
            String[] h = first.split("\\s+");
            if (h.length < 3) {
                return false;
            }
            long n = Long.parseLong(h[0]) * Long.parseLong(h[1]) * Long.parseLong(h[2]);
            if (n <= 0 || n > Integer.MAX_VALUE / 4) {
                return false;
            }
            String fname = header.getFileName().toString();
            if (!fname.endsWith(".time.hdr")) {
                return false;
            }
            Path buf = header.resolveSibling(fname.substring(0, fname.length() - ".time.hdr".length()) + ".time.buf");
            return Files.isRegularFile(buf) && Files.size(buf) == n * 4L;
        } catch (Exception e) {
            return false;
        }
    }

    public void write(Path dir, String root) throws IOException {
        write(dir, root, ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Reads a {@code .time.hdr} / {@code .time.buf} pair. The byte order is detected from the data (travel times
     * must be finite and non-negative), preferring little endian.
     *
     * @param hdr path of the header file (name {@code <root>.<PHASE>.<label>.time.hdr})
     */
    /** Text fields of a {@code .time.hdr} (no {@code .buf} read). */
    public static final class Header {
        public final String label;
        public final String phase;
        public final String gridType;
        public final int nx;
        public final int ny;
        public final int nz;
        public final double x0;
        public final double y0;
        public final double z0;
        public final double dx;
        public final double dy;
        public final double dz;
        public final double srcX;
        public final double srcY;
        public final double srcZ;
        public final NllGeographicTransform transform;
        public final Path file;

        Header(String label, String phase, String gridType, int nx, int ny, int nz,
               double x0, double y0, double z0, double dx, double dy, double dz,
               double srcX, double srcY, double srcZ, NllGeographicTransform transform, Path file) {
            this.label = label;
            this.phase = phase;
            this.gridType = gridType;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.srcX = srcX;
            this.srcY = srcY;
            this.srcZ = srcZ;
            this.transform = transform;
            this.file = file;
        }

        /** Geographic station, or {@code null} when the header has no transform. */
        public com.treloc.xtreloc.io.Station toStation() {
            if (transform == null || label == null || label.isBlank()) {
                return null;
            }
            double[] ll = transform.toLatLon(srcX, srcY);
            return new com.treloc.xtreloc.io.Station(label, ll[0], ll[1], -srcZ * 1000.0, 0.0, 0.0);
        }
    }

    /** Reads the text header only (no {@code .buf}). */
    public static Header parseHeader(Path hdr) throws IOException {
        String fname = hdr.getFileName().toString();
        if (!fname.endsWith(".time.hdr")) {
            throw new IOException("Not a travel-time grid header (expected *.time.hdr): " + fname);
        }
        String base = fname.substring(0, fname.length() - ".time.hdr".length());
        String[] parts = base.split("\\.");
        if (parts.length < 3) {
            throw new IOException("Cannot read phase and station label from file name: " + fname);
        }
        String phase = parts[parts.length - 2];
        List<String> lines = new ArrayList<>();
        for (String l : Files.readAllLines(hdr, StandardCharsets.US_ASCII)) {
            if (!l.trim().isEmpty()) {
                lines.add(l.trim());
            }
        }
        if (lines.size() < 2) {
            throw new IOException("Incomplete header: " + hdr);
        }
        String[] h = lines.get(0).split("\\s+");
        if (h.length < 10) {
            throw new IOException("Malformed grid header line in " + hdr + ": " + lines.get(0));
        }
        String gridType = h[9];
        if (!"TIME".equalsIgnoreCase(gridType) && !"TIME2D".equalsIgnoreCase(gridType)) {
            throw new IOException("Unsupported grid type '" + gridType + "' in " + hdr
                + " (only TIME / TIME2D grids are supported)");
        }
        int nx;
        int ny;
        int nz;
        double x0;
        double y0;
        double z0;
        double dx;
        double dy;
        double dz;
        try {
            nx = Integer.parseInt(h[0]);
            ny = Integer.parseInt(h[1]);
            nz = Integer.parseInt(h[2]);
            x0 = Double.parseDouble(h[3]);
            y0 = Double.parseDouble(h[4]);
            z0 = Double.parseDouble(h[5]);
            dx = Double.parseDouble(h[6]);
            dy = Double.parseDouble(h[7]);
            dz = Double.parseDouble(h[8]);
        } catch (NumberFormatException e) {
            throw new IOException("Malformed grid header line in " + hdr + ": " + lines.get(0), e);
        }
        String[] s = lines.get(1).split("\\s+");
        if (s.length < 4) {
            throw new IOException("Malformed source line in " + hdr + ": " + lines.get(1));
        }
        String label = s[0];
        double sx;
        double sy;
        double sz;
        try {
            sx = Double.parseDouble(s[1]);
            sy = Double.parseDouble(s[2]);
            sz = Double.parseDouble(s[3]);
        } catch (NumberFormatException e) {
            throw new IOException("Malformed source line in " + hdr + ": " + lines.get(1), e);
        }
        NllGeographicTransform tr = null;
        if (lines.size() >= 3) {
            try {
                tr = NllTransforms.parse(lines.get(2));
            } catch (IOException e) {
                throw new IOException(e.getMessage() + " (" + hdr + ")", e);
            }
        }
        return new Header(label, phase, gridType, nx, ny, nz, x0, y0, z0, dx, dy, dz, sx, sy, sz, tr, hdr);
    }

    public static StationTimeGrid read(Path hdr) throws IOException {
        Header head = parseHeader(hdr);
        String fname = hdr.getFileName().toString();
        String base = fname.substring(0, fname.length() - ".time.hdr".length());
        int nx = head.nx;
        int ny = head.ny;
        int nz = head.nz;
        Path buf = hdr.resolveSibling(base + ".time.buf");
        long n = (long) nx * ny * nz;
        if (n <= 0 || n > Integer.MAX_VALUE / 4) {
            throw new IOException("Grid too large or empty: " + n + " nodes in " + hdr);
        }
        if (Files.size(buf) != n * 4) {
            throw new IOException("Size of " + buf.getFileName() + " (" + Files.size(buf) + " bytes) does not match "
                + nx + "x" + ny + "x" + nz + " float values");
        }
        float[] data = new float[(int) n];
        ByteBuffer heap = ByteBuffer.allocate((int) n * 4);
        try (FileChannel ch = FileChannel.open(buf)) {
            while (heap.hasRemaining()) {
                if (ch.read(heap) < 0) {
                    throw new IOException("Short read of " + buf.getFileName());
                }
            }
        }
        heap.flip();
        ByteOrder order = ByteOrder.LITTLE_ENDIAN;
        if (!samplePlausible(heap, order)) {
            order = ByteOrder.BIG_ENDIAN;
            if (!samplePlausible(heap, order)) {
                throw new IOException("Values of " + buf.getFileName() + " are not valid travel times in either byte order");
            }
        }
        heap.order(order).asFloatBuffer().get(data);
        return new StationTimeGrid(head.label, head.phase, nx, ny, nz, head.x0, head.y0, head.z0,
            head.dx, head.dy, head.dz, head.srcX, head.srcY, head.srcZ, head.transform, data);
    }

    /** Spot-check evenly spaced samples so endianness does not require a full extra pass. */
    private static boolean samplePlausible(ByteBuffer raw, ByteOrder order) {
        java.nio.FloatBuffer fb = raw.duplicate().order(order).asFloatBuffer();
        int n = fb.remaining();
        if (n <= 0) {
            return false;
        }
        int step = Math.max(1, n / 64);
        for (int i = 0; i < n; i += step) {
            float v = fb.get(i);
            if (!(v >= 0.0f) || v > 1.0e6f) {
                return false;
            }
        }
        return true;
    }
}
