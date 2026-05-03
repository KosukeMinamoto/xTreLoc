package com.treloc.xtreloc.solver;

/**
 * CLI runner for {@link Raytrace1D} against bundled or path velocity tables (.nd / .tvel).
 *
 * Usage:
 *   java -Dxtreloc.raytrace.debug=true -cp target/classes:target/dependency/* \
 *     com.treloc.xtreloc.solver.RaytraceStandalone \
 *     --model prem.nd --dist-km 80 --src-depth-km 10 --stn-depth-km 0.1
 */
public final class RaytraceStandalone {

    private RaytraceStandalone() {
    }

    public static void main(String[] args) {
        try {
            Arguments a = Arguments.parse(args);
            Raytrace1D solver = Raytrace1D.load(a.model);
            Raytrace1D.RaySolution r = solver.solveFastestRay(a.distKm, a.srcDepthKm, a.stnDepthKm);

            System.out.println("Raytrace1D standalone result");
            System.out.println("  model: " + a.model);
            System.out.println("  dist_km: " + a.distKm);
            System.out.println("  src_depth_km: " + a.srcDepthKm);
            System.out.println("  stn_depth_km: " + a.stnDepthKm);
            System.out.println("  travel_time_sec: " + r.travelTimeSeconds);
            System.out.println("  takeoff_deg: " + Math.toDegrees(r.takeoffAngleRad));
            System.out.println("  incident_deg: " + Math.toDegrees(r.incidentAngleRad));
        } catch (IllegalArgumentException e) {
            System.err.println("Argument error: " + e.getMessage());
            printUsage();
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  --model <name-or-path> --dist-km <double> --src-depth-km <double> --stn-depth-km <double>");
        System.out.println("Example:");
        System.out.println("  --model prem.nd --dist-km 80 --src-depth-km 10 --stn-depth-km 0.1");
        System.out.println("Verbose internal trace to stderr:");
        System.out.println("  -Dxtreloc.raytrace.debug=true");
    }

    private static final class Arguments {
        final String model;
        final double distKm;
        final double srcDepthKm;
        final double stnDepthKm;

        private Arguments(String model, double distKm, double srcDepthKm, double stnDepthKm) {
            this.model = model;
            this.distKm = distKm;
            this.srcDepthKm = srcDepthKm;
            this.stnDepthKm = stnDepthKm;
        }

        static Arguments parse(String[] args) {
            String model = null;
            Double distKm = null;
            Double srcDepthKm = null;
            Double stnDepthKm = null;

            for (int i = 0; i < args.length; i++) {
                String k = args[i];
                if ("--model".equals(k)) {
                    model = nextValue(args, ++i, k);
                } else if ("--dist-km".equals(k)) {
                    distKm = parseDouble(nextValue(args, ++i, k), k);
                } else if ("--src-depth-km".equals(k)) {
                    srcDepthKm = parseDouble(nextValue(args, ++i, k), k);
                } else if ("--stn-depth-km".equals(k)) {
                    stnDepthKm = parseDouble(nextValue(args, ++i, k), k);
                } else {
                    throw new IllegalArgumentException("Unknown option: " + k);
                }
            }

            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("Missing required --model");
            }
            if (distKm == null) {
                throw new IllegalArgumentException("Missing required --dist-km");
            }
            if (srcDepthKm == null) {
                throw new IllegalArgumentException("Missing required --src-depth-km");
            }
            if (stnDepthKm == null) {
                throw new IllegalArgumentException("Missing required --stn-depth-km");
            }
            return new Arguments(model, distKm, srcDepthKm, stnDepthKm);
        }

        private static String nextValue(String[] args, int idx, String key) {
            if (idx >= args.length) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            return args[idx];
        }

        private static double parseDouble(String v, String key) {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number for " + key + ": " + v);
            }
        }
    }
}
