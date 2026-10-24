package com.treloc.xtreloc.app.gui.model;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

/**
 * Reference to a velocity model that can be visualized in the 3D map: the bundled 3-D grid, a 3-D CSV file, or a
 * 1-D model (same specification as the Solver: bundled name such as {@code prem.nd}, or a file path).
 */
public final class VelocityModelSpec {

    public enum Kind {
        /** Bundled 3-D grid (classpath resource). */
        BUNDLED_3D,
        /** 3-D CSV grid on disk ({@code lon,lat,depth_km,vs_km_s[,...]}, optionally gzip-compressed). */
        FILE_3D,
        /** 1-D model (TauP .nd / .tvel, HYPO .aob), shown as a layer cake. */
        MODEL_1D
    }

    public static final String NAKAJIMA_LABEL = "Tohoku Vs (Nakajima et al., 2001)";
    public static final String NAKAJIMA_RESOURCE = "velocity-models-3d/nakajima2001_tohoku_vs.csv.gz";

    public final Kind kind;
    public final String label;
    /** Classpath resource, file path or model name (as understood by {@code Raytrace1D.load}). */
    public final String ref;

    private VelocityModelSpec(Kind kind, String label, String ref) {
        this.kind = kind;
        this.label = label;
        this.ref = ref;
    }

    public static VelocityModelSpec bundledNakajima() {
        return new VelocityModelSpec(Kind.BUNDLED_3D, NAKAJIMA_LABEL, NAKAJIMA_RESOURCE);
    }

    /** 1-D model given as in the Solver (bundled name or file path). */
    public static VelocityModelSpec forModelRef(String ref) {
        String r = ref.trim();
        return new VelocityModelSpec(Kind.MODEL_1D, new File(r).getName(), r);
    }

    /** A file picked by the user: 3-D CSV by extension, otherwise a 1-D model. */
    public static VelocityModelSpec fromFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".csv") || n.endsWith(".csv.gz") || n.endsWith(".gz")) {
            return new VelocityModelSpec(Kind.FILE_3D, f.getName(), f.getAbsolutePath());
        }
        return new VelocityModelSpec(Kind.MODEL_1D, f.getName(), f.getAbsolutePath());
    }

    public boolean is3D() {
        return kind != Kind.MODEL_1D;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VelocityModelSpec)) {
            return false;
        }
        VelocityModelSpec s = (VelocityModelSpec) o;
        return kind == s.kind && Objects.equals(ref, s.ref);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, ref);
    }

    @Override
    public String toString() {
        return label;
    }
}
