package com.treloc.xtreloc.app.util;

import java.io.PrintStream;

/**
 * Shared ASCII banner (boxed xTreLoc logo) for CLI and interactive launcher.
 */
public final class AsciiLogo {

    private AsciiLogo() {
    }

    /**
     * Prints the standard logo and version line to the given stream.
     */
    public static void print(PrintStream out) {
        out.println("");
        out.println("╔──────────────────────────────────────────────────────────────────────────────╗");
        out.println("│              MMP\"\"MM\"\"YMM                   `7MMF'                           │");
        out.println("│              P'   MM   `7                     MM                             │");
        out.println("│   `7M'   `MF'     MM      `7Mb,od8  .gP\"Ya    MM         ,pW\"Wq.   ,p6\"bo    │");
        out.println("│     `VA ,V'       MM        MM' \"' ,M'   Yb   MM        6W'   `Wb 6M'  OO    │");
        out.println("│       XMX         MM        MM     8M\"\"\"\"\"\"   MM      , 8M     M8 8M         │");
        out.println("│     ,V' VA.       MM        MM     YM.    ,   MM     ,M YA.   ,A9 YM.    ,   │");
        out.println("│   .AM.   .MA.   .JMML.    .JMML.    `Mbmmd' .JMMmmmmMMM  `Ybmd9'   YMbmd'    │");
        out.println("╚──────────────────────────────────────────────────────────────────────────────╝");
        out.println("");
        out.println(com.treloc.xtreloc.util.VersionInfo.getVersionString());
        out.println("");
    }
}
