package com.treloc.xtreloc.solver;

import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;

/**
 * Station-pair row layout in memory and on disk:
 * {@code [stnk_idx, stnl_idx, lagt, weight, phaseCode]} with {@code phaseCode} 0 = P, 1 = S.
 * File lines are {@code stnk stnl lagt coef PHASE}.
 */
public final class DatPair {

    public static final double P = 0.0;
    public static final double S = 1.0;

    private DatPair() {}

    public static double code(String token) {
        if (token == null || token.isBlank()) {
            return S;
        }
        char c = Character.toUpperCase(token.trim().charAt(0));
        return c == 'P' ? P : S;
    }

    public static String label(double[] row) {
        return isP(row) ? "P" : "S";
    }

    public static String label(double phaseCode) {
        return phaseCode < 0.5 ? "P" : "S";
    }

    public static boolean isP(double[] row) {
        return row != null && row.length > 4 && row[4] < 0.5;
    }

    public static boolean isS(double[] row) {
        return !isP(row);
    }

    public static Phase phase(double[] row) {
        return isP(row) ? Phase.P : Phase.S;
    }

    public static boolean samePhase(double[] a, double[] b) {
        return isP(a) == isP(b);
    }

    public static boolean finiteTravel(double t) {
        return Double.isFinite(t) && t > 0.0 && t < 1.0e100;
    }

    public static boolean finitePair(double[] t, int stnk, int stnl) {
        return t != null && stnk >= 0 && stnl >= 0 && stnk < t.length && stnl < t.length
            && finiteTravel(t[stnk]) && finiteTravel(t[stnl]);
    }

    /** Predicted {@code t(stnl) − t(stnk)}. */
    public static double calcLag(double[] t, int stnk, int stnl) {
        return t[stnl] - t[stnk];
    }

    /** Predicted {@code t(stnl) − t(stnk)} for the row's phase. */
    public static double calcLag(double[] row, double[] tP, double[] tS) {
        int k = (int) row[0];
        int l = (int) row[1];
        double[] t = isP(row) ? tP : tS;
        return calcLag(t, k, l);
    }

    /**
     * Predicted triple difference for events {@code (0, 1)} and stations {@code (k, l)}:
     * {@code (t1[l] − t1[k]) − (t0[l] − t0[k])}.
     * Must match {@code tdTime = lag(eve1) − lag(eve0)} built in clustering.
     */
    public static double calcTriple(double[] tEve0, double[] tEve1, int stnk, int stnl) {
        return calcLag(tEve1, stnk, stnl) - calcLag(tEve0, stnk, stnl);
    }

    public static double[] residual(double[][] lagTable, double[] tP, double[] tS) {
        double[] out = new double[lagTable.length];
        for (int i = 0; i < lagTable.length; i++) {
            int k = (int) lagTable[i][0];
            int l = (int) lagTable[i][1];
            double[] t = timesFor(lagTable[i], tP, tS);
            if (!finitePair(t, k, l)) {
                out[i] = Double.NaN;
                continue;
            }
            out[i] = lagTable[i][2] - calcLag(t, k, l);
        }
        return out;
    }

    public static double[] timesFor(double[] row, double[] tP, double[] tS) {
        return isP(row) ? tP : tS;
    }

    public static double[][] dtdrFor(double[] row, double[][] dP, double[][] dS) {
        return isP(row) ? dP : dS;
    }

    /** True if any row is P / S. Scans once. */
    public static boolean[] phasesPresent(double[][] lagTable) {
        boolean p = false;
        boolean s = false;
        if (lagTable != null) {
            for (double[] row : lagTable) {
                if (isP(row)) {
                    p = true;
                } else {
                    s = true;
                }
                if (p && s) {
                    break;
                }
            }
        }
        return new boolean[] { p, s };
    }
}
