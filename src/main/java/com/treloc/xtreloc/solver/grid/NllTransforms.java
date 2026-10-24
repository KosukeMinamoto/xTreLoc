package com.treloc.xtreloc.solver.grid;

import java.io.IOException;

/** Parses the {@code TRANSFORM} line of a NonLinLoc {@code .hdr} file. */
public final class NllTransforms {

    private NllTransforms() {
    }

    /**
     * @return the transform, or {@code null} for {@code NONE} / missing line
     */
    public static NllGeographicTransform parse(String line) throws IOException {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] g = line.trim().split("\\s+");
        if (g.length < 2 || !g[0].equalsIgnoreCase("TRANSFORM") && !g[0].equalsIgnoreCase("TRANS")) {
            return null;
        }
        String type = g[1];
        if (type.equalsIgnoreCase("NONE")) {
            return null;
        }
        if (type.equalsIgnoreCase("SIMPLE")) {
            return parseSimple(line, g);
        }
        if (type.equalsIgnoreCase("LAMBERT") || type.equalsIgnoreCase("LAMBART")) {
            return parseLambert(line, g);
        }
        throw new IOException("Unsupported TRANSFORM '" + type + "' (SIMPLE, LAMBERT or NONE)");
    }

    private static NllSimpleTransform parseSimple(String line, String[] g) throws IOException {
        double lat0 = Double.NaN;
        double lon0 = Double.NaN;
        double rot = 0.0;
        boolean keyed = false;
        for (int i = 2; i + 1 < g.length; i++) {
            try {
                if ("LatOrig".equalsIgnoreCase(g[i])) {
                    lat0 = Double.parseDouble(g[i + 1]);
                    keyed = true;
                } else if ("LongOrig".equalsIgnoreCase(g[i])) {
                    lon0 = Double.parseDouble(g[i + 1]);
                    keyed = true;
                } else if ("RotAngle".equalsIgnoreCase(g[i]) || "RotCW".equalsIgnoreCase(g[i])) {
                    rot = Double.parseDouble(g[i + 1]);
                    keyed = true;
                }
            } catch (NumberFormatException e) {
                throw new IOException("Malformed TRANSFORM SIMPLE line: " + line, e);
            }
        }
        if (!keyed && g.length >= 4) {
            try {
                lat0 = Double.parseDouble(g[2]);
                lon0 = Double.parseDouble(g[3]);
                if (g.length >= 5) {
                    rot = Double.parseDouble(g[4]);
                }
            } catch (NumberFormatException e) {
                throw new IOException("Malformed TRANSFORM SIMPLE line: " + line, e);
            }
        }
        if (Double.isNaN(lat0) || Double.isNaN(lon0)) {
            throw new IOException("TRANSFORM SIMPLE without LatOrig / LongOrig: " + line);
        }
        if (Math.abs(rot) > 1e-9) {
            throw new IOException("TRANSFORM SIMPLE with RotAngle != 0 is not supported: " + line);
        }
        return new NllSimpleTransform(lat0, lon0);
    }

    private static NllLambertTransform parseLambert(String line, String[] g) throws IOException {
        String ell = null;
        double lat0 = Double.NaN;
        double lon0 = Double.NaN;
        double phi1 = Double.NaN;
        double phi2 = Double.NaN;
        double rot = 0.0;
        boolean keyed = false;
        for (int i = 2; i + 1 < g.length; i++) {
            try {
                if ("RefEllipsoid".equalsIgnoreCase(g[i]) || "Ellipsoid".equalsIgnoreCase(g[i])) {
                    ell = g[i + 1];
                    keyed = true;
                } else if ("LatOrig".equalsIgnoreCase(g[i])) {
                    lat0 = Double.parseDouble(g[i + 1]);
                    keyed = true;
                } else if ("LongOrig".equalsIgnoreCase(g[i])) {
                    lon0 = Double.parseDouble(g[i + 1]);
                    keyed = true;
                } else if ("FirstStdParal".equalsIgnoreCase(g[i]) || "FirstStdParallel".equalsIgnoreCase(g[i])) {
                    phi1 = Double.parseDouble(g[i + 1]);
                    keyed = true;
                } else if ("SecondStdParal".equalsIgnoreCase(g[i]) || "SecondStdParallel".equalsIgnoreCase(g[i])) {
                    phi2 = Double.parseDouble(g[i + 1]);
                    keyed = true;
                } else if ("RotCW".equalsIgnoreCase(g[i]) || "RotAngle".equalsIgnoreCase(g[i])) {
                    rot = Double.parseDouble(g[i + 1]);
                    keyed = true;
                }
            } catch (NumberFormatException e) {
                throw new IOException("Malformed TRANSFORM LAMBERT line: " + line, e);
            }
        }
        if (!keyed && g.length >= 7) {
            ell = g[2];
            try {
                lat0 = Double.parseDouble(g[3]);
                lon0 = Double.parseDouble(g[4]);
                phi1 = Double.parseDouble(g[5]);
                phi2 = Double.parseDouble(g[6]);
                if (g.length >= 8) {
                    rot = Double.parseDouble(g[7]);
                }
            } catch (NumberFormatException e) {
                throw new IOException("Malformed TRANSFORM LAMBERT line: " + line, e);
            }
        }
        if (ell == null || Double.isNaN(lat0) || Double.isNaN(lon0) || Double.isNaN(phi1) || Double.isNaN(phi2)) {
            throw new IOException("TRANSFORM LAMBERT needs RefEllipsoid, LatOrig, LongOrig, FirstStdParal, SecondStdParal: "
                + line);
        }
        if (!NllLambertTransform.knownEllipsoid(ell)) {
            throw new IOException("Unknown TRANSFORM LAMBERT ellipsoid '" + ell + "' in: " + line);
        }
        try {
            return NllLambertTransform.of(ell, lat0, lon0, phi1, phi2, rot);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage() + " in: " + line, e);
        }
    }
}
