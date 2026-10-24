package com.treloc.xtreloc.solver.eikonal;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command-line converter: raw Nakajima et al. (2001) table (S-wave, {@code dvs_vel_JGR2001.out}) to the volume CSV
 * read by {@link VelocityVolume3D#fromCsv(Path)}.
 *
 * <pre>
 * java -cp ... com.treloc.xtreloc.solver.eikonal.VelocityVolumeConverter dvs_vel_JGR2001.out out.csv.gz
 * </pre>
 */
public final class VelocityVolumeConverter {

    private VelocityVolumeConverter() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: VelocityVolumeConverter <nakajima raw .out> <output .csv[.gz]>");
            System.exit(2);
        }
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        VelocityVolume3D v = VelocityVolume3D.fromNakajimaRaw(in);
        v.writeCsv(out);
        double[] r = v.range(VelocityVolume3D.CH_VS);
        System.out.printf("%s: %d x %d x %d nodes, lon %.3f..%.3f lat %.3f..%.3f depth %.1f..%.1f km, Vs %.3f..%.3f%n",
            v.name(), v.nLon(), v.nLat(), v.nDepth(), v.minLon(), v.maxLon(), v.minLat(), v.maxLat(),
            v.minDepth(), v.maxDepth(), r[0], r[1]);
    }
}
