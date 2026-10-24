package com.treloc.xtreloc.solver.grid;

import com.treloc.xtreloc.solver.eikonal.LocalFrame;
import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Samples a {@link LayerCakeModel} (with strike / dip) as a geographic {@link VelocityVolume3D} for display. */
public final class LayerCakeVolumes {

    public static final String CH_VP = "vp_km_s";

    private LayerCakeVolumes() {
    }

    /**
     * Volume over the lon / lat box and {@code minDepthKm..maxDepthKm} (default min = 0), channels {@code vs_km_s} and {@code vp_km_s}.
     * Horizontal layers use a 2 x 2 horizontal grid with the profile's own depth samples (sharp interfaces);
     * dipping layers are sampled on a regular grid.
     *
     * @param nHoriz number of nodes along longitude and latitude for dipping layers
     * @param nDepth number of depth nodes for dipping layers
     */
    public static VelocityVolume3D build(LayerCakeModel m, String name, double lonMin, double lonMax,
                                         double latMin, double latMax, double maxDepthKm, int nHoriz, int nDepth) {
        return build(m, name, lonMin, lonMax, latMin, latMax, 0.0, maxDepthKm, nHoriz, nDepth);
    }

    public static VelocityVolume3D build(LayerCakeModel m, String name, double lonMin, double lonMax,
                                         double latMin, double latMax, double minDepthKm, double maxDepthKm,
                                         int nHoriz, int nDepth) {
        if (!(lonMax > lonMin) || !(latMax > latMin) || !(maxDepthKm > minDepthKm)) {
            throw new IllegalArgumentException("Region must satisfy min < max and max depth > min depth");
        }
        if (m.dipDeg() == 0.0) {
            double[] ds = m.depthSamples();
            double[] deps = new double[ds.length + 2];
            int n = 0;
            deps[n++] = minDepthKm;
            for (double d : ds) {
                if (d > minDepthKm + 1e-9 && d < maxDepthKm - 1e-9) {
                    deps[n++] = d;
                }
            }
            deps[n++] = maxDepthKm;
            deps = Arrays.copyOf(deps, n);
            return sample(m, name, new double[] {lonMin, lonMax}, new double[] {latMin, latMax}, deps);
        }
        int nh = Math.max(5, nHoriz);
        int nd = Math.max(11, nDepth);
        double[] lons = new double[nh];
        double[] lats = new double[nh];
        for (int i = 0; i < nh; i++) {
            lons[i] = lonMin + (lonMax - lonMin) * i / (nh - 1);
            lats[i] = latMin + (latMax - latMin) * i / (nh - 1);
        }
        double[] deps = new double[nd];
        for (int k = 0; k < nd; k++) {
            deps[k] = minDepthKm + (maxDepthKm - minDepthKm) * k / (nd - 1);
        }
        return sample(m, name, lons, lats, deps);
    }

    private static VelocityVolume3D sample(LayerCakeModel m, String name, double[] lons, double[] lats, double[] deps) {
        int nx = lons.length;
        int ny = lats.length;
        int nz = deps.length;
        float[] vs = new float[nx * ny * nz];
        float[] vp = new float[nx * ny * nz];
        // the dip is referenced to the centre of the region, like the travel-time grids
        LocalFrame frame = new LocalFrame(0.5 * (lats[0] + lats[ny - 1]), 0.5 * (lons[0] + lons[nx - 1]));
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                double[] xy = frame.toXY(lats[j], lons[i]);
                for (int k = 0; k < nz; k++) {
                    int idx = (k * ny + j) * nx + i;
                    vs[idx] = (float) m.velocity(Phase.S, xy[0], xy[1], deps[k]);
                    vp[idx] = (float) m.velocity(Phase.P, xy[0], xy[1], deps[k]);
                }
            }
        }
        Map<String, float[]> ch = new LinkedHashMap<>();
        ch.put(VelocityVolume3D.CH_VS, vs);
        ch.put(CH_VP, vp);
        return VelocityVolume3D.of(name, lons, lats, deps, ch);
    }
}
