package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.service.AssociationModel;
import com.treloc.xtreloc.app.gui.service.EqtPickWriter;
import com.treloc.xtreloc.app.gui.service.PickPool;
import com.treloc.xtreloc.app.gui.service.RealAssociator;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.FileChooserHelper;
import com.treloc.xtreloc.app.gui.util.SharedFileManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Step 2 of the Absolute workflow: associates the picks of the {@link PickPool} into events with the
 * external REAL program ({@link RealAssociator}). The settings are remembered between sessions.
 */
public class AssociatePanel extends JPanel {

    private final PickPool pool;
    private final AssociationModel model;
    private final Preferences prefs;

    private final JLabel poolLabel = new JLabel();
    private final JTextField executableField = new JTextField();
    private final JTextField ttdbField = new JTextField();
    private final JTextField stationField = new JTextField();
    private final JTextField networkField = new JTextField("XX");
    private final JTextField latField = new JTextField();
    private final JTextField gridRField = new JTextField();
    private final JTextField gridGField = new JTextField();
    private final JTextField velocityField = new JTextField();
    private final JTextField thresholdsField = new JTextField();
    private final JTextField workField = new JTextField();
    private final JButton runButton = new JButton("Run REAL");
    private final JButton cancelButton = new JButton("Cancel");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea logArea = new JTextArea(8, 20);

    private Consumer<String> statusSink = s -> { };
    private Runnable busyListener = () -> { };
    private SwingWorker<RealAssociator.Result, String> worker;
    private volatile boolean busy;

    /**
     * @param prefs where the settings are kept ({@code null}: nothing is read or written, for tests)
     */
    public AssociatePanel(PickPool pool, AssociationModel model, Preferences prefs) {
        super(new BorderLayout());
        this.pool = pool;
        this.model = model;
        this.prefs = prefs;
        AppPanelStyle.setPanelBackground(this);

        RealAssociator.Params defaults = new RealAssociator.Params();
        gridRField.setText(defaults.gridR);
        gridGField.setText(defaults.gridG);
        velocityField.setText(defaults.velocityV);
        thresholdsField.setText(defaults.thresholdsS);
        loadPrefs();
        File station = SharedFileManager.getInstance().getStationFile();
        if (stationField.getText().isEmpty() && station != null) {
            stationField.setText(station.getAbsolutePath());
        }

        JPanel form = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(form);
        form.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        int y = 0;
        y = section(form, y, "Pick pool");
        poolLabel.setFont(AppPanelStyle.getLabelFont());
        y = row(form, y, null, poolLabel, null);
        JButton importButton = new JButton("Import picks\u2026");
        importButton.setToolTipText("Add the picks of a picks.csv (station,phase,component,time,prob) to the pool");
        importButton.setFocusPainted(false);
        importButton.addActionListener(e -> importPicks());
        JButton clearButton = new JButton("Clear pool");
        clearButton.setFocusPainted(false);
        clearButton.addActionListener(e -> pool.clear());
        JPanel poolButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        AppPanelStyle.setPanelBackground(poolButtons);
        poolButtons.add(importButton);
        poolButtons.add(clearButton);
        y = row(form, y, null, poolButtons, null);

        y = section(form, y, "REAL");
        y = row(form, y, "Executable", executableField, browse(executableField, JFileChooser.FILES_ONLY));
        y = row(form, y, "Travel times", ttdbField, browse(ttdbField, JFileChooser.FILES_ONLY));
        ttdbField.setToolTipText("ttdb.txt made with REAL's taup_tt.py; its range and interval must match -R and -G.");
        y = row(form, y, "Stations", stationField, browse(stationField, JFileChooser.FILES_ONLY));
        stationField.setToolTipText("Station file with code, latitude and longitude (the file of the Model page).");
        y = row(form, y, "Network", networkField, null);
        networkField.setToolTipText("Network code of the station names in the pick files (NET.STA.P.txt).");
        y = row(form, y, "Center lat.", latField, null);
        latField.setToolTipText("Latitude that makes degrees and km consistent (-D). Empty: mean of the stations.");

        y = section(form, y, "Grid and thresholds");
        y = row(form, y, "-R", gridRField, null);
        gridRField.setToolTipText("rx/rh/tdx/tdh/tint: horizontal and depth range (deg, km), grid interval, time window.");
        y = row(form, y, "-G", gridGField, null);
        gridGField.setToolTipText("trx/trh/tdx/tdh: range and interval of the travel-time table.");
        y = row(form, y, "-V", velocityField, null);
        velocityField.setToolTipText("vp0/vs0 (km/s) for the velocity of the station corrections.");
        y = row(form, y, "-S", thresholdsField, null);
        thresholdsField.setToolTipText("np0/ns0/nps0/npsboth0/std0/dtps/nrt/drt/nxd/rsel: the thresholds of the association.");

        y = section(form, y, "Work folder");
        y = row(form, y, "Folder", workField, browse(workField, JFileChooser.DIRECTORIES_ONLY));
        workField.setToolTipText("Pick files and REAL's output are written here. Empty: a folder under the temp directory.");

        runButton.setFocusPainted(false);
        AppPanelStyle.styleSuccessButton(runButton);
        runButton.addActionListener(e -> runNow());
        cancelButton.setFocusPainted(false);
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancel());
        JPanel runRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        AppPanelStyle.setPanelBackground(runRow);
        runRow.add(runButton);
        runRow.add(cancelButton);
        y = row(form, y, null, runRow, null);
        statusLabel.setFont(AppPanelStyle.getLabelFont());
        statusLabel.setForeground(AppPanelStyle.getMutedTextColor());
        y = row(form, y, null, statusLabel, null);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = y;
        filler.weighty = 1;
        form.add(new JPanel(), filler);

