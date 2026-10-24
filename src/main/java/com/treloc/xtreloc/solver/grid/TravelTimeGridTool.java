package com.treloc.xtreloc.solver.grid;

import com.treloc.xtreloc.io.StationRepository;
import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Command-line builder of station-wise travel-time grids (NonLinLoc {@code TIME} format) from a 1-D layer cake
 * (optionally with a strike / dip). Run with {@code ./gradlew gridTool -PgridToolArgs="..."}.
 */
public final class TravelTimeGridTool {

    private TravelTimeGridTool() {
    }

    private static final String USAGE = String.join("\n",
        "Usage: TravelTimeGridTool --model <spec> --stations <station.tbl> --out <dir> [options]",
        "  --model <spec>       prem.nd, iasp91.tvel, ak135.tvel, or a .nd/.tvel file with Vp and Vs",
        "  --stations <file>    xTreLoc station table (code lat lon elevation_m pc sc)",
        "  --out <dir>          output directory (<root>.<P|S>.<station>.time.hdr/.buf are written)",
        "  --root <name>        file name root (default: model name without extension)",
        "  --phases P,S         phases (default P,S)",
        "  --strike <deg>       strike of the layering, azimuth from north (default 0)",
        "  --dip <deg>          dip of the layering, 0..85 (default 0 = horizontal layers)",
        "  --center <lat,lon>   grid centre (default: centre of the stations)",
        "  --half-width <x[,y]> half width in km (default: fit the stations plus --margin)",
        "  --margin <km>        margin around the stations when fitting (default 30)",
        "  --depth <km>         maximum depth (default 60)",
        "  --spacing <h[,z]>    horizontal [,depth] spacing in km (default 1.0; NonLinLoc dx=dy [,dz])",
        "  --spacing-z <km>     depth spacing (overrides the second value of --spacing)",
        "  --no-flatten         flat layers instead of the spherical Earth (Earth flattening)",
        "  --big-endian         write big-endian .buf files (default little endian)");

    public static void main(String[] args) throws Exception {
        String model = null;
        String stationFile = null;
        String out = null;
        String root = null;
        String phasesArg = "P,S";
        double strike = 0.0;
        double dip = 0.0;
        double[] center = null;
        double[] half = null;
        double margin = 30.0;
        double depth = 60.0;
        double spacing = 1.0;
        Double spacingZ = null;
        boolean flatten = true;
        ByteOrder order = ByteOrder.LITTLE_ENDIAN;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--model": model = args[++i]; break;
                case "--stations": stationFile = args[++i]; break;
                case "--out": out = args[++i]; break;
                case "--root": root = args[++i]; break;
                case "--phases": phasesArg = args[++i]; break;
                case "--strike": strike = Double.parseDouble(args[++i]); break;
                case "--dip": dip = Double.parseDouble(args[++i]); break;
                case "--center": center = numbers(args[++i], 2); break;
                case "--half-width": half = numbers(args[++i], -1); break;
                case "--margin": margin = Double.parseDouble(args[++i]); break;
                case "--depth": depth = Double.parseDouble(args[++i]); break;
                case "--spacing": {
                    double[] s = numbers(args[++i], -1);
                    spacing = s[0];
                    if (s.length > 1) {
                        spacingZ = s[1];
                    }
                    break;
                }
                case "--spacing-z": spacingZ = Double.parseDouble(args[++i]); break;
                case "--no-flatten": flatten = false; break;
                case "--big-endian": order = ByteOrder.BIG_ENDIAN; break;
                case "--help":
                case "-h":
                    System.out.println(USAGE);
                    return;
                default:
                    System.err.println("Unknown option: " + a + "\n" + USAGE);
                    System.exit(2);
            }
        }
        if (model == null || stationFile == null || out == null) {
            System.err.println(USAGE);
            System.exit(2);
        }
        Set<Phase> phases = EnumSet.noneOf(Phase.class);
        for (String p : phasesArg.split(",")) {
            phases.add(Phase.valueOf(p.trim().toUpperCase(Locale.ROOT)));
        }

        LayerCakeModel cake = LayerCakeModel.load(model).withStrikeDip(strike, dip);
        StationRepository repo = StationRepository.load(Paths.get(stationFile));
        List<TravelTimeGridBuilder.StationPoint> stations = new ArrayList<>();
        String[] codes = repo.getCodes();
        double[][] tbl = repo.getStationTable();
        for (int i = 0; i < codes.length; i++) {
            stations.add(new TravelTimeGridBuilder.StationPoint(codes[i], tbl[i][0], tbl[i][1], tbl[i][2]));
        }
        double dz = spacingZ != null ? spacingZ : spacing;
        TravelTimeGridBuilder.Region region;
        if (half == null) {
            region = TravelTimeGridBuilder.Region.fitStations(stations, margin, depth, spacing, dz);
            if (center != null) {
                region = new TravelTimeGridBuilder.Region(center[0], center[1],
                    region.halfWidthXKm + Math.abs(center[1] - region.centerLon) * 111.0,
                    region.halfWidthYKm + Math.abs(center[0] - region.centerLat) * 111.0,
                    0.0, depth, spacing, dz);
            }
        } else {
            double[] c = center;
            if (c == null) {
                TravelTimeGridBuilder.Region f = TravelTimeGridBuilder.Region.fitStations(stations, margin, depth, spacing, dz);
                c = new double[] {f.centerLat, f.centerLon};
            }
            region = new TravelTimeGridBuilder.Region(c[0], c[1], half[0], half.length > 1 ? half[1] : half[0],
                0.0, depth, spacing, dz);
        }
        if (root == null) {
            String n = cake.name();
            int dot = n.lastIndexOf('.');
            root = (dot > 0 ? n.substring(0, dot) : n).replaceAll("[^A-Za-z0-9_-]", "_");
        }
        TravelTimeGridBuilder builder = new TravelTimeGridBuilder(cake, region, flatten, phases);
        System.out.printf(Locale.ROOT, "%s%n  centre %.4f, %.4f  half width %.1f x %.1f km  depth 0-%.1f km  dx=dy=%.2f dz=%.2f km%n"
                + "  %d nodes per grid, %d stations x %d phases%n",
            cake, region.centerLat, region.centerLon, region.halfWidthXKm, region.halfWidthYKm, region.maxDepthKm,
            region.spacingHorizKm, region.spacingDepthKm, builder.nodesPerGrid(), stations.size(), phases.size());
        long t0 = System.currentTimeMillis();
        TravelTimeGridSet set = builder.build(stations, (done, total, msg) ->
            System.out.printf(Locale.ROOT, "  [%d/%d] %s%n", done, total, msg));
        Path dir = Paths.get(out);
        set.write(dir, root, order);
        System.out.printf(Locale.ROOT, "Wrote %d grids to %s (%.1f s)%n", set.size(), dir.toAbsolutePath(),
            (System.currentTimeMillis() - t0) / 1000.0);
    }

    private static double[] numbers(String s, int expected) {
        String[] p = s.split("[,\\s]+");
        if (expected > 0 && p.length != expected) {
            throw new IllegalArgumentException("Expected " + expected + " numbers: " + s);
        }
        double[] v = new double[p.length];
        for (int i = 0; i < p.length; i++) {
            v[i] = Double.parseDouble(p[i]);
        }
        return v;
    }
}
