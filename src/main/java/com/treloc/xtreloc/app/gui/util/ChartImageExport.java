package com.treloc.xtreloc.app.gui.util;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Save / copy chart-like {@link JPanel} images and a right-click menu (Viewer Hist / Scatter, etc.).
 * <p>Uses {@link Robot#createScreenCapture} when the component is on-screen (reliable WYSIWYG),
 * otherwise falls back to off-screen {@link JComponent#printAll} with double buffering disabled.
 */
public final class ChartImageExport {

    private static final Logger logger = Logger.getLogger(ChartImageExport.class.getName());

    private ChartImageExport() {
    }

    /**
     * Resolves a non-zero size for off-screen rasterization (when Robot is not used).
     */
    private static java.awt.Dimension resolveExportSize(JComponent panel) {
        int w = panel.getWidth();
        int h = panel.getHeight();
        if (w <= 0 || h <= 0) {
            for (Container p = panel.getParent(); p != null && (w <= 0 || h <= 0); p = p.getParent()) {
                p.validate();
                w = panel.getWidth();
                h = panel.getHeight();
            }
        }
        if (w <= 0 || h <= 0) {
            java.awt.Dimension ps = panel.getPreferredSize();
            if (ps != null && ps.width > 0 && ps.height > 0) {
                w = ps.width;
                h = ps.height;
            }
        }
        if (w <= 0 || h <= 0) {
            java.awt.Dimension ms = panel.getMinimumSize();
            if (ms != null && ms.width > 0 && ms.height > 0) {
                w = ms.width;
                h = ms.height;
            }
        }
        if (w <= 0) {
            w = 600;
        }
        if (h <= 0) {
            h = 450;
        }
        return new java.awt.Dimension(w, h);
    }

    /**
     * Captures the component as it appears on screen (same pixels as the user sees).
     */
    private static BufferedImage tryRobotScreenGrab(Component c) {
        try {
            if (!c.isShowing()) {
                return null;
            }
            int cw = c.getWidth();
            int ch = c.getHeight();
            if (cw < 1 || ch < 1) {
                return null;
            }
            Point screenLoc = c.getLocationOnScreen();
            Rectangle captureRect = new Rectangle(screenLoc.x, screenLoc.y, cw, ch);
            Robot robot = new Robot();
            robot.setAutoDelay(0);
            return robot.createScreenCapture(captureRect);
        } catch (AWTException | SecurityException | IllegalComponentStateException ex) {
            logger.log(Level.FINE, "Screen capture unavailable: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Off-screen paint using {@link JComponent#printAll} (double buffering disabled).
     */
    private static void paintWithPrintAll(JComponent jc, Graphics2D g2, int w, int h) {
        RepaintManager rm = RepaintManager.currentManager(jc);
        boolean wasDoubleBuffered = rm.isDoubleBufferingEnabled();
        java.awt.Dimension prev = jc.getSize();
        try {
            rm.setDoubleBufferingEnabled(false);
            jc.setSize(w, h);
            jc.doLayout();
            AffineTransform savedTx = g2.getTransform();
            Shape savedClip = g2.getClip();
            try {
                g2.setTransform(new AffineTransform());
                g2.setClip(0, 0, w, h);
                jc.printAll(g2);
            } finally {
                g2.setTransform(savedTx);
                g2.setClip(savedClip);
            }
        } finally {
            rm.setDoubleBufferingEnabled(wasDoubleBuffered);
            jc.setSize(prev);
            Container p = jc.getParent();
            if (p != null) {
                p.revalidate();
            }
        }
    }

    /**
     * Rasterizes a Swing component for export: screen grab when possible, otherwise off-screen {@code printAll}.
     *
     * @return image or {@code null} if {@code c} is not a {@link JComponent} and screen grab failed
     */
    public static BufferedImage rasterizeComponent(Component c) {
        BufferedImage fromRobot = tryRobotScreenGrab(c);
        if (fromRobot != null) {
            return fromRobot;
        }
        if (!(c instanceof JComponent)) {
            return null;
        }
        JComponent jc = (JComponent) c;
        java.awt.Dimension dim = resolveExportSize(jc);
        int w = dim.width;
        int h = dim.height;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
            paintWithPrintAll(jc, g2, w, h);
        } finally {
            g2.dispose();
        }
        return img;
    }

    /**
     * Saves a raster snapshot of the panel.
     */
    public static void exportPanelToImage(JPanel panel, Component parent, String defaultName) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(defaultName));
        FileChooserHelper.setDefaultDirectory(fc);
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        String path = f.getAbsolutePath();
        if (!path.toLowerCase().endsWith(".png") && !path.toLowerCase().endsWith(".jpg")
            && !path.toLowerCase().endsWith(".jpeg")) {
            f = new File(path + ".png");
        }
        try {
            if (panel.getTopLevelAncestor() != null) {
                panel.getTopLevelAncestor().validate();
            }
            panel.validate();
            BufferedImage img = rasterizeComponent(panel);
            if (img == null) {
                logger.warning("Chart export: could not capture chart; show Hist/Scatter tab in the foreground and try again.");
                return;
            }
            String ext = f.getName().toLowerCase();
            boolean written;
            if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) {
                try {
                    written = javax.imageio.ImageIO.write(img, "JPEG", f);
                } catch (Exception jpegEx) {
                    logger.log(Level.FINE, "JPEG write failed, falling back to PNG: " + f.getAbsolutePath(), jpegEx);
                    File pngFile = new File(f.getParent(), f.getName().replaceFirst("\\.[jJ][pP][eE]?[gG]$", ".png"));
                    written = javax.imageio.ImageIO.write(img, "PNG", pngFile);
                    f = pngFile;
                }
            } else {
                written = javax.imageio.ImageIO.write(img, "PNG", f);
            }
            if (!written) {
                throw new IOException("ImageIO.write returned false for " + f.getAbsolutePath());
            }
            logger.info("Chart export saved: " + f.getAbsolutePath());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Chart image export failed: " + f.getAbsolutePath(), ex);
        }
    }

    public static void copyPanelToClipboard(JPanel chartPanel, Component parent) {
        try {
            if (chartPanel.getTopLevelAncestor() != null) {
                chartPanel.getTopLevelAncestor().validate();
            }
            chartPanel.validate();
            BufferedImage img = rasterizeComponent(chartPanel);
            if (img == null) {
                logger.warning("Chart copy: could not capture chart for clipboard.");
                return;
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(img), null);
        } catch (IllegalStateException ex) {
            logger.log(Level.WARNING, "Chart copy: clipboard unavailable", ex);
        }
    }

    /**
     * Right-click (and popup-trigger platforms): Save as…, Copy to clipboard, Reset zoom.
     */
    public static void installChartPopupMenu(JPanel chartPanel, Component dialogParent,
                                              Runnable resetZoomAction, String defaultFileName) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    maybeShow(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    maybeShow(e);
                }
            }

            private void maybeShow(MouseEvent e) {
                JPopupMenu menu = new JPopupMenu();
                JMenuItem save = new JMenuItem("Save as...");
                save.addActionListener(ev -> exportPanelToImage(chartPanel, dialogParent, defaultFileName));
                JMenuItem copy = new JMenuItem("Copy to clipboard");
                copy.addActionListener(ev -> copyPanelToClipboard(chartPanel, dialogParent));
                JMenuItem reset = new JMenuItem("Reset zoom");
                reset.addActionListener(ev -> resetZoomAction.run());
                menu.add(save);
                menu.add(copy);
                menu.addSeparator();
                menu.add(reset);
                menu.show(chartPanel, e.getX(), e.getY());
            }
        };
        chartPanel.addMouseListener(adapter);
    }

    private static final class ImageTransferable implements Transferable {
        private final Image image;

        ImageTransferable(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
