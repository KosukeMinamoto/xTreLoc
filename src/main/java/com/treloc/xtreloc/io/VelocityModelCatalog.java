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
        if (value == null) return false;
        String v = value.trim();
        if (v.isEmpty()) return false;
        if (BUNDLED_MODELS.contains(v)) return true;
        if (v.startsWith(RESOURCE_DIR)) {
            return BUNDLED_MODELS.contains(v.substring(RESOURCE_DIR.length()));
        }
        return false;
    }

    public static String toResourcePath(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith(RESOURCE_DIR)) return v;
        if (BUNDLED_MODELS.contains(v)) return RESOURCE_DIR + v;
        return v;
    }
}
