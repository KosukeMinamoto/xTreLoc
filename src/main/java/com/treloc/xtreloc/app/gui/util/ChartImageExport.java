package com.treloc.xtreloc.app.gui.util;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Save / copy chart-like {@link JPanel} images and a right-click menu (Viewer Hist / Scatter と同様).
 */
public final class ChartImageExport {

    private static final Logger logger = Logger.getLogger(ChartImageExport.class.getName());

    private ChartImageExport() {
    }

    /**
     * Saves a raster snapshot of the panel (white background, then {@link JPanel#paint(Graphics)}).
     */
    public static void exportPanelToImage(JPanel panel, Component parent, String defaultName) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(defaultName));
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
            int w = panel.getWidth() > 0 ? panel.getWidth() : 600;
            int h = panel.getHeight() > 0 ? panel.getHeight() : 450;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
            panel.paint(g2);
            g2.dispose();
            String ext = f.getName().toLowerCase();
            if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) {
                try {
                    javax.imageio.ImageIO.write(img, "JPEG", f);
                } catch (Exception jpegEx) {
                    logger.log(Level.FINE, "JPEG write failed, falling back to PNG: " + f.getAbsolutePath(), jpegEx);
                    File pngFile = new File(f.getParent(), f.getName().replaceFirst("\\.[jJ][pP][eE]?[gG]$", ".png"));
                    javax.imageio.ImageIO.write(img, "PNG", pngFile);
                    f = pngFile;
                }
            } else {
                javax.imageio.ImageIO.write(img, "PNG", f);
            }
            JOptionPane.showMessageDialog(parent, "Saved: " + f.getAbsolutePath());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Chart image export failed: " + f.getAbsolutePath(), ex);
            JOptionPane.showMessageDialog(parent, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void copyPanelToClipboard(JPanel chartPanel, Component parent) {
        int w = chartPanel.getWidth() > 0 ? chartPanel.getWidth() : 600;
        int h = chartPanel.getHeight() > 0 ? chartPanel.getHeight() : 450;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
            chartPanel.paint(g2);
        } finally {
            g2.dispose();
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(img), null);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(parent,
                "Clipboard unavailable: " + ex.getMessage(),
                "Copy failed",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Right-click: Save as…, Copy to clipboard, Reset zoom.
     */
    public static void installChartPopupMenu(JPanel chartPanel, Component dialogParent,
                                              Runnable resetZoomAction, String defaultFileName) {
        chartPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShow(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShow(e);
            }

            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
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
        });
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
