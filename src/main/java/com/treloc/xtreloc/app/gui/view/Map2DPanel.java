package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.map2d.Map2DCanvas;
import com.treloc.xtreloc.app.gui.map2d.Map2DRenderer;
import com.treloc.xtreloc.app.gui.service.ShapefileOverlayExtractor;
import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.SharedFileManager;
import com.treloc.xtreloc.app.gui.volume3d.ColorMaps;
import com.treloc.xtreloc.app.gui.volume3d.Map3DIcons;
import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D.Reduce;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

/**
 * 2-D map with the same chrome as the 3-D view: full-width canvas and icon-strip pop-up cards
 * (View, Background, Range, Style, Tools, Export).
 */
public class Map2DPanel {

    private static final int STRIP_WIDTH = 46;
    private static final int CARD_WIDTH = 240;
    private static final int STYLE_CARD_WIDTH = 460;
    private MapLayerStylePanel stylePanel;
    private static final int SLIDER_STEPS = 1000;

    private final Map2DCanvas canvas = new Map2DCanvas();
    private final Map2DRenderer renderer = canvas.renderer();
    private final MapView mapView;
    private final VelocityViewerPanel viewer;
    private final MapView.ColorBarPanel colorBar = new MapView.ColorBarPanel(MapView.ColorPalette.SEISMIC);
    private String lastTimeLabel;

    private final JPanel root = new JPanel(new BorderLayout());
    private final JLayeredPane area;
    private final JPanel strip = new JPanel();
    private final JPanel cardHost = new JPanel(new BorderLayout());
    private final JLabel cardTitle = new JLabel();
    private final JScrollPane cardScroll = new JScrollPane();
    private final Map<String, JToggleButton> stripButtons = new LinkedHashMap<>();
    private final Map<String, JComponent> cards = new HashMap<>();
    private String openCard;

    private JComboBox<Reduce> reduceCombo;
    private JComboBox<String> channelCombo;
    private JCheckBox velocityCheck;
    private JSlider sliceSlider;
    private JLabel sliceLabel;
    private JLabel sliceName;
    private JSpinner scaleSpinner;
    private JPanel sliceRow;
    private JPanel scaleRow;
    private boolean updatingUi;
    private JLabel status = new JLabel(" ");
    private final EventSeismicOpener seismicOpener;

    public Map2DPanel(MapView mapView, VelocityViewerPanel viewer) {
        this.mapView = mapView;
        this.viewer = viewer;
        canvas.setMapView(mapView);
        area = new JLayeredPane() {
            @Override
            public void doLayout() {
                layoutArea(getWidth(), getHeight());
            }
        };
        buildStrip();
        buildCardHost();
        buildCards();
        area.add(canvas, JLayeredPane.DEFAULT_LAYER);
        area.add(strip, JLayeredPane.PALETTE_LAYER);
        area.add(cardHost, JLayeredPane.POPUP_LAYER);
        cardHost.setVisible(false);

        root.setBackground(AppPanelStyle.getPanelBg());
        if (mapView != null && mapView.getMapTopPanel() != null) {
            root.add(mapView.getMapTopPanel(), BorderLayout.NORTH);
        }
        seismicOpener = new EventSeismicOpener(canvas.selectionModel(),
            () -> mapView == null ? List.<CatalogInfo>of() : mapView.getCatalogInfos());
        canvas.setEventActivatedListener(key -> seismicOpener.open(key, false));
        root.add(area, BorderLayout.CENTER);
        status.setFont(AppPanelStyle.getLabelFont());
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        root.add(status, BorderLayout.SOUTH);
        seismicOpener.setMessageListener(status::setText);

        canvas.setColorBarClickListener(this::openColorBar);
        canvas.setCatalogColorBarClickListener(this::openCatalogColorBar);
        colorBar.addChangeListener(this::applyColorBar);
        if (mapView != null && mapView.getColorBar() != null) {
            mapView.getColorBar().addChangeListener(canvas::repaint);
        }
        if (mapView != null) {
            mapView.addOverlayChangeListener(this::refreshOverlays);
            canvas.showError = mapView.isShowErrorEllipse();
            canvas.showNetwork = mapView.isShowConnections();
        }
        if (viewer != null) {
            viewer.addVolumeListener(this::syncVolume);
        }
        SharedFileManager.getInstance().addGeometryListener(this::applyGeometry);
        applyGeometry();
        refreshOverlays();
        syncVolume();
        updateSliceUi();
        updateScaleEnabled();
    }

