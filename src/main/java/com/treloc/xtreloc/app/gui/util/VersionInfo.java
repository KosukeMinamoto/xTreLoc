package com.treloc.xtreloc.app.gui.util;

/**
 * GUI-facing version info; delegates to {@link com.treloc.xtreloc.util.VersionInfo}.
 */
public class VersionInfo {
    /** @see com.treloc.xtreloc.util.VersionInfo#getVersion() */
    public static String getVersion() {
        return com.treloc.xtreloc.util.VersionInfo.getVersion();
    }

    /** @see com.treloc.xtreloc.util.VersionInfo#getApplicationName() */
    public static String getApplicationName() {
        return com.treloc.xtreloc.util.VersionInfo.getApplicationName();
    }

    /** @see com.treloc.xtreloc.util.VersionInfo#getVersionString() */
    public static String getVersionString() {
        return com.treloc.xtreloc.util.VersionInfo.getVersionString();
    }
}

