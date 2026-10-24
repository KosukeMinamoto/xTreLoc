package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.service.AssociationModel;
import com.treloc.xtreloc.app.gui.service.CrossCorrelationPickingService;
import com.treloc.xtreloc.app.gui.service.EqtPickWriter;
import com.treloc.xtreloc.app.gui.service.PickPool;
import com.treloc.xtreloc.app.gui.service.RealAssociator;
import com.treloc.xtreloc.app.gui.service.RealResultWriter;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper;
import com.treloc.xtreloc.app.gui.util.SharedFileManager;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * Step 3 of the Absolute workflow: writes the pick pool and the associated events in the formats the
 * other tools read: REAL files, the catalog of the Model page, and per-event picks
 * ({@code .obs}, WIN pickfile, station-pair {@code .dat}).
 */
public class AbsoluteExportPanel extends JPanel {

    private final PickPool pool;
    private final AssociationModel model;

    private final JTextField outputField = new JTextField();
    private final JCheckBox catalogBox = new JCheckBox("Catalog (CSV, for the Model page)", true);
    private final JCheckBox realBox = new JCheckBox("REAL result (catalog_sel.txt, phase_sel.txt)", true);
    private final JCheckBox obsBox = new JCheckBox("Per event: picks (.obs)", true);
    private final JCheckBox winBox = new JCheckBox("Per event: WIN pickfile (#p / #s)", false);
    private final JCheckBox datBox = new JCheckBox("Per event: station-pair differences (.dat)", true);
    private final JTextField maxDistField = new JTextField(6);
    private final JCheckBox poolCsvBox = new JCheckBox("Pick pool (picks.csv)", false);
    private final JLabel summary = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");
    private Consumer<String> statusSink = s -> { };

