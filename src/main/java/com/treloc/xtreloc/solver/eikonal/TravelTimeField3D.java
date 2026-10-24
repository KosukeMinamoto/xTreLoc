package com.treloc.xtreloc.solver.eikonal;

/**
 * Travel-time field of one source (usually a station, by reciprocity) on an {@link EikonalGrid3D}.
 *
 * <p>The grid stores the smooth factor {@code tau}; the travel time at any point is
 * {@code T = T0 * tau} with {@code T0 = sSrc * |x - x_src|}. Interpolating {@code tau} (instead of {@code T})
 * keeps the cone-shaped singularity at the source exact, so values and gradients are accurate close to the
 * source as well.</p>
 *
 * <p>Instances are immutable and thread-safe.</p>
 */
public final class TravelTimeField3D {

    private final EikonalGrid3D grid;
    private final float[] tau;
    /** Source in grid-local model coordinates (node (i,j,k) is at (i hx, j hy, k hz)). */
    private final double su;
    private final double sv;
    private final double sw;
    private final double sSrc;

    TravelTimeField3D(EikonalGrid3D grid, float[] tau, double su, double sv, double sw, double sSrc) {
        this.grid = grid;
        this.tau = tau;
        this.su = su;
        this.sv = sv;
        this.sw = sw;
        this.sSrc = sSrc;
    }

    public EikonalGrid3D grid() {
        return grid;
    }

    /**
     * Travel time (s) from the source to the point (local Cartesian km, physical depth km), or {@code NaN} if the
     * point is outside the grid.
     */
    public double time(double xKm, double yKm, double depthKm) {
        double[] out = new double[4];
        return evaluate(xKm, yKm, depthKm, out) ? out[0] : Double.NaN;
    }

    /** Travel time (s) at a geographic position; requires a grid built with a {@link LocalFrame}. */
    public double timeLatLon(double lat, double lon, double depthKm) {
        double[] xy = frame().toXY(lat, lon);
        return time(xy[0], xy[1], depthKm);
    }

    /**
     * Travel time and gradient {@code (dT/dx, dT/dy, dT/ddepth)} in s/km (east, north, physical depth down).
     *
     * @param out receives {@code {T, dTdx, dTdy, dTdDepth}} (length >= 4)
     * @return false if the point is outside the grid
     */
    public boolean timeAndGradient(double xKm, double yKm, double depthKm, double[] out) {
        if (!evaluate(xKm, yKm, depthKm, out)) {
            return false;
        }
        out[3] *= grid.dModelDepthdDepth(depthKm);
        return true;
    }

    private LocalFrame frame() {
        LocalFrame f = grid.frame();
        if (f == null) {
            throw new IllegalStateException("Grid has no geographic frame");
        }
        return f;
    }

    /** out = {T, dT/dx, dT/dy, dT/dzModel}. */
    private boolean evaluate(double xKm, double yKm, double depthKm, double[] out) {
        final int nx = grid.nx();
        final int ny = grid.ny();
        final int nz = grid.nz();
        final double hx = grid.hx();
        final double hy = grid.hy();
        final double hz = grid.hz();
        double u = xKm - grid.xMin();
        double v = yKm - grid.yMin();
        double w = grid.toModelDepth(depthKm);
        double eps = 1e-9;
        if (u < -eps || v < -eps || w < -eps
            || u > (nx - 1) * hx + eps || v > (ny - 1) * hy + eps || w > (nz - 1) * hz + eps
            || Double.isNaN(u + v + w)) {
            return false;
        }
        int i = (int) Math.floor(u / hx);
        int j = (int) Math.floor(v / hy);
        int k = (int) Math.floor(w / hz);
        i = Math.max(0, Math.min(nx - 2, i));
        j = Math.max(0, Math.min(ny - 2, j));
        k = Math.max(0, Math.min(nz - 2, k));
        double fx = Math.max(0.0, Math.min(1.0, u / hx - i));
        double fy = Math.max(0.0, Math.min(1.0, v / hy - j));
        double fz = Math.max(0.0, Math.min(1.0, w / hz - k));

        int b = (k * ny + j) * nx + i;
        int sy = nx;
        int sz = nx * ny;
        double c000 = tau[b];
        double c100 = tau[b + 1];
        double c010 = tau[b + sy];
        double c110 = tau[b + sy + 1];
        double c001 = tau[b + sz];
        double c101 = tau[b + sz + 1];
        double c011 = tau[b + sz + sy];
        double c111 = tau[b + sz + sy + 1];

        double c00 = c000 + (c100 - c000) * fx;
        double c10 = c010 + (c110 - c010) * fx;
        double c01 = c001 + (c101 - c001) * fx;
        double c11 = c011 + (c111 - c011) * fx;
        double c0 = c00 + (c10 - c00) * fy;
        double c1 = c01 + (c11 - c01) * fy;
        double tauV = c0 + (c1 - c0) * fz;

        // derivatives of the trilinear interpolant
        double dx0 = (c100 - c000) * (1 - fy) + (c110 - c010) * fy;
        double dx1 = (c101 - c001) * (1 - fy) + (c111 - c011) * fy;
        double dTaudx = (dx0 * (1 - fz) + dx1 * fz) / hx;
        double dTaudy = ((c10 - c00) * (1 - fz) + (c11 - c01) * fz) / hy;
        double dTaudz = (c1 - c0) / hz;

        double dx = u - su;
        double dy = v - sv;
        double dz = w - sw;
        double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double t0 = sSrc * r;
        out[0] = t0 * tauV;
        if (r > 0.0) {
            double g0x = sSrc * dx / r;
            double g0y = sSrc * dy / r;
            double g0z = sSrc * dz / r;
            out[1] = tauV * g0x + t0 * dTaudx;
            out[2] = tauV * g0y + t0 * dTaudy;
            out[3] = tauV * g0z + t0 * dTaudz;
        } else {
            out[1] = 0.0;
            out[2] = 0.0;
            out[3] = 0.0;
        }
        return true;
    }
}
