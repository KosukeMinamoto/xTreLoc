package com.treloc.xtreloc.app.gui.util;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Loads static UI images from the application JAR classpath ({@code src/main/resources/images/}).
 * <p>
 * Bundled assets belong here rather than under {@code ~/.xtreloc}; the app data directory is for
 * settings, logs, and user-downloaded files.
 */
public final class BundledImageLoader {

    private static final Logger LOG = Logger.getLogger(BundledImageLoader.class.getName());
    private static final String PREFIX = "images/";

    private BundledImageLoader() {}

    /**
     * @param fileName file name only, e.g. {@code "logo.png"}
     * @return classpath URL, or null if missing
     */
    public static URL resourceUrl(String fileName) {
        String path = PREFIX + fileName;
        URL u = BundledImageLoader.class.getClassLoader().getResource(path);
        if (u == null) {
            LOG.fine("Bundled image not on classpath: " + path);
        }
        return u;
    }

    public static ImageIcon loadImageIcon(String fileName) {
        URL u = resourceUrl(fileName);
        return u != null ? new ImageIcon(u) : null;
    }

    public static Image loadImage(String fileName) {
        ImageIcon ic = loadImageIcon(fileName);
        return ic != null ? ic.getImage() : null;
    }
}
