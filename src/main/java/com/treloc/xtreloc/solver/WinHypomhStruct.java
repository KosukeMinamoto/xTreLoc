package com.treloc.xtreloc.solver;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WIN / hypomh velocity-structure file ({@code struct.tbl}, {@code .aob}).
 *
 * <p>Format (win(1W)): line 1 initial hypocentre {@code 3F10.0} (lat, lon, depth km); line 2 layer count
 * and 3-character name {@code I5,2X,A3}; then {@code n+2} P velocities (km/s) at layer tops {@code 7F10.0};
 * then {@code n+1} thicknesses (km) {@code 7F10.0}; then unused hypocentre uncertainties {@code 4F10.0}.
 * Each layer has a constant positive velocity gradient and velocity is continuous at interfaces
 * (linear interpolation of the discrete P samples). S is {@code Vp / 1.73}.</p>
 *
 * @see <a href="https://wwweic.eri.u-tokyo.ac.jp/WIN/man.ja/win.html">win(1W)</a>
 */
public final class WinHypomhStruct {

    /** Documented WIN {@code Vp/Vs}. */
    public static final double VP_VS = 1.73;

    public final String name;
    public final int nLayers;
    public final double[] depthKm;
    public final double[] vp;
    public final double[] vs;

    public WinHypomhStruct(String name, int nLayers, double[] depthKm, double[] vp, double[] vs) {
        this.name = name;
        this.nLayers = nLayers;
        this.depthKm = depthKm;
        this.vp = vp;
        this.vs = vs;
    }

    public static boolean isWinFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String n = fileName.toLowerCase(Locale.ROOT);
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        return n.endsWith(".aob") || n.endsWith(".win") || n.equals("struct.tbl") || n.equals("struct")
            || n.endsWith(".struct") || n.contains("struct");
    }

    /** True when the non-comment lines look like a WIN structure file rather than TauP {@code .nd}. */
    public static boolean looksLike(List<String> raw) {
        List<String> lines = contentLines(raw);
        if (lines.size() < 3) {
            return false;
        }
        String[] l1 = tokens(lines.get(0));
        String[] l2 = tokens(lines.get(1));
        if (l1.length < 3 || l2.length < 2) {
            return false;
        }
        if (!isPlainNumber(l1[0]) || !isPlainNumber(l1[1]) || !isPlainNumber(l1[2])) {
            return false;
        }
        int n;
        try {
            if (l2[0].indexOf('.') >= 0) {
                return false;
            }
            n = Integer.parseInt(l2[0]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (n < 1 || n > 80) {
            return false;
        }
        return !isPlainNumber(l2[1]) || (l2[1].indexOf('.') < 0 && l2.length == 2);
    }

    public static WinHypomhStruct read(BufferedReader br) throws IOException {
        List<String> raw = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            raw.add(line);
        }
        return parse(raw);
    }

    public static WinHypomhStruct parse(List<String> raw) throws IOException {
        List<String> lines = contentLines(raw);
        if (lines.size() < 3) {
            throw new IOException("WIN structure file: too few lines");
        }
        String[] l2 = tokens(lines.get(1));
        if (l2.length < 2) {
            throw new IOException("WIN structure line 2: expected layer count and name, got: " + lines.get(1));
        }
        int nn;
        try {
            nn = Integer.parseInt(l2[0]);
        } catch (NumberFormatException e) {
            throw new IOException("WIN structure line 2: layer count is not an integer: " + l2[0]);
        }
        if (nn < 1 || nn > 80) {
            throw new IOException("WIN structure: layer count out of range: " + nn);
        }
        String name = l2[1].length() > 3 ? l2[1].substring(0, 3) : l2[1];
        int nVp = nn + 2;
        int nTh = nn + 1;
        List<Double> nums = new ArrayList<>();
        for (int i = 2; i < lines.size(); i++) {
            for (String t : tokens(lines.get(i))) {
                nums.add(parseFortranFloat(t));
            }
        }
        if (nums.size() < nVp + nTh) {
            throw new IOException("WIN structure: expected " + nVp + " P velocities and " + nTh
                + " thicknesses, got " + nums.size() + " numbers");
        }
        double[] vpTop = new double[nVp];
        for (int i = 0; i < nVp; i++) {
            vpTop[i] = nums.get(i);
            if (!(vpTop[i] > 0.0) || !Double.isFinite(vpTop[i])) {
                throw new IOException("WIN structure: P velocity must be positive (index " + i + "): " + vpTop[i]);
            }
        }
        double[] th = new double[nTh];
        for (int i = 0; i < nTh; i++) {
            th[i] = nums.get(nVp + i);
            if (!(th[i] > 0.0) || !Double.isFinite(th[i])) {
                throw new IOException("WIN structure: thickness must be positive (index " + i + "): " + th[i]);
            }
        }
        double[] depth = new double[nVp];
        double[] vp = new double[nVp];
        double[] vs = new double[nVp];
        double z = 0.0;
        for (int i = 0; i < nVp; i++) {
            depth[i] = z;
            vp[i] = vpTop[i];
            vs[i] = vpTop[i] / VP_VS;
            if (i < nTh) {
                z += th[i];
            }
        }
        return new WinHypomhStruct(name, nn, depth, vp, vs);
    }

    static List<String> contentLines(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String line : raw) {
            if (line == null) {
                continue;
            }
            String t = stripComment(line).trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        int slash = line.indexOf("//");
        int cut = -1;
        if (hash >= 0 && (slash < 0 || hash < slash)) {
            cut = hash;
        } else if (slash >= 0) {
            cut = slash;
        }
        return cut >= 0 ? line.substring(0, cut) : line;
    }

    private static String[] tokens(String line) {
        return line.trim().split("\\s+");
    }

    private static boolean isPlainNumber(String s) {
        try {
            Double.parseDouble(s.replace('D', 'E').replace('d', 'e'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double parseFortranFloat(String t) throws IOException {
        try {
            return Double.parseDouble(t.replace('D', 'E').replace('d', 'e'));
        } catch (NumberFormatException e) {
            throw new IOException("WIN structure: not a number: " + t);
        }
    }
}
