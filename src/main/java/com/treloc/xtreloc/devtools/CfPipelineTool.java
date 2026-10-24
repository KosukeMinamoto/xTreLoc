package com.treloc.xtreloc.devtools;

import com.treloc.xtreloc.app.gui.service.CrossCorrelationPickingService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Dev / parity helper: export characteristic-function SAC like the GUI batch, or compare two CF output folders.
 * <p>
 * Usage:
 * <pre>
 *   CfPipelineTool export &lt;sacRoot&gt; &lt;outDir&gt; [freqMin freqMax]
 *   CfPipelineTool compare &lt;dirA&gt; &lt;dirB&gt; [atol]
 * </pre>
 * Example (Gradle): {@code ./gradlew cfTool -PcfToolArgs='export 20071201 build/cf-java 2 8'}
 */
public final class CfPipelineTool {

    private CfPipelineTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0]) || "-h".equals(args[0])) {
            printHelp();
            return;
        }
        String cmd = args[0].toLowerCase(Locale.ROOT);
        if ("export".equals(cmd)) {
            runExport(Arrays.copyOfRange(args, 1, args.length));
        } else if ("compare".equals(cmd)) {
            runCompare(Arrays.copyOfRange(args, 1, args.length));
        } else {
            System.err.println("Unknown command: " + args[0]);
            printHelp();
            System.exit(2);
        }
    }

    private static void printHelp() {
        System.out.println("CfPipelineTool — CF SAC export / directory compare");
        System.out.println();
        System.out.println("  export <sacRoot> <outDir> [freqMin Hz] [freqMax Hz]");
        System.out.println("    Writes <outDir>/*_CF.sac (CHARACTERISTIC + optional band-pass).");
        System.out.println("    Default filter: none (omit freq args).");
        System.out.println();
        System.out.println("  compare <dirA> <dirB> [absoluteTolerance]");
        System.out.println("    Pairs files * _CF.sac by name; reports max |a-b| per file and worst station.");
        System.out.println("    Default tolerance: 1e-4 (exit 1 if exceeded).");
        System.out.println();
    }

    private static void runExport(String[] a) throws Exception {
        if (a.length < 2) {
            System.err.println("export: need <sacRoot> <outDir>");
            System.exit(2);
        }
        Path sacRoot = Paths.get(a[0]).toAbsolutePath().normalize();
        Path outDir = Paths.get(a[1]).toAbsolutePath().normalize();
        Path outputParent = outDir.getParent();
        if (outputParent == null) {
            outputParent = Paths.get(".").toAbsolutePath();
        }
        String eventDirName = outDir.getFileName().toString();
        CrossCorrelationPickingService.Params p = new CrossCorrelationPickingService.Params();
        p.signalKind = CrossCorrelationPickingService.SignalKind.CHARACTERISTIC;
        if (a.length >= 4) {
            p.filterFreqMin = Float.parseFloat(a[2]);
            p.filterFreqMax = Float.parseFloat(a[3]);
        } else {
            p.filterFreqMin = null;
            p.filterFreqMax = null;
        }
        System.out.println("CF export: sacRoot=" + sacRoot);
        System.out.println("           outDir=" + outDir + " (files here via cfSubdir=\".\")");
        System.out.println("           filter=" + p.filterFreqMin + "–" + p.filterFreqMax + " Hz");
        CrossCorrelationPickingService.runDerivedSacOnly(
            sacRoot,
            outputParent,
            eventDirName,
            p,
            ".",
            msg -> System.out.println("[CF] " + msg));
        System.out.println("Done.");
    }

    private static void runCompare(String[] a) throws Exception {
        if (a.length < 2) {
            System.err.println("compare: need <dirA> <dirB> [atol]");
            System.exit(2);
        }
        Path dirA = Paths.get(a[0]).toAbsolutePath().normalize();
        Path dirB = Paths.get(a[1]).toAbsolutePath().normalize();
        double atol = a.length >= 3 ? Double.parseDouble(a[2]) : 1e-4;

        int n = 0;
        double worst = 0;
        String worstName = "";
        try (Stream<Path> paths = java.nio.file.Files.list(dirA)) {
            for (Path pa : (Iterable<Path>) paths::iterator) {
                if (!pa.getFileName().toString().endsWith("_CF.sac")) {
                    continue;
                }
                String name = pa.getFileName().toString();
                Path pb = dirB.resolve(name);
                if (!java.nio.file.Files.isRegularFile(pb)) {
                    System.err.println("Missing in B: " + name);
                    System.exit(1);
                }
                float[] fa = readSacY(pa);
                float[] fb = readSacY(pb);
                if (fa.length != fb.length) {
                    System.err.println("Length mismatch " + name + ": " + fa.length + " vs " + fb.length);
                    System.exit(1);
                }
                double mx = 0;
                for (int i = 0; i < fa.length; i++) {
                    double d = Math.abs(fa[i] - fb[i]);
                    if (d > mx) {
                        mx = d;
                    }
                }
                n++;
                System.out.printf(Locale.US, "%s  max_abs_diff=%.6e  npts=%d%n", name, mx, fa.length);
                if (mx > worst) {
                    worst = mx;
                    worstName = name;
                }
            }
        }
        if (n == 0) {
            System.err.println("No *_CF.sac files in " + dirA);
            System.exit(1);
        }
        System.out.println("---");
        System.out.printf(Locale.US, "Compared %d files. Worst: %s max_abs_diff=%.6e (atol=%.6e)%n",
            n, worstName.isEmpty() ? "(none)" : worstName, worst, atol);
        if (worst > atol) {
            System.err.println("FAIL: exceeds absolute tolerance.");
            System.exit(1);
        }
        System.out.println("OK.");
    }

    private static float[] readSacY(Path sacPath) throws Exception {
        edu.sc.seis.seisFile.sac.SacTimeSeries s = edu.sc.seis.seisFile.sac.SacTimeSeries.read(sacPath.toString());
        float[] y = s.getY();
        return java.util.Arrays.copyOf(y, y.length);
    }
}