        JScrollPane formScroll = new JScrollPane(form);
        AppPanelStyle.styleScrollPane(formScroll);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        logArea.setEditable(false);
        logArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        AppPanelStyle.styleScrollPane(logScroll);
        logScroll.setBorder(AppPanelStyle.createTitledSectionBorder("REAL output"));
        logScroll.setPreferredSize(new Dimension(100, 140));

        add(formScroll, BorderLayout.CENTER);
        add(logScroll, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(320, 400));
        setMinimumSize(new Dimension(200, 120));

        pool.addListener(p -> SwingUtilities.invokeLater(this::updatePoolLabel));
        updatePoolLabel();
        AppPanelStyle.applyThemeTo(this);
    }

    // ------------------------------------------------------------------ form helpers

    private static int section(JPanel form, int y, String title) {
        JLabel l = new JLabel(title);
        l.setFont(AppPanelStyle.getSectionTitleFont());
        l.setForeground(AppPanelStyle.getTitleColor());
        l.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 3;
        c.anchor = GridBagConstraints.WEST;
        form.add(l, c);
        return y + 1;
    }

    private static int row(JPanel form, int y, String label, Component field, Component trailing) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(2, 0, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        if (label != null) {
            c.gridx = 0;
            JLabel l = new JLabel(label);
            l.setFont(AppPanelStyle.getLabelFont());
            form.add(l, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            form.add(field, c);
            if (trailing != null) {
                c.gridx = 2;
                c.weightx = 0;
                c.fill = GridBagConstraints.NONE;
                form.add(trailing, c);
            }
        } else {
            c.gridx = 0;
            c.gridwidth = 3;
            form.add(field, c);
        }
        return y + 1;
    }

    private JButton browse(JTextField target, int mode) {
        JButton b = new JButton("\u2026");
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 6, 0, 6));
        b.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(mode);
            FileChooserHelper.setDefaultDirectory(fc);
            String cur = target.getText().trim();
            if (!cur.isEmpty()) {
                File f = new File(cur);
                if (f.exists()) {
                    fc.setSelectedFile(f);
                }
            }
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                target.setText(fc.getSelectedFile().getAbsolutePath());
                if (target == stationField) {
                    SharedFileManager.getInstance().setStationFile(fc.getSelectedFile());
                }
            }
        });
        return b;
    }

    private void updatePoolLabel() {
        poolLabel.setText(pool.size() + " picks (P " + pool.count("P") + ", S " + pool.count("S") + ")");
        runButton.setEnabled(!busy);
    }

    // ------------------------------------------------------------------ settings

    private static final String[] KEYS = {"exe", "ttdb", "station", "net", "lat", "R", "G", "V", "S", "work"};

    private JTextField[] fields() {
        return new JTextField[] {executableField, ttdbField, stationField, networkField, latField, gridRField,
            gridGField, velocityField, thresholdsField, workField};
    }

    private void loadPrefs() {
        if (prefs == null) {
            return;
        }
        JTextField[] f = fields();
        for (int i = 0; i < KEYS.length; i++) {
            String v = prefs.get("real." + KEYS[i], null);
            if (v != null) {
                f[i].setText(v);
            }
        }
    }

    private void savePrefs() {
        if (prefs == null) {
            return;
        }
        JTextField[] f = fields();
        for (int i = 0; i < KEYS.length; i++) {
            prefs.put("real." + KEYS[i], f[i].getText().trim());
        }
    }

    /** The settings of the form; a blank work folder becomes a folder under the temp directory. */
    RealAssociator.Params readParams() {
        RealAssociator.Params p = new RealAssociator.Params();
        p.executable = path(executableField);
        p.travelTimeTable = path(ttdbField);
        p.stationFile = path(stationField);
        p.network = networkField.getText().trim().isEmpty() ? "XX" : networkField.getText().trim();
        String lat = latField.getText().trim();
        if (!lat.isEmpty()) {
            p.latCenter = Double.parseDouble(lat);
        }
        p.gridR = gridRField.getText().trim();
        p.gridG = gridGField.getText().trim();
        p.velocityV = velocityField.getText().trim();
        p.thresholdsS = thresholdsField.getText().trim();
        Path work = path(workField);
        p.workDir = work != null ? work : Paths.get(System.getProperty("java.io.tmpdir"), "xtreloc-real");
        return p;
    }

    private static Path path(JTextField f) {
        String t = f.getText().trim();
        return t.isEmpty() ? null : Paths.get(t);
    }

    void applyParams(RealAssociator.Params p) {
        executableField.setText(p.executable == null ? "" : p.executable.toString());
        ttdbField.setText(p.travelTimeTable == null ? "" : p.travelTimeTable.toString());
        stationField.setText(p.stationFile == null ? "" : p.stationFile.toString());
        networkField.setText(p.network);
        latField.setText(Double.isFinite(p.latCenter) ? String.valueOf(p.latCenter) : "");
        gridRField.setText(p.gridR);
        gridGField.setText(p.gridG);
        velocityField.setText(p.velocityV);
        thresholdsField.setText(p.thresholdsS);
        workField.setText(p.workDir == null ? "" : p.workDir.toString());
    }

    // ------------------------------------------------------------------ actions

    /** Receives the one-line status (for the status bar). */
    public void setStatusSink(Consumer<String> sink) {
        this.statusSink = sink != null ? sink : s -> { };
    }

    /** Called when a run starts or ends, to enable the Run command accordingly. */
    public void setBusyListener(Runnable listener) {
        this.busyListener = listener != null ? listener : () -> { };
    }

    public boolean isBusy() {
        return busy;
    }

    private void setBusy(boolean b) {
        busy = b;
        runButton.setEnabled(!b);
        cancelButton.setEnabled(b);
        busyListener.run();
    }

    private void say(String s) {
        statusLabel.setText(s);
        statusSink.accept(s);
    }

    private void importPicks() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Picks CSV");
        FileChooserHelper.setDefaultDirectory(fc);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            int added = pool.addAll(EqtPickWriter.readCsv(fc.getSelectedFile().toPath()));
            say(added + " pick(s) added to the pool.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Import picks", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Runs REAL on the pool in the background; the result goes to the {@link AssociationModel}. */
    public void runNow() {
        if (busy) {
            return;
        }
        RealAssociator.Params params;
        try {
            params = readParams();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "The center latitude is not a number.", "REAL",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        savePrefs();
        List<com.treloc.xtreloc.app.gui.service.EqtPicker.Pick> picks = pool.snapshot();
        logArea.setText("");
        setBusy(true);
        say("REAL running on " + picks.size() + " picks...");
        worker = new SwingWorker<>() {
            @Override
            protected RealAssociator.Result doInBackground() throws Exception {
                return RealAssociator.run(params, picks, this::publish);
            }

            @Override
            protected void process(List<String> lines) {
                for (String l : lines) {
                    logArea.append(l + "\n");
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    RealAssociator.Result r = get();
                    model.setEvents(r.events());
                    say(r.events().size() + " event(s) from " + picks.size() + " picks in " + r.days()
                        + " day(s). Work folder: " + r.workDir());
                } catch (java.util.concurrent.CancellationException ex) {
                    say("REAL cancelled.");
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    say("REAL failed: " + c.getMessage());
                    JOptionPane.showMessageDialog(AssociatePanel.this, c.getMessage(), "REAL",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    public void cancel() {
        if (worker != null) {
            worker.cancel(true);
        }
    }

    // ------------------------------------------------------------------ test hooks

    boolean waitForIdle(long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (busy && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        return !busy;
    }

    String statusTextForTest() {
        return statusLabel.getText();
    }

    String logTextForTest() {
        return logArea.getText();
    }

    boolean runEnabledForTest() {
        return runButton.isEnabled();
    }

    String poolTextForTest() {
        return poolLabel.getText();
    }
}