    /** Reloads the map layers, overlays and the current velocity volume. */
    public void refreshView() {
        if (mapView != null) {
            mapView.reloadMap();
        }
        if (viewer != null) {
            viewer.ensureLoaded();
        }
        syncVolume();
        refreshOverlays();
        canvas.repaint();
    }

    public JComponent getComponent() {
        return root;
    }

    public Map2DCanvas canvas() {
        return canvas;
    }

    /**
     * Connects the map to the Seismic page: a double-click on an event passes the event's waveform
     * folders to {@code opener}.
     */
    public void setSeismicOpener(EventSeismicOpener.SeismicOpener opener) {
        seismicOpener.setSeismicOpener(opener);
    }

    public void syncVolume() {
        if (viewer != null) {
            viewer.ensureLoaded();
        }
        boolean tt = viewer != null && viewer.isTravelTimeSource();
        VelocityVolume3D model = (viewer != null && !tt) ? viewer.modelVolume() : null;
        VelocityVolume3D time = (viewer != null && tt) ? viewer.timeVolume() : null;
        String timeLabel = tt ? (viewer != null ? viewer.timeQuantityName() : null) : null;
        List<String> timeNames = tt && viewer != null ? viewer.timeQuantityNames() : List.of();
        boolean keep = renderer.volume != null;
        boolean newTime = timeLabel != null && !timeLabel.equals(lastTimeLabel);
        lastTimeLabel = timeLabel;
        updatingUi = true;
        try {
            if (channelCombo != null) {
                Object prev = channelCombo.getSelectedItem();
                channelCombo.removeAllItems();
                if (model != null) {
                    for (String c : model.channelNames()) {
                        channelCombo.addItem(c);
                    }
                }
                for (String n : timeNames) {
                    channelCombo.addItem(n);
                }
                if (newTime) {
                    channelCombo.setSelectedItem(timeLabel);
                } else if (prev != null) {
                    channelCombo.setSelectedItem(prev);
                } else if (model != null) {
                    channelCombo.setSelectedItem(preferredChannel(model));
                }
            }
            applySelectedQuantity(keep);
            if (newTime) {
                renderer.showVelocity = true;
                if (velocityCheck != null) {
                    velocityCheck.setSelected(true);
                }
            }
        } finally {
            updatingUi = false;
        }
        updateSliceUi();
        updateScaleEnabled();
        canvas.repaint();
        VelocityVolume3D shown = renderer.volume;
        if (shown == null) {
            status.setText(tt ? "No travel-time table" : "No velocity model");
        } else {
            status.setText(shown.name());
        }
    }

    private void applySelectedQuantity(boolean keepView) {
        Object item = channelCombo == null ? null : channelCombo.getSelectedItem();
        String name = item == null ? null : item.toString();
        String timeLabel = viewer != null ? viewer.timeQuantityName() : null;
        List<String> timeNames = viewer != null ? viewer.timeQuantityNames() : List.of();
        boolean tt = viewer != null && viewer.isTravelTimeSource();
        VelocityVolume3D time = (viewer != null && tt) ? viewer.timeVolume() : null;
        VelocityVolume3D model = (viewer != null && !tt) ? viewer.modelVolume() : null;
        if (!tt) {
            timeNames = List.of();
        }
        if (name != null && timeNames.contains(name)) {
            if (time != null && name.equals(timeLabel)) {
                renderer.setVolume(time, time.channelNames().get(0), keepView);
            } else if (viewer != null) {
                viewer.selectTimeQuantity(name);
            }
        } else if (model != null) {
            String ch = name;
            if (ch == null || !model.hasChannel(ch)) {
                ch = preferredChannel(model);
            }
            boolean first = renderer.volume == null;
            renderer.setVolume(model, ch, keepView);
            if (first) {
                renderer.showVelocity = true;
                if (velocityCheck != null) {
                    velocityCheck.setSelected(true);
                }
            }
        } else {
            renderer.setVolume(null, null, keepView);
        }
    }

