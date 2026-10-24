package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.service.AssociationModel;
import com.treloc.xtreloc.app.gui.service.RealAssociator;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/**
 * Right pane of the Associate and Export steps: the events found by REAL and, below, the picks that
 * belong to the selected event.
 */
public class AssociationResultPanel extends JPanel implements AssociationModel.Listener {

    private static final String[] EVENT_COLUMNS = {"ID", "Time", "Lat", "Lon", "Dep (km)", "Std (s)", "nP", "nS", "Gap"};
    private static final String[] PHASE_COLUMNS = {"Station", "Phase", "Comp", "Time", "TT (s)", "Res (s)", "Weight"};

    private final AssociationModel model;
    private final JLabel summary = new JLabel();
    private final DefaultTableModel eventModel = readOnly(EVENT_COLUMNS);
    private final DefaultTableModel phaseModel = readOnly(PHASE_COLUMNS);
    private final JTable eventTable = new JTable(eventModel);
    private final JTable phaseTable = new JTable(phaseModel);
    private final JButton mapButton = new JButton("Add to Map");
    private Consumer<List<RealAssociator.Event>> mapHandler;

    public AssociationResultPanel(AssociationModel model) {
        super(new BorderLayout());
        this.model = model;
        AppPanelStyle.setPanelBackground(this);

        summary.setFont(AppPanelStyle.getSectionTitleFont());
        summary.setForeground(AppPanelStyle.getTitleColor());
        mapButton.setFocusPainted(false);
        mapButton.setToolTipText("Show the events as a catalog layer on the Model page's map");
        mapButton.addActionListener(e -> addToMap());
        JPanel header = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(header);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, AppPanelStyle.getBorderColor()),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        header.add(summary, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        AppPanelStyle.setPanelBackground(buttons);
        buttons.add(mapButton);
        header.add(buttons, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        AppPanelStyle.styleTable(eventTable);
        eventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showPhases();
            }
        });
        AppPanelStyle.styleTable(phaseTable);
        JScrollPane eventScroll = new JScrollPane(eventTable);
        AppPanelStyle.styleScrollPane(eventScroll);
        JScrollPane phaseScroll = new JScrollPane(phaseTable);
        AppPanelStyle.styleScrollPane(phaseScroll);
        phaseScroll.setBorder(AppPanelStyle.createTitledSectionBorder("Picks of the selected event"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eventScroll, phaseScroll);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        model.addListener(this);
        refresh();
    }

    private static DefaultTableModel readOnly(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /** What "Add to Map" does with the events. */
    public void setMapHandler(Consumer<List<RealAssociator.Event>> handler) {
        this.mapHandler = handler;
        refresh();
    }

    /** Shows the events of the model; may be called from the thread that changed it. */
    @Override
    public void eventsChanged(AssociationModel changed) {
        if (SwingUtilities.isEventDispatchThread()) {
            refresh();
        } else {
            SwingUtilities.invokeLater(this::refresh);
        }
    }

    public void addToMap() {
        List<RealAssociator.Event> events = model.events();
        if (mapHandler != null && !events.isEmpty()) {
            mapHandler.accept(events);
        }
    }

    private void refresh() {
        List<RealAssociator.Event> events = model.events();
        eventModel.setRowCount(0);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
        for (RealAssociator.Event e : events) {
            eventModel.addRow(new Object[] {e.id(), fmt.format(e.origin()), String.format(Locale.ROOT, "%.4f", e.lat()),
                String.format(Locale.ROOT, "%.4f", e.lon()), String.format(Locale.ROOT, "%.2f", e.depth()),
                String.format(Locale.ROOT, "%.3f", e.std()), e.np(), e.ns(), String.format(Locale.ROOT, "%.0f", e.gap())});
        }
        summary.setText(events.isEmpty() ? "No events yet. Run REAL on the Associate page."
            : events.size() + " event(s)");
        mapButton.setEnabled(mapHandler != null && !events.isEmpty());
        if (!events.isEmpty()) {
            eventTable.setRowSelectionInterval(0, 0);
        }
        showPhases();
    }

    private void showPhases() {
        phaseModel.setRowCount(0);
        int row = eventTable.getSelectedRow();
        List<RealAssociator.Event> events = model.events();
        if (row < 0 || row >= events.size()) {
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
        for (RealAssociator.Phase p : events.get(row).phases()) {
            phaseModel.addRow(new Object[] {p.station(), p.phase(), p.component(), fmt.format(p.time()),
                String.format(Locale.ROOT, "%.3f", p.travelTime()), String.format(Locale.ROOT, "%.3f", p.residual()),
                String.format(Locale.ROOT, "%.2f", p.weight())});
        }
    }

    // ------------------------------------------------------------------ test hooks

    int eventRowsForTest() {
        return eventModel.getRowCount();
    }

    int phaseRowsForTest() {
        return phaseModel.getRowCount();
    }

    String summaryForTest() {
        return summary.getText();
    }

    boolean mapEnabledForTest() {
        return mapButton.isEnabled();
    }

    void selectEventForTest(int row) {
        eventTable.setRowSelectionInterval(row, row);
    }
}
