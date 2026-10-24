package com.treloc.xtreloc.solver.eikonal;

import java.util.Arrays;

/**
 * Factored eikonal solver on a uniform Cartesian grid (Gauss-Seidel fast sweeping, 8 sweep directions).
 *
 * <p>Solves {@code |grad T| = s(x)} with the factorization {@code T = T0 * tau}, where
 * {@code T0 = sSrc * |x - x_src|} is the travel time of a homogeneous medium with the source slowness
 * {@code sSrc}. The factor {@code tau} is smooth at the source, which removes the first-order error that the
 * plain solver makes around the point source (Fomel et al. 2009; Treister and Haber 2016).</p>
 *
 * <p>Discretization (Godunov): for every axis {@code d} the derivative of {@code T} is
 * {@code T_d = tau dT0/dx_d + T0 dtau/dx_d}, with {@code dT0/dx_d} analytic and {@code dtau/dx_d} a one-sided
 * difference. With {@code sigma = +1} (information from the lower neighbor) or {@code sigma = -1} (upper neighbor)
 * the outward flux is {@code A tau - B} with {@code A = sigma dT0/dx_d + T0/h} and {@code B = T0 tau_nb / h}. The
 * node value solves {@code sum_d max(0, A_d- tau - B_d-, A_d+ tau - B_d+)^2 = s^2}; the left side is convex and
 * non-decreasing in {@code tau}, so a Newton iteration started from an upper bound converges monotonically.</p>
 *
 * <p>Grid-local coordinates: node {@code (i, j, k)} is at {@code (i hx, j hy, k hz)}; the node index is
 * {@code (k * ny + j) * nx + i}.</p>
 */
final class FactoredFastSweeping {

    private FactoredFastSweeping() {}

    private static final int MAX_NEWTON = 40;