    private static String preferredChannel(VelocityVolume3D model) {
        if (model.hasChannel(VelocityVolume3D.CH_VS)) {
            return VelocityVolume3D.CH_VS;
        }
        if (model.hasChannel(VelocityVolume3D.CH_DVS_MEAN)) {
            return VelocityVolume3D.CH_DVS_MEAN;
        }
        return model.channelNames().get(0);
    }

    private void applyGeometry() {
        SharedFileManager.Geometry g = SharedFileManager.getInstance().getGeometry();
        canvas.gridSpacingKm = g != null && g.spacingKm > 0 ? g.spacingKm : 2.0;
        if (g != null && g.hasRegion() && renderer.volume == null) {
            renderer.lonMin = g.lonMin;
            renderer.lonMax = g.lonMax;
            renderer.latMin = g.latMin;
            renderer.latMax = g.latMax;
            renderer.resetView();
        }
        canvas.repaint();
    }

    private void refreshOverlays() {
        if (mapView == null) {
            canvas.repaint();
            return;
        }
        canvas.symbolSize = mapView.getSymbolSize();
        canvas.gridStyle = mapView.getGridStyle();
        canvas.showError = mapView.isShowErrorEllipse();
        canvas.showNetwork = mapView.isShowConnections();
        double[] sel = mapView.selectedPoint();
        if (sel != null) {
            canvas.setHighlight(sel[0], sel[1]);
        } else {
            canvas.clearHighlight();
        }
        List<Map2DCanvas.StyledPolyline> lines = new ArrayList<>();
        double[] b = renderer.worldBounds();
        for (MapView.ShapefileRef s : mapView.getShapefileLayers()) {
            if (!s.visible) {
                continue;
            }
            try {
                List<double[]> raw = ShapefileOverlayExtractor.extract(s.source, b[0], b[1],
                    renderer.collapse == 2 ? b[2] : -90, renderer.collapse == 2 ? b[3] : 90);
                for (double[] pl : raw) {
                    lines.add(new Map2DCanvas.StyledPolyline(pl, s.style.colorWithOpacity(), s.style.stroke()));
                }
            } catch (Exception ignored) {
                // keep the rest of the overlays
            }
        }
        canvas.setShapeLines(lines);
        canvas.repaint();
    }

