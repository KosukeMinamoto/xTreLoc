package com.treloc.xtreloc.app.gui.service;

import java.util.Locale;

/**
 * Maps SAC {@code KCMPNM} strings onto a vertical / first-horizontal / second-horizontal role.
 * Naming varies by network: UD / Z / V, N / HA / H1, E / HB / H2, and so on.
 */
public final class SacComponentClassifier {

    public enum Role {
        VERTICAL,
        H1,
        H2,
        HORIZONTAL,
        OTHER
    }

    private SacComponentClassifier() {}

    public static String normalize(String kcmpnm) {
        return kcmpnm == null ? "" : kcmpnm.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    public static Role classify(String kcmpnm) {
        String c = normalize(kcmpnm);
        if (c.isEmpty()) {
            return Role.OTHER;
        }
        if (isVertical(c)) {
            return Role.VERTICAL;
        }
        if (isH1(c)) {
            return Role.H1;
        }
        if (isH2(c)) {
            return Role.H2;
        }
        if (isHorizontal(c)) {
            return Role.HORIZONTAL;
        }
        return Role.OTHER;
    }

    public static boolean isVertical(String cmpUpper) {
        String c = normalize(cmpUpper);
        if (c.isEmpty()) {
            return false;
        }
        if (c.equals("UD") || c.equals("VU") || c.equals("VERT")) {
            return true;
        }
        if (c.endsWith("UD") || c.endsWith("HZ") || c.endsWith("BHZ") || c.endsWith("HHZ")
            || c.endsWith("EHZ") || c.endsWith("SHZ")) {
            return true;
        }
        char last = c.charAt(c.length() - 1);
        return last == 'Z' || last == 'U' || last == 'V';
    }

    public static boolean isH1(String cmpUpper) {
        String c = normalize(cmpUpper);
        if (c.isEmpty() || isVertical(c)) {
            return false;
        }
        if (tokenIs(c, "N", "NS", "X", "H1", "HA", "HN", "HX", "1", "A")) {
            return true;
        }
        return c.endsWith("HHN") || c.endsWith("BHN") || c.endsWith("EHN") || c.endsWith("SHN")
            || c.endsWith("HN") || c.endsWith("H1") || c.endsWith("HA") || c.endsWith("HX")
            || endsWithToken(c, "N") || endsWithToken(c, "X");
    }

    public static boolean isH2(String cmpUpper) {
        String c = normalize(cmpUpper);
        if (c.isEmpty() || isVertical(c)) {
            return false;
        }
        if (tokenIs(c, "E", "EW", "Y", "H2", "HB", "HE", "HY", "2", "B")) {
            return true;
        }
        return c.endsWith("HHE") || c.endsWith("BHE") || c.endsWith("EHE") || c.endsWith("SHE")
            || c.endsWith("HE") || c.endsWith("H2") || c.endsWith("HB") || c.endsWith("HY")
            || endsWithToken(c, "E") || endsWithToken(c, "Y");
    }

    private static boolean tokenIs(String c, String... tokens) {
        for (String t : tokens) {
            if (c.equals(t)) {
                return true;
            }
        }
        return false;
    }

    /** Last character match only for short codes (avoids treating "EHN" as H2 via a lone 'E'). */
    private static boolean endsWithToken(String c, String token) {
        return c.length() <= 3 && c.endsWith(token);
    }

    public static boolean isHorizontal(String cmpUpper) {
        String c = normalize(cmpUpper);
        if (c.isEmpty() || isVertical(c)) {
            return false;
        }
        if (isH1(c) || isH2(c)) {
            return true;
        }
        char last = c.charAt(c.length() - 1);
        return last == 'N' || last == 'E' || last == 'X' || last == 'Y' || last == '1' || last == '2'
            || c.contains("H");
    }
}
