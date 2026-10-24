package com.treloc.xtreloc.io;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Bundled velocity-model catalog.
 */
public final class VelocityModelCatalog {

    public static final String RESOURCE_DIR = "velocity-models/";
    public static final String DEFAULT_MODEL = "prem.nd";

    private static final List<String> BUNDLED_MODELS = Collections.unmodifiableList(Arrays.asList(
        "prem.nd",
        "pwdk.nd",
        "alfs.nd",
        "sp6.nd",
        "herrin.nd",
        "1066a.nd",
        "1066b.nd",
        "ak135.tvel",
        "iasp91.tvel"
    ));

    private VelocityModelCatalog() {
    }

    public static String[] comboModels() {
        return BUNDLED_MODELS.toArray(new String[0]);
    }

    public static boolean isBundledModelName(String value) {
        return bundledFileName(value) != null;
    }

    /** {@code prem} / {@code prem.nd} / {@code velocity-models/prem.nd} → {@code prem.nd}. */
    public static String bundledFileName(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (v.startsWith(RESOURCE_DIR)) {
            v = v.substring(RESOURCE_DIR.length());
        }
        if (BUNDLED_MODELS.contains(v)) {
            return v;
        }
        for (String m : BUNDLED_MODELS) {
            int dot = m.lastIndexOf('.');
            if (dot > 0 && m.substring(0, dot).equalsIgnoreCase(v)) {
                return m;
            }
        }
        return null;
    }

    public static String toResourcePath(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith(RESOURCE_DIR)) return v;
        String bundled = bundledFileName(v);
        if (bundled != null) return RESOURCE_DIR + bundled;
        return v;
    }
}