    public AbsoluteExportPanel(PickPool pool, AssociationModel model) {
        super(new BorderLayout());
        this.pool = pool;
        this.model = model;
        AppPanelStyle.setPanelBackground(this);

        JPanel form = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(form);
        form.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        int y = 0;

        summary.setFont(AppPanelStyle.getLabelFont());
        y = add(form, c, y, summary);

        y = add(form, c, y, title("Events"));
        for (JCheckBox b : new JCheckBox[] {catalogBox, realBox, obsBox, winBox, datBox}) {
            b.setOpaque(false);
            y = add(form, c, y, b);
        }
        maxDistField.setToolTipText("Largest distance between the two stations of a pair (km). Empty: all pairs. "
            + "Needs the station file of the Model page.");
        JPanel distRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        AppPanelStyle.setPanelBackground(distRow);
        distRow.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 0));
        distRow.add(new JLabel("Max pair distance (km)"));
        distRow.add(maxDistField);
        y = add(form, c, y, distRow);

        y = add(form, c, y, title("Picks"));
        poolCsvBox.setOpaque(false);
        y = add(form, c, y, poolCsvBox);

        y = add(form, c, y, title("Output folder"));
        JButton browse = new JButton("\u2026");
        browse.setFocusPainted(false);
        browse.addActionListener(e -> {
            File start = outputField.getText().trim().isEmpty() ? null : new File(outputField.getText().trim());
            File dir = DirectoryChooserHelper.selectDirectory(this, "Output folder", start);
            if (dir != null) {
                outputField.setText(dir.getAbsolutePath());
            }
        });
        JPanel outRow = new JPanel(new BorderLayout(4, 0));
        AppPanelStyle.setPanelBackground(outRow);
        outRow.add(outputField, BorderLayout.CENTER);
        outRow.add(browse, BorderLayout.EAST);
        y = add(form, c, y, outRow);

        JButton export = new JButton("Export");
        export.setFocusPainted(false);
        AppPanelStyle.styleSuccessButton(export);
        export.addActionListener(e -> exportNow());
        JPanel exportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        AppPanelStyle.setPanelBackground(exportRow);
        exportRow.add(export);
        y = add(form, c, y, exportRow);
        statusLabel.setFont(AppPanelStyle.getLabelFont());
        statusLabel.setForeground(AppPanelStyle.getMutedTextColor());
        y = add(form, c, y, statusLabel);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = y;
        filler.weighty = 1;
        form.add(new JPanel(), filler);

        JScrollPane scroll = new JScrollPane(form);
        AppPanelStyle.styleScrollPane(scroll);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new java.awt.Dimension(320, 400));
        setMinimumSize(new java.awt.Dimension(200, 120));

        pool.addListener(p -> javax.swing.SwingUtilities.invokeLater(this::updateSummary));
        model.addListener(m -> javax.swing.SwingUtilities.invokeLater(this::updateSummary));
        updateSummary();
        AppPanelStyle.applyThemeTo(this);
    }

    private static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppPanelStyle.getSectionTitleFont());
        l.setForeground(AppPanelStyle.getTitleColor());
        l.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        return l;
    }

    private static int add(JPanel form, GridBagConstraints c, int y, java.awt.Component comp) {
        c.gridy = y;
        form.add(comp, c);
        return y + 1;
    }

    private void updateSummary() {
        summary.setText(model.events().size() + " event(s), " + pool.size() + " picks in the pool");
    }

    /** Receives the one-line status (for the status bar). */
    public void setStatusSink(Consumer<String> sink) {
        this.statusSink = sink != null ? sink : s -> { };
    }

    private void say(String s) {
        statusLabel.setText(s);
        statusSink.accept(s);
    }

    /** Writes the selected formats into the output folder. */
    public void exportNow() {
        String out = outputField.getText().trim();
        if (out.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Choose an output folder first.", "Export",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        double maxDist = 0;
        String maxRaw = maxDistField.getText().trim();
        if (!maxRaw.isEmpty()) {
            try {
                maxDist = Double.parseDouble(maxRaw);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid max pair distance: " + maxRaw, "Export",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        Path dir = Paths.get(out);
        List<RealAssociator.Event> events = model.events();
        StringBuilder done = new StringBuilder();
        try {
            Map<Integer, String> dats = new HashMap<>();
            boolean perEvent = obsBox.isSelected() || winBox.isSelected() || datBox.isSelected();
            if (perEvent && !events.isEmpty()) {
                RealResultWriter.EventFiles opt = new RealResultWriter.EventFiles();
                opt.obs = obsBox.isSelected();
                opt.winPickfile = winBox.isSelected();
                opt.pairDat = datBox.isSelected();
                opt.maxPairDistKm = maxDist;
                opt.coords = stationCoords(maxDist > 0 || winBox.isSelected());
                if (maxDist > 0 && opt.coords.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                        "The pair-distance limit needs the station file (code lat lon) of the Model page.",
                        "Export", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                RealResultWriter.Counts n = RealResultWriter.writeEventFiles(dir.resolve("events"), events, opt, dats);
                done.append(n.obs()).append(" .obs, ").append(n.pickfiles()).append(" pickfile(s), ")
                    .append(n.dats()).append(" .dat");
                if (n.skippedDats() > 0) {
                    done.append(" (").append(n.skippedDats()).append(" event(s) without a station pair)");
                }
                done.append("; ");
            }
            if (catalogBox.isSelected() && !events.isEmpty()) {
                Map<Integer, String> rel = new HashMap<>();
                dats.forEach((id, name) -> rel.put(id, "events/" + name));
                RealResultWriter.writeCatalogCsv(dir.resolve("catalog_real.csv"), events, rel);
                done.append("catalog_real.csv; ");
            }
            if (realBox.isSelected() && !events.isEmpty()) {
                RealResultWriter.writeRealFiles(dir.resolve("real"), events);
                done.append("real/; ");
            }
            if (poolCsvBox.isSelected() && pool.size() > 0) {
                EqtPickWriter.writeCsv(dir.resolve("picks.csv"), pool.snapshot());
                done.append("picks.csv; ");
            }
        } catch (IOException ex) {
            say("Export failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Export", JOptionPane.ERROR_MESSAGE);
            return;
        }
        say(done.length() == 0 ? "Nothing to export (no events or picks, or no format selected)."
            : "Exported to " + dir.getFileName() + ": " + done.toString().replaceAll("; $", "."));
    }

    private Map<String, CrossCorrelationPickingService.StationCoord> stationCoords(boolean needed) {
        File f = SharedFileManager.getInstance().getStationFile();
        if (!needed || f == null) {
            return Collections.emptyMap();
        }
        try {
            return CrossCorrelationPickingService.loadCoordsForDistanceFilter(f.toPath());
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    // ------------------------------------------------------------------ test hooks

    void setOutputForTest(String dir) {
        outputField.setText(dir);
    }

    void setFormatsForTest(boolean catalog, boolean real, boolean obs, boolean win, boolean dat, boolean poolCsv) {
        catalogBox.setSelected(catalog);
        realBox.setSelected(real);
        obsBox.setSelected(obs);
        winBox.setSelected(win);
        datBox.setSelected(dat);
        poolCsvBox.setSelected(poolCsv);
    }

    String statusForTest() {
        return statusLabel.getText();
    }

    String summaryForTest() {
        return summary.getText();
    }
}