    /**
     * @param slow      node slowness (s/km)
     * @param su,sv,sw  source position in grid-local coordinates (inside the grid)
     * @param sSrc      slowness used for the factor {@code T0}
     * @param maxSweeps maximum number of full 8-direction sweeps
     * @param tol       stop when the largest relative change of tau in one full sweep is below this
     * @return {@code tau} for every node
     */
    static double[] solveTau(int nx, int ny, int nz, double hx, double hy, double hz, float[] slow,
                             double su, double sv, double sw, double sSrc, int maxSweeps, double tol) {
        final int n = nx * ny * nz;
        final double[] t0 = new double[n];
        final double[] tau = new double[n];
        Arrays.fill(tau, Double.POSITIVE_INFINITY);
        final boolean[] fixed = new boolean[n];

        for (int k = 0; k < nz; k++) {
            double dz = k * hz - sw;
            for (int j = 0; j < ny; j++) {
                double dy = j * hy - sv;
                for (int i = 0; i < nx; i++) {
                    double dx = i * hx - su;
                    int idx = (k * ny + j) * nx + i;
                    t0[idx] = sSrc * Math.sqrt(dx * dx + dy * dy + dz * dz);
                    // nodes in the source box (|d| <= h in every direction) are exact: tau = 1
                    if (Math.abs(dx) <= hx * (1 + 1e-9) && Math.abs(dy) <= hy * (1 + 1e-9)
                        && Math.abs(dz) <= hz * (1 + 1e-9)) {
                        tau[idx] = 1.0;
                        fixed[idx] = true;
                    }
                }
            }
        }

        final double ss = sSrc * sSrc;
        final double invHx = 1.0 / hx;
        final double invHy = 1.0 / hy;
        final double invHz = 1.0 / hz;
        // stencils: [dim*2 + side] ; side 0 = lower neighbor (sigma=+1), 1 = upper neighbor (sigma=-1)
        final double[] sa = new double[6];
        final double[] sb = new double[6];

        for (int sweep = 0; sweep < maxSweeps; sweep++) {
            double maxChange = 0.0;
            for (int dirK = 0; dirK < 2; dirK++) {
                for (int dirJ = 0; dirJ < 2; dirJ++) {
                    for (int dirI = 0; dirI < 2; dirI++) {
                        int k0 = dirK == 0 ? 0 : nz - 1;
                        int kStep = dirK == 0 ? 1 : -1;
                        int j0 = dirJ == 0 ? 0 : ny - 1;
                        int jStep = dirJ == 0 ? 1 : -1;
                        int i0 = dirI == 0 ? 0 : nx - 1;
                        int iStep = dirI == 0 ? 1 : -1;
                        for (int kk = 0, k = k0; kk < nz; kk++, k += kStep) {
                            double dz = k * hz - sw;
                            for (int jj = 0, j = j0; jj < ny; jj++, j += jStep) {
                                double dy = j * hy - sv;
                                for (int ii = 0, i = i0; ii < nx; ii++, i += iStep) {
                                    int idx = (k * ny + j) * nx + i;
                                    if (fixed[idx]) {
                                        continue;
                                    }
                                    double t0c = t0[idx];
                                    double dx = i * hx - su;
                                    double s = slow[idx];

                                    boolean any = false;
                                    double bound = Double.POSITIVE_INFINITY;
                                    for (int d = 0; d < 3; d++) {
                                        double t0d;
                                        double invH;
                                        int stride;
                                        boolean hasLo;
                                        boolean hasHi;
                                        if (d == 0) {
                                            t0d = ss * dx / t0c; invH = invHx; stride = 1;
                                            hasLo = i > 0; hasHi = i < nx - 1;
                                        } else if (d == 1) {
                                            t0d = ss * dy / t0c; invH = invHy; stride = nx;
                                            hasLo = j > 0; hasHi = j < ny - 1;
                                        } else {
                                            t0d = ss * dz / t0c; invH = invHz; stride = nx * ny;
                                            hasLo = k > 0; hasHi = k < nz - 1;
                                        }
                                        // lower neighbor (sigma = +1)
                                        double a = Double.NaN;
                                        double b = Double.NaN;
                                        if (hasLo) {
                                            double tn = tau[idx - stride];
                                            double ap = t0d + t0c * invH;
                                            if (tn < Double.POSITIVE_INFINITY && ap > 1e-14) {
                                                a = ap;
                                                b = t0c * tn * invH;
                                            }
                                        }
                                        sa[2 * d] = a;
                                        sb[2 * d] = b;
                                        if (a == a) {
                                            any = true;
                                            double ub = (b + s) / a;
                                            if (ub < bound) {
                                                bound = ub;
                                            }
                                        }
                                        // upper neighbor (sigma = -1)
                                        a = Double.NaN;
                                        b = Double.NaN;
                                        if (hasHi) {
                                            double tn = tau[idx + stride];
                                            double ap = -t0d + t0c * invH;
                                            if (tn < Double.POSITIVE_INFINITY && ap > 1e-14) {
                                                a = ap;
                                                b = t0c * tn * invH;
                                            }
                                        }
                                        sa[2 * d + 1] = a;
                                        sb[2 * d + 1] = b;
                                        if (a == a) {
                                            any = true;
                                            double ub = (b + s) / a;
                                            if (ub < bound) {
                                                bound = ub;
                                            }
                                        }
                                    }
                                    if (!any) {
                                        continue;
                                    }

                                    // Newton iteration from the upper bound on the convex, non-decreasing
                                    // H(tau) = sum_d max(0, A tau - B)^2 - s^2.
                                    double t = bound;
                                    final double s2 = s * s;
                                    for (int it = 0; it < MAX_NEWTON; it++) {
                                        double f = -s2;
                                        double fp = 0.0;
                                        for (int d = 0; d < 3; d++) {
                                            double c = 0.0;
                                            double slope = 0.0;
                                            for (int side = 0; side < 2; side++) {
                                                double a = sa[2 * d + side];
                                                if (a == a) {
                                                    double v = a * t - sb[2 * d + side];
                                                    if (v > c) {
                                                        c = v;
                                                        slope = a;
                                                    }
                                                }
                                            }
                                            f += c * c;
                                            fp += 2.0 * c * slope;
                                        }
                                        if (f <= 1e-11 * s2 || fp <= 0.0) {
                                            break;
                                        }
                                        double step = f / fp;
                                        t -= step;
                                        if (step <= 1e-10 * t) {
                                            break;
                                        }
                                    }
                                    if (!(t > 0.0)) {
                                        continue;
                                    }
                                    double old = tau[idx];
                                    if (t < old) {
                                        double change = Double.isInfinite(old) ? 1.0 : (old - t) / t;
                                        if (change > maxChange) {
                                            maxChange = change;
                                        }
                                        tau[idx] = t;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (maxChange < tol) {
                break;
            }
        }
        return tau;
    }
}