    private void buildStrip() {
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setBackground(AppPanelStyle.getPanelBg());
        strip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, AppPanelStyle.getBorderColor()));
        strip.add(Box.createVerticalStrut(6));
        Object[][] items = {
            {"View", Map3DIcons.Kind.VIEW, "Reset view, plan / sections and slice"},
            {"Background", Map3DIcons.Kind.SLICE, "Quantity, statistic, fill and vertical exaggeration"},
            {"Range", Map3DIcons.Kind.RANGE, "Colour range of the velocity background"},
            {"Style", Map3DIcons.Kind.TABLE, "Symbol, line style, colour, size and opacity"},
            {"Tools", Map3DIcons.Kind.TOOLS, "Legend, colour bar, grid, station codes"},
            {"Export", Map3DIcons.Kind.EXPORT, "Export the current view"},
        };
        for (Object[] it : items) {
            String name = (String) it[0];
            JToggleButton b = new JToggleButton(new Map3DIcons((Map3DIcons.Kind) it[1], 24,
                AppPanelStyle.getContentTextColor()));
            b.setToolTipText((String) it[2]);
            b.setFocusPainted(false);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.addActionListener(e -> showCard(b.isSelected() ? name : null));
            stripButtons.put(name, b);
            strip.add(b);
            strip.add(Box.createVerticalStrut(4));
        }
        strip.add(Box.createVerticalGlue());
        JToggleButton refresh = new JToggleButton(new Map3DIcons(Map3DIcons.Kind.REFRESH, 24,
            AppPanelStyle.getContentTextColor()));
        refresh.setToolTipText("Reload map, overlays and PREM / velocity background");
        refresh.setFocusPainted(false);
        refresh.setAlignmentX(Component.CENTER_ALIGNMENT);
        Dimension rd = new Dimension(STRIP_WIDTH - 6, 38);
        refresh.setPreferredSize(rd);
        refresh.setMaximumSize(rd);
        refresh.addActionListener(e -> {
            refresh.setSelected(false);
            refreshView();
        });
        strip.add(refresh);
        strip.add(Box.createVerticalStrut(6));
    }

    private void layoutArea(int w, int h) {
        int cw = Math.max(0, w - STRIP_WIDTH);
        canvas.setBounds(0, 0, cw, h);
        strip.setBounds(cw, 0, STRIP_WIDTH, h);
        if (!cardHost.isVisible()) {
            return;
        }
        JComponent body = openCard == null ? null : cards.get(openCard);
        Dimension pref = body != null ? body.getPreferredSize() : new Dimension(CARD_WIDTH, 80);
        int contentH = Math.max(40, pref.height);
        int preferred = "Style".equals(openCard) ? STYLE_CARD_WIDTH : Math.max(CARD_WIDTH, pref.width + 8);
        int maxW = Math.max(120, w - STRIP_WIDTH - 16);
        int width = Math.min(preferred, maxW);
        int height = Math.min(36 + contentH + 6, Math.max(72, h - 16));
        int x = Math.max(6, w - STRIP_WIDTH - width - 6);
        cardHost.setBounds(x, 8, width, height);
    }

    private void buildCardHost() {
        cardTitle.setFont(AppPanelStyle.getSectionTitleFont());
        cardTitle.setForeground(AppPanelStyle.getTitleColor());
        cardTitle.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        JButton close = new JButton("\u00D7");
        close.setToolTipText("Close");
        close.setFocusPainted(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setMargin(new java.awt.Insets(0, 6, 0, 8));
        close.addActionListener(e -> showCard(null));
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppPanelStyle.getPanelBg());
        header.add(cardTitle, BorderLayout.CENTER);
        header.add(close, BorderLayout.EAST);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppPanelStyle.getBorderColor()));
        cardScroll.setBorder(null);
        cardScroll.getViewport().setBackground(AppPanelStyle.getPanelBg());
        cardScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cardHost.setBackground(AppPanelStyle.getPanelBg());
        cardHost.setBorder(BorderFactory.createLineBorder(AppPanelStyle.getBorderColor()));
        cardHost.add(header, BorderLayout.NORTH);
        cardHost.add(cardScroll, BorderLayout.CENTER);
    }

    private void showCard(String name) {
        if (name == null || name.equals(openCard)) {
            openCard = null;
            cardHost.setVisible(false);
        } else {
            openCard = name;
            cardTitle.setText(name);
            if ("Style".equals(name) && stylePanel != null) {
                stylePanel.reload();
            }
            cardScroll.setViewportView(cards.get(name));
            cardHost.setVisible(true);
        }
        for (Map.Entry<String, JToggleButton> e : stripButtons.entrySet()) {
            e.getValue().setSelected(e.getKey().equals(openCard));
        }
        area.revalidate();
        area.repaint();
    }

    private void buildCards() {
        cards.put("View", viewCard());
        cards.put("Background", backgroundCard());
        cards.put("Range", rangeCard());
        stylePanel = new MapLayerStylePanel(mapView);
        cards.put("Style", stylePanel);
        cards.put("Tools", toolsCard());
        cards.put("Export", exportCard());
    }

    private JPanel body() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(AppPanelStyle.getPanelBg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        return p;
    }

    private JPanel row() {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        r.setBackground(AppPanelStyle.getPanelBg());
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    private JCheckBox check(String text, boolean on) {
        JCheckBox cb = new JCheckBox(text, on);
        cb.setBackground(AppPanelStyle.getPanelBg());
        cb.setFont(AppPanelStyle.getLabelFont());
        return cb;
    }

    private JButton cardButton(String text) {
        JButton b = new JButton(text);
        b.setMargin(new java.awt.Insets(2, 8, 2, 8));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
        return b;
    }

    private JPanel viewCard() {
        JPanel p = body();
        JButton plan = cardButton("Plan");
        JButton latSec = cardButton("Lat–depth");
        JButton lonSec = cardButton("Lon–depth");
        JButton reset = cardButton("Reset view");
        plan.setToolTipText("Plan  lat–lon");
        latSec.setToolTipText("Section  lat–depth");
        lonSec.setToolTipText("Section  lon–depth");
        plan.addActionListener(e -> selectCollapse(2));
        latSec.addActionListener(e -> selectCollapse(0));
        lonSec.addActionListener(e -> selectCollapse(1));
        reset.addActionListener(e -> {
            renderer.resetView();
            canvas.repaint();
        });
        p.add(plan);
        p.add(Box.createVerticalStrut(4));
        p.add(latSec);
        p.add(Box.createVerticalStrut(4));
        p.add(lonSec);
        p.add(Box.createVerticalStrut(4));
        p.add(reset);
        p.add(Box.createVerticalStrut(8));

        sliceName = new JLabel("Depth");
        sliceName.setFont(AppPanelStyle.getLabelFont());
        sliceLabel = new JLabel(" ");
        sliceLabel.setFont(AppPanelStyle.getLabelFont());
        sliceLabel.setPreferredSize(new Dimension(72, 18));
        sliceRow = new JPanel(new BorderLayout(6, 0));
        sliceRow.setBackground(AppPanelStyle.getPanelBg());
        sliceRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sliceRow.add(sliceName, BorderLayout.WEST);
        sliceRow.add(sliceLabel, BorderLayout.EAST);
        p.add(sliceRow);
        sliceSlider = new JSlider(0, SLIDER_STEPS, SLIDER_STEPS / 2);
        sliceSlider.setBackground(AppPanelStyle.getPanelBg());
        sliceSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        sliceSlider.addChangeListener(e -> {
            if (updatingUi) {
                return;
            }
            double lo = renderer.sliceMin();
            double hi = renderer.sliceMax();
            renderer.slicePos = lo + sliceSlider.getValue() / (double) SLIDER_STEPS * (hi - lo);
            updateSliceLabel();
            if (renderer.reduce == Reduce.SLICE) {
                renderer.rebuildPlane();
                canvas.repaint();
            }
        });
        p.add(sliceSlider);
        return p;
    }

    private void selectCollapse(int collapse) {
        if (updatingUi) {
            return;
        }
        renderer.collapse = collapse;
        renderer.slicePos = renderer.defaultSlice();
        if (renderer.collapse == 2) {
            renderer.scaleRatio = 1.0;
            if (scaleSpinner != null) {
                updatingUi = true;
                scaleSpinner.setValue(1.0);
                updatingUi = false;
            }
        }
        updateSliceUi();
        updateScaleEnabled();
        renderer.rebuildPlane();
        canvas.repaint();
    }

    private JPanel backgroundCard() {
        JPanel p = body();

        velocityCheck = check("Velocity background", true);
        renderer.showVelocity = true;
        velocityCheck.addActionListener(e -> {
            renderer.showVelocity = velocityCheck.isSelected();
            if (renderer.showVelocity) {
                renderer.rebuildPlane();
            }
            canvas.repaint();
        });
        JPanel rb = row();
        rb.add(velocityCheck);
        p.add(rb);

        channelCombo = new JComboBox<>();
        AppPanelStyle.styleComboBox(channelCombo);
        channelCombo.addActionListener(e -> {
            if (updatingUi || channelCombo.getSelectedItem() == null) {
                return;
            }
            applySelectedQuantity(true);
            canvas.repaint();
        });
        JPanel r1 = row();
        r1.add(new JLabel("Quantity"));
        r1.add(channelCombo);
        p.add(r1);

        reduceCombo = new JComboBox<>(Reduce.values());
        reduceCombo.setSelectedItem(Reduce.SLICE);
        AppPanelStyle.styleComboBox(reduceCombo);
        reduceCombo.addActionListener(e -> {
            if (updatingUi) {
                return;
            }
            renderer.reduce = (Reduce) reduceCombo.getSelectedItem();
            updateSliceUi();
            renderer.rebuildPlane();
            canvas.repaint();
        });
        JPanel r2 = row();
        r2.add(new JLabel("Statistic"));
        r2.add(reduceCombo);
        p.add(r2);

        JComboBox<Map2DRenderer.FillStyle> fillCombo = new JComboBox<>(Map2DRenderer.FillStyle.values());
        fillCombo.setSelectedItem(renderer.fillStyle);
        AppPanelStyle.styleComboBox(fillCombo);
        fillCombo.addActionListener(e -> {
            if (updatingUi || fillCombo.getSelectedItem() == null) {
                return;
            }
            renderer.fillStyle = (Map2DRenderer.FillStyle) fillCombo.getSelectedItem();
            canvas.repaint();
        });
        JPanel rFill = row();
        rFill.add(new JLabel("Type"));
        rFill.add(fillCombo);
        p.add(rFill);

        scaleSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.2, 100.0, 0.25));
        scaleSpinner.addChangeListener(e -> {
            renderer.scaleRatio = ((Number) scaleSpinner.getValue()).doubleValue();
            canvas.repaint();
        });
        scaleRow = row();
        scaleRow.add(new JLabel("Vertical exaggeration"));
        scaleRow.add(scaleSpinner);
        p.add(scaleRow);
        JLabel hint = new JLabel("<html><body style='width:220px'>Sections only. 1 = equal kilometres; "
            + "values above 1 stretch depth.</body></html>");
        hint.setFont(AppPanelStyle.getLabelFont());
        hint.setForeground(AppPanelStyle.getMutedTextColor());
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(hint);
        return p;
    }

    private void updateSliceUi() {
        if (sliceSlider == null) {
            return;
        }
        updatingUi = true;
        try {
            boolean slice = renderer.reduce == Reduce.SLICE;
            String axis = renderer.collapse == 2 ? "Depth" : (renderer.collapse == 0 ? "Longitude" : "Latitude");
            sliceName.setText(axis);
            sliceSlider.setEnabled(slice);
            sliceName.setEnabled(slice);
            sliceLabel.setEnabled(slice);
            double lo = renderer.sliceMin();
            double hi = renderer.sliceMax();
            double f = hi > lo ? (renderer.slicePos - lo) / (hi - lo) : 0.5;
            sliceSlider.setValue((int) Math.round(Math.max(0, Math.min(1, f)) * SLIDER_STEPS));
            updateSliceLabel();
        } finally {
            updatingUi = false;
        }
    }

    private void updateSliceLabel() {
        if (sliceLabel == null) {
            return;
        }
        if (renderer.collapse == 2) {
            sliceLabel.setText(String.format(Locale.ROOT, "%.1f km", renderer.slicePos));
        } else if (renderer.collapse == 0) {
            sliceLabel.setText(String.format(Locale.ROOT, "%.3f°E", renderer.slicePos));
        } else {
            sliceLabel.setText(String.format(Locale.ROOT, "%.3f°N", renderer.slicePos));
        }
    }

    private void updateScaleEnabled() {
        if (scaleSpinner == null) {
            return;
        }
        boolean on = renderer.collapse != 2;
        if (!on) {
            renderer.scaleRatio = 1.0;
        }
        scaleSpinner.setEnabled(on);
        if (scaleRow != null) {
            for (Component c : scaleRow.getComponents()) {
                c.setEnabled(on);
            }
        }
    }

    private JPanel rangeCard() {
        JPanel p = body();
        JButton pal = cardButton("Palette and range...");
        pal.addActionListener(e -> openColorBar());
        JButton auto = cardButton("Auto range");
        auto.addActionListener(e -> {
            renderer.rebuildPlane();
            colorBar.setRange(renderer.vmin, renderer.vmax,
                renderer.channel == null ? "Value" : renderer.channel);
            canvas.repaint();
        });
        p.add(pal);
        p.add(Box.createVerticalStrut(4));
        p.add(auto);
        return p;
    }

    private void openColorBar() {
        java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(root);
        if (w != null) {
            colorBar.setDialogParent(w);
        }
        colorBar.showSettingsDialog();
        applyColorBar();
    }

    private void openCatalogColorBar() {
        if (mapView == null || mapView.getColorBar() == null) {
            return;
        }
        java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(root);
        if (w != null) {
            mapView.getColorBar().setDialogParent(w);
        }
        mapView.getColorBar().showSettingsDialog();
        canvas.repaint();
    }

    private void applyColorBar() {
        renderer.vmin = colorBar.getMinValue();
        renderer.vmax = colorBar.getMaxValue();
        Color[] arr = colorBar.getColorArray();
        if (arr != null && arr.length > 1) {
            int[] lut = new int[arr.length];
            for (int i = 0; i < arr.length; i++) {
                lut[i] = arr[i].getRGB() & 0xFFFFFF;
            }
            renderer.lut = lut;
        } else {
            renderer.lut = ColorMaps.SEISMIC.lut();
        }
        canvas.repaint();
    }

    private JPanel toolsCard() {
        JPanel p = body();
        JCheckBox st = check("Stations", true);
        st.addActionListener(e -> {
            canvas.showStations = st.isSelected();
            canvas.repaint();
        });
        JCheckBox codes = check("Station codes", true);
        canvas.showStationCodes = true;
        codes.addActionListener(e -> {
            canvas.showStationCodes = codes.isSelected();
            canvas.repaint();
        });
        JCheckBox hy = check("Hypocenters", true);
        hy.addActionListener(e -> {
            canvas.showHypocenters = hy.isSelected();
            canvas.repaint();
        });
        JCheckBox sh = check("Shapefiles", true);
        sh.addActionListener(e -> {
            canvas.showShapefiles = sh.isSelected();
            canvas.repaint();
        });
        JCheckBox grid = check("Grid", true);
        grid.addActionListener(e -> {
            canvas.showGrid = grid.isSelected();
            canvas.repaint();
        });
        JCheckBox legend = check("Legend", canvas.showLegend);
        JCheckBox bgBar = check("Background colour bar", canvas.showColorBar);
        JCheckBox hypoBar = check("Hypocenter colour bar", canvas.showCatalogColorBar);
        JCheckBox scale = check("Scale bar", canvas.showScale);
        JCheckBox nesw = check("N / S / E / W", canvas.showCompass);
        legend.addActionListener(e -> {
            canvas.showLegend = legend.isSelected();
            canvas.repaint();
        });
        bgBar.addActionListener(e -> {
            canvas.showColorBar = bgBar.isSelected();
            canvas.repaint();
        });
        hypoBar.addActionListener(e -> {
            canvas.showCatalogColorBar = hypoBar.isSelected();
            canvas.repaint();
        });
        scale.addActionListener(e -> {
            canvas.showScale = scale.isSelected();
            canvas.repaint();
        });
        nesw.addActionListener(e -> {
            canvas.showCompass = nesw.isSelected();
            canvas.repaint();
        });
        for (JCheckBox cb : new JCheckBox[] {st, codes, hy, sh, grid, legend, bgBar, hypoBar, scale, nesw}) {
            p.add(cb);
        }
        return p;
    }

    private JPanel exportCard() {
        JPanel p = body();
        JButton save = new JButton("Save as PNG...");
        save.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("map2d.png"));
            if (fc.showSaveDialog(root) == JFileChooser.APPROVE_OPTION) {
                try {
                    ImageIO.write(canvas.snapshot(), "png", fc.getSelectedFile());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(root, ex.getMessage());
                }
            }
        });
        JButton copy = new JButton("Copy to clipboard");
        copy.addActionListener(e -> {
            BufferedImage img = canvas.snapshot();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] {DataFlavor.imageFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return DataFlavor.imageFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) {
                    return img;
                }
            }, null);
        });
        JPanel r1 = row();
        r1.add(save);
        p.add(r1);
        JPanel r2 = row();
        r2.add(copy);
        p.add(r2);
        return p;
    }

    JComponent cardBodyForTest(String name) {
        return cards.get(name);
    }
}
