package com.treloc.xtreloc.app.gui.util;

/**
 * Parsing / formatting of the optional "Freq min / Freq max (Hz)" fields shared by the picking and trace-processing UIs.
 * <p>
 * Empty corner = no limit on that edge: low only → high-pass, high only → low-pass, both → band-pass, none → no filter.
 */
public final class FrequencySpec {

    /** Result of {@link #parse(String, String)}: either {@code error} is set, or {@code min}/{@code max} are valid (nullable). */
    public static final class Result {
        public final Float min;
        public final Float max;
        public final String error;

        Result(Float min, Float max, String error) {
            this.min = min;
            this.max = max;
            this.error = error;
        }
    }

    private FrequencySpec() {}

    public static Result parse(String lowRaw, String highRaw) {
        String lo = lowRaw == null ? "" : lowRaw.trim();
        String hi = highRaw == null ? "" : highRaw.trim();
        if (lo.isEmpty() && hi.isEmpty()) {
            return new Result(null, null, null);
        }
        try {
            if (lo.isEmpty()) {
                float h = Float.parseFloat(hi);
                if (h <= 0) {
                    return new Result(null, null, "Frequency must be positive.");
                }
                return new Result(null, h, null);
            }
            if (hi.isEmpty()) {
                float l = Float.parseFloat(lo);
                if (l <= 0) {
                    return new Result(null, null, "Frequency must be positive.");
                }
                return new Result(l, null, null);
            }
            float l = Float.parseFloat(lo);
            float h = Float.parseFloat(hi);
            if (l <= 0 || h <= 0) {
                return new Result(null, null, "Frequencies must be positive.");
            }
            if (l >= h) {
                return new Result(null, null, "Freq min must be less than freq max.");
            }
            return new Result(l, h, null);
        } catch (NumberFormatException ex) {
            return new Result(null, null, "Invalid frequency value.");
        }
    }

    /** Text for a frequency field ({@code ""} for null, integers without decimals). */
    public static String toFieldText(Float f) {
        if (f == null) {
            return "";
        }
        float v = f;
        if (Math.abs(v - Math.rint(v)) < 1e-6f) {
            return String.valueOf((int) Math.rint(v));
        }
        return String.valueOf(v);
    }

    /** Human-readable description of the applied filter. */
    public static String describe(Float min, Float max) {
        if (min == null && max == null) {
            return "no filter";
        }
        if (min != null && max != null) {
            return toFieldText(min) + "–" + toFieldText(max) + " Hz band-pass";
        }
        if (min != null) {
            return "high-pass " + toFieldText(min) + " Hz";
        }
        return "low-pass " + toFieldText(max) + " Hz";
    }
}
