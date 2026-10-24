package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.service.ArrivalTime;
import com.treloc.xtreloc.app.gui.service.PickEditor;
import com.treloc.xtreloc.app.gui.service.PickProposal;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * The absolute picks of the open event as a table: current pick, pending automatic proposal and the
 * difference between them, one row per station and phase. Proposals can be applied (all, only the
 * missing ones, or the selected rows) or discarded; picks can be removed; Undo / Redo cover every change.
 */
public class PickTablePanel extends JPanel implements PickEditor.Listener {

    private static final String[] COLUMNS = {"Station", "Phase", "Pick", "Proposal", "\u0394 (s)", "Method", "Prob"};

    private final PickEditor editor;
    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JButton applySelected = new JButton("Apply");
    private final JButton remove = new JButton("Remove");
    private final JButton undo = new JButton("Undo");
    private final JButton redo = new JButton("Redo");
    private final List<String[]> rowKeys = new ArrayList<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private List<PickProposal> proposals = new ArrayList<>();

    public PickTablePanel(PickEditor editor) {
        super(new BorderLayout(0, 2));
        this.editor = editor;
        editor.addListener(this);
        setBackground(AppPanelStyle.getPanelBg());

        AppPanelStyle.styleTable(table);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtons();
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    removeSelected();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        AppPanelStyle.styleScrollPane(scroll);
        scroll.setPreferredSize(new Dimension(240, 150));
        add(scroll, BorderLayout.CENTER);

        applySelected.setToolTipText("Make the selected proposals the picks");
        applySelected.addActionListener(e -> applySelected());
        remove.setToolTipText("Remove the selected picks (Delete). Undo brings them back.");
        remove.addActionListener(e -> removeSelected());
        undo.addActionListener(e -> editor.undo());
        redo.addActionListener(e -> editor.redo());
        for (JButton b : new JButton[] {applySelected, remove, undo, redo}) {
            b.setFocusPainted(false);
            b.setMargin(new java.awt.Insets(1, 6, 1, 6));
        }
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        buttons.setBackground(AppPanelStyle.getPanelBg());
        buttons.add(applySelected);
        buttons.add(remove);
        buttons.add(undo);
        buttons.add(redo);
        add(buttons, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        rebuild();
    }

    /** Called after the table changed (picks or proposals), for example to redraw the waveforms. */
    public void addChangeListener(Runnable r) {
        if (r != null) {
            changeListeners.add(r);
        }
    }

    @Override
    public void picksChanged(PickEditor e) {
        rebuild();
        fire();
    }

    /** Replaces the pending proposals. */
    public void setProposals(List<PickProposal> list) {
        proposals = list == null ? new ArrayList<>() : new ArrayList<>(list);
        rebuild();
        fire();
    }

    public List<PickProposal> proposals() {
        return new ArrayList<>(proposals);
    }

    public void discardProposals() {
        if (!proposals.isEmpty()) {
            setProposals(null);
        }
    }

    /**
     * Applies every proposal as one undoable step and clears the proposals.
     *
     * @return number of picks that changed
     */
    public int applyAll(boolean onlyEmpty) {
        int n = editor.apply(proposals, onlyEmpty);
        proposals = new ArrayList<>();
        rebuild();
        fire();
        return n;
    }

    /** Applies the proposals of the selected rows; the other proposals stay pending. */
    public int applySelected() {
        List<PickProposal> chosen = new ArrayList<>();
        for (int row : table.getSelectedRows()) {
            PickProposal p = proposalOf(rowKeys.get(row));
            if (p != null) {
                chosen.add(p);
            }
        }
        if (chosen.isEmpty()) {
            return 0;
        }
        proposals.removeAll(chosen);
        return editor.apply(chosen, false);
    }

    /** Removes the picks of the selected rows (proposals of those rows are kept). */
    public int removeSelected() {
        List<String[]> keys = new ArrayList<>();
        for (int row : table.getSelectedRows()) {
            keys.add(rowKeys.get(row));
        }
        return editor.removeAll(keys);
    }

    private PickProposal proposalOf(String[] key) {
        for (PickProposal p : proposals) {
            if (p.station().replace(" ", "").equals(key[0]) && p.phase().equals(key[1])) {
                return p;
            }
        }
        return null;
    }

    private void rebuild() {
        List<String[]> selected = new ArrayList<>();
        for (int row : table.getSelectedRows()) {
            if (row < rowKeys.size()) {
                selected.add(rowKeys.get(row));
            }
        }
        Map<String, String[]> keys = new LinkedHashMap<>();
        for (ArrivalTime a : editor.picks()) {
            keys.put(a.getStationName() + "|" + a.getPhaseDescriptor(),
                new String[] {a.getStationName(), a.getPhaseDescriptor()});
        }
        for (PickProposal p : proposals) {
            String st = p.station().replace(" ", "");
            keys.put(st + "|" + p.phase(), new String[] {st, p.phase()});
        }
        List<String[]> sorted = new ArrayList<>(keys.values());
        sorted.sort(Comparator.<String[], String>comparing(k -> k[0], String.CASE_INSENSITIVE_ORDER)
            .thenComparing(k -> "P".equals(k[1]) ? 0 : 1));

        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
        model.setRowCount(0);
        rowKeys.clear();
        for (String[] k : sorted) {
            ArrivalTime pick = editor.get(k[0], k[1]);
            PickProposal prop = proposalOf(k);
            String delta = "";
            if (pick != null && prop != null) {
                delta = String.format(Locale.ROOT, "%+.3f",
                    (prop.time().getTime() - pick.getArrivalTime().getTime()) / 1000.0);
            }
            model.addRow(new Object[] {k[0], k[1],
                pick == null ? "" : fmt.format(pick.getArrivalTime()),
                prop == null ? "" : fmt.format(prop.time()),
                delta, prop == null ? "" : prop.method(),
                prop == null || Double.isNaN(prop.score()) ? ""
                    : String.format(Locale.ROOT, "%.2f", prop.score())});
            rowKeys.add(k);
        }
        for (String[] s : selected) {
            for (int i = 0; i < rowKeys.size(); i++) {
                if (rowKeys.get(i)[0].equals(s[0]) && rowKeys.get(i)[1].equals(s[1])) {
                    table.addRowSelectionInterval(i, i);
                }
            }
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean anyProposal = false;
        boolean anyPick = false;
        for (int row : table.getSelectedRows()) {
            if (row >= rowKeys.size()) {
                continue;
            }
            anyProposal |= proposalOf(rowKeys.get(row)) != null;
            anyPick |= editor.get(rowKeys.get(row)[0], rowKeys.get(row)[1]) != null;
        }
        applySelected.setEnabled(anyProposal);
        remove.setEnabled(anyPick);
        undo.setEnabled(editor.canUndo());
        redo.setEnabled(editor.canRedo());
        undo.setToolTipText(editor.canUndo() ? "Undo: " + editor.undoLabel() : "Nothing to undo");
        redo.setToolTipText(editor.canRedo() ? "Redo: " + editor.redoLabel() : "Nothing to redo");
    }

    private void fire() {
        for (Runnable r : changeListeners) {
            r.run();
        }
    }

    /** Times of the pending proposals by station and phase, for the waveform overlay. */
    public Map<String, Date> proposalTimes(String phase) {
        Map<String, Date> out = new LinkedHashMap<>();
        for (PickProposal p : proposals) {
            if (phase.equals(p.phase())) {
                out.put(p.station().replace(" ", ""), p.time());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ test hooks

    int rowCount() {
        return model.getRowCount();
    }

    Object cell(int row, int col) {
        return model.getValueAt(row, col);
    }

    void selectRows(int... rows) {
        table.clearSelection();
        for (int r : rows) {
            table.addRowSelectionInterval(r, r);
        }
    }

    boolean applyEnabled() {
        return applySelected.isEnabled();
    }

    boolean removeEnabled() {
        return remove.isEnabled();
    }

    boolean undoEnabled() {
        return undo.isEnabled();
    }

    void clickUndo() {
        undo.doClick();
    }
}
