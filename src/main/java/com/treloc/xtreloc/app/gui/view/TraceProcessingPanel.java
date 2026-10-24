package com.treloc.xtreloc.app.gui.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import com.treloc.xtreloc.app.gui.service.CrossCorrelationPickingService;
import com.treloc.xtreloc.app.gui.service.SacComponentClassifier;
import com.treloc.xtreloc.app.gui.service.SacInventoryService;
import com.treloc.xtreloc.app.gui.service.TraceProcessingService;
import com.treloc.xtreloc.app.gui.service.TraceProcessingService.Operation;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.AppSettingsCache;
import com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper;
import com.treloc.xtreloc.app.gui.util.FrequencySpec;
import com.treloc.xtreloc.app.gui.util.UiFonts;

/**
 * Trace processing: directory-hierarchy based calculation of derived traces (envelope, characteristic function)
 * and cross-correlation lag tables.
 * <p>
 * The directory tree of the input root is shown; selecting a directory and pressing <b>Execute</b> runs the chosen
 * operation (with its own parameter set) on every directory below it that directly contains {@code .sac} files.
 * The hierarchy is mirrored below the output directory, see {@link TraceProcessingService}.
 * Lives as the Cross-corr section of the unified Picking workbench; waveforms stay on the right pane.
 *
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class TraceProcessingPanel {

    private static final Logger logger = Logger.getLogger(TraceProcessingPanel.class.getName());

    // ------------------------------------------------------------------ parameter forms

    /** One parameter set (card) per operation; fields that an operation does not use stay {@code null}. */
    private static final class OperationForm {
        final JPanel panel = new JPanel(new GridBagLayout());
        private final GridBagConstraints gc = new GridBagConstraints();
        private int row;

        JTextField filterMin;
        JTextField filterMax;
        JTextField eig;
        JTextField sta;
        JTextField lta;
        JTextField ccWindow;
        JTextField ccOverlap;
        JTextField ccMaxDist;
        JTextField ccMinCoef;
        JTextField ccMinPairs;

        OperationForm() {
            AppPanelStyle.setPanelBackground(panel);
            gc.insets = new Insets(2, 4, 2, 4);
            gc.anchor = GridBagConstraints.NORTHWEST;
            gc.fill = GridBagConstraints.HORIZONTAL;
        }

        JTextField addField(String label, String initial, String tooltip) {
            JLabel l = new JLabel(label);
            JTextField f = new JTextField(initial, 5);
            f.setColumns(5);
            Dimension pref = f.getPreferredSize();
            f.setPreferredSize(new Dimension(Math.max(52, Math.min(pref.width, 64)), pref.height));
            f.setMaximumSize(new Dimension(68, pref.height));
            if (tooltip != null) {
                l.setToolTipText(tooltip);
                f.setToolTipText(tooltip);
            }
            AppPanelStyle.styleTextField(f);
            gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
            gc.fill = GridBagConstraints.NONE;
            gc.anchor = GridBagConstraints.WEST;
            panel.add(l, gc);
            gc.gridx = 1;
            panel.add(f, gc);
            row++;
            return f;
        }

        void addRow(String label, Component c) {
            gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
            gc.fill = GridBagConstraints.NONE;
            gc.anchor = GridBagConstraints.WEST;
            panel.add(new JLabel(label), gc);
            gc.gridx = 1; gc.weightx = 1.0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(c, gc);
            row++;
        }
    }

    private static final String FILTER_TIP =
        "Empty = no limit on this edge. Low only → high-pass, high only → low-pass, both → band-pass.";
    private static final String CF_INFO =
        "Characteristic function from the two selected horizontals (H1 / H2, e.g. Ha and Hb).\n"
            + "Optional band-pass, then eigen analysis and STA/LTA. Writes one SAC per used station.\n"
            + "On Input, uncheck a component column header to drop that component for every station.";
    private static final String ENV_INFO =
        "Horizontal-vector envelope √(H1²+H2²) from the selected horizontals.\n"
            + "Optional band-pass and a centred moving average. Writes one SAC per used station.\n"
            + "Disable a component column on Input when one channel should be left out.";
    private final SacDirectoryBrowser browser = new SacDirectoryBrowser();
    private final Map<Operation, OperationForm> forms = new HashMap<>();
    private final List<SacInventoryService.StationRow> inventory = new ArrayList<>();
    private final Map<String, Boolean> columnEnabled = new LinkedHashMap<>();
    private List<String> componentColumns = List.of();
    private List<File> selectedSacFiles = List.of();
    private Operation cfKind = Operation.CHARACTERISTIC;
    private boolean fillingTable;

    private JPanel leftPanel;
    private JPanel workSection;
    private JPanel functionsSection;
    private JPanel xcorrSection;
    private JPanel statusSection;
    private JPanel progressSection;
    private JPanel rightPanel;
    private File workingDir;
    private JTextField outputDirField;
    private JLabel selectedSacLabel;
    private JLabel targetLabel;
    private JLabel statusLabel;
    private JButton runButton;
    private JButton xcorrRunButton;
    private JButton cancelButton;
    private JTable unitTable;
    private DefaultTableModel unitModel;
    private JProgressBar progressBar;

    private String outputDir = "";
    private SwingWorker<?, ?> activeWorker;
    private int listingGeneration;

    private static final class Tick {
        final int done;
        final int total;
        final String msg;

        Tick(int done, int total, String msg) {
            this.done = done;
            this.total = total;
            this.msg = msg == null ? "" : msg;
        }
    }

    private void applyTick(Tick t) {
        if (t.total > 0) {
            progressBar.setMaximum(t.total);
            progressBar.setValue(Math.min(t.done, t.total));
        }
        progressBar.setString(t.msg);
        statusLabel.setText(t.msg);
    }

    private CrossCorrelationPickingService.Progress tickingProgress(java.util.function.Consumer<Tick> publish) {
        return new CrossCorrelationPickingService.Progress() {
            @Override
            public void onMessage(String message) {
                publish.accept(new Tick(-1, -1, message));
            }

            @Override
            public void onProgress(int done, int total, String message) {
                publish.accept(new Tick(done, total, message));
            }
        };
    }
    private Consumer<File> inputRootListener;
    private Consumer<File> directoryPreviewListener;
    private Supplier<String> stationFileSupplier;

    public TraceProcessingPanel() {
        String home = AppSettingsCache.snapshot().getHomeDirectory();
        if (home != null && !home.isEmpty() && new File(home).isDirectory()) {
            outputDir = home;
        }
        workSection = createWorkSection();
        statusSection = createStatusPanel();
        progressSection = createProgressSection();
        leftPanel = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(leftPanel);
        leftPanel.add(workSection, BorderLayout.CENTER);
        browser.setDirectoryListener(this::onDirectorySelected);
        refreshLayoutHint();
        refreshExecutionControls();
    }

    /** CF / Envelope nested tabs and Run. */
    public JPanel getFunctionsSection() {
        return functionsSection;
    }

    /** XCorr parameters (Correlation → XCorr). */
    public JPanel getXcorrSection() {
        return xcorrSection;
    }

    /** Progress bar kept visible at the bottom of the settings column. */
    public JPanel getProgressSection() {
        return progressSection;
    }

    public void setStationFileSupplier(Supplier<String> supplier) {
        this.stationFileSupplier = supplier;
    }

    /** Directory chosen in the shared picker tree (CF inventory + default CC folder). */
    public void setWorkingDirectory(File dir) {
        workingDir = dir;
        refreshLayoutHint();
        scanInventory(dir);
    }

    /** SAC files selected in the middle pane (used by Cross-corr Run). */
    public void setSelectedSacFiles(List<File> files) {
        selectedSacFiles = files == null ? List.of() : new ArrayList<>(files);
        if (selectedSacLabel != null) {
            selectedSacLabel.setText(            selectedSacFiles.isEmpty()
                ? "Select CF SAC files in the left tree, then Run."
                : selectedSacFiles.size() + " CF file(s) selected");
        }
    }

    /** True while a batch run is in progress (used to confirm before closing the window). */
    public boolean isBusy() {
        return activeWorker != null && !activeWorker.isDone();
    }

    public JPanel getLeftPanel() {
        return leftPanel;
    }

    public JPanel getRightPanel() {
        return rightPanel;
    }

    public void setInputRootListener(Consumer<File> listener) {
        this.inputRootListener = listener;
    }

    public void setDirectoryPreviewListener(Consumer<File> listener) {
        this.directoryPreviewListener = listener;
    }

    // ------------------------------------------------------------------ layout

    private JPanel createWorkSection() {
        forms.put(Operation.ENVELOPE, buildEnvelopeForm());
        forms.put(Operation.CHARACTERISTIC, buildCfForm());
        forms.put(Operation.CROSS_CORRELATION, buildCrossCorrelationForm());
        functionsSection = buildFunctionsSection();
        xcorrSection = buildCrossCorrTab();
        return functionsSection;
    }

    private JPanel buildFunctionsSection() {
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBackground(AppPanelStyle.getPanelBg());
        stack.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(AppPanelStyle.getPanelBg());
        tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel cfWrap = leftWrap(forms.get(Operation.CHARACTERISTIC).panel);
        JPanel envWrap = leftWrap(forms.get(Operation.ENVELOPE).panel);
        tabs.addTab("CF", cfWrap);
        tabs.addTab("Envelope", envWrap);
        tabs.setTabComponentAt(0, InfoPopup.tabLabel("CF", stack, "Characteristic function", CF_INFO));
        tabs.setTabComponentAt(1, InfoPopup.tabLabel("Envelope", stack, "Envelope", ENV_INFO));
        tabs.addChangeListener(e -> {
            cfKind = tabs.getSelectedIndex() == 1 ? Operation.ENVELOPE : Operation.CHARACTERISTIC;
        });
        stack.add(tabs);

        JPanel exec = createExecuteBar();
        exec.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(exec);
        return stack;
    }

    private static JPanel leftWrap(JPanel form) {
        JPanel wrap = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(wrap);
        wrap.add(form, BorderLayout.NORTH);
        return wrap;
    }

    private JPanel buildCrossCorrTab() {
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBackground(AppPanelStyle.getPanelBg());
        stack.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel form = forms.get(Operation.CROSS_CORRELATION).panel;
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(form);

        selectedSacLabel = new JLabel("Select CF SAC files in the left tree, then Run.");
        selectedSacLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectedSacLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        stack.add(selectedSacLabel);

        xcorrRunButton = new JButton("Run");
        xcorrRunButton.setFont(UiFonts.uiBold(12f));
        Dimension btnSize = new Dimension(96, 28);
        xcorrRunButton.setPreferredSize(btnSize);
        xcorrRunButton.setMinimumSize(btnSize);
        xcorrRunButton.setMaximumSize(btnSize);
        AppPanelStyle.styleSuccessButton(xcorrRunButton);
        xcorrRunButton.setToolTipText("Cross-correlate the selected CF SAC files (no CF recomputation).");
        xcorrRunButton.addActionListener(e -> runCrossCorr());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        AppPanelStyle.setPanelBackground(row);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(xcorrRunButton);
        stack.add(row);
        return stack;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(panel);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightPanel = panel;

        targetLabel = new JLabel("Select a SAC directory.");
        targetLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));
        panel.add(targetLabel, BorderLayout.NORTH);

        unitModel = newInventoryModel(List.of());
        unitTable = new JTable(unitModel);
        unitTable.setFillsViewportHeight(true);
        unitTable.setDefaultRenderer(Boolean.class, new ComponentCellRenderer());
        unitTable.getTableHeader().setDefaultRenderer(new ComponentHeaderRenderer());
        unitTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = unitTable.columnAtPoint(e.getPoint());
                if (col >= 4 && col - 4 < componentColumns.size()) {
                    String name = componentColumns.get(col - 4);
                    boolean on = Boolean.TRUE.equals(columnEnabled.get(name));
                    columnEnabled.put(name, !on);
                    unitTable.getTableHeader().repaint();
                    unitTable.repaint();
                }
            }
        });
        unitTable.getModel().addTableModelListener(e -> persistInventoryEdits());
        JScrollPane tableScroll = new JScrollPane(unitTable);
        AppPanelStyle.styleScrollPane(tableScroll);
        tableScroll.setPreferredSize(new Dimension(220, 180));
        panel.add(tableScroll, BorderLayout.CENTER);
        return panel;
    }

    private DefaultTableModel newInventoryModel(List<String> comps) {
        Object[] cols = new Object[4 + comps.size()];
        cols[0] = "Use";
        cols[1] = "Sta";
        cols[2] = "Hz";
        cols[3] = "Gaps";
        for (int i = 0; i < comps.size(); i++) {
            cols[4 + i] = comps.get(i);
        }
        return new DefaultTableModel(cols, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                if (column == 0) {
                    return true;
                }
                if (column >= 4 && row >= 0 && row < inventory.size()) {
                    return inventory.get(row).hasComponent(componentColumns.get(column - 4));
                }
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 || column >= 4 ? Boolean.class : String.class;
            }
        };
    }

    private void rebuildInventoryTable() {
        fillingTable = true;
        componentColumns = SacInventoryService.uniqueComponentNames(inventory);
        Set<String> keep = new HashSet<>(componentColumns);
        columnEnabled.keySet().removeIf(k -> !keep.contains(k));
        for (String name : componentColumns) {
            columnEnabled.putIfAbsent(name, Boolean.TRUE);
        }
        unitModel = newInventoryModel(componentColumns);
        unitTable.setModel(unitModel);
        unitTable.getColumnModel().getColumn(0).setPreferredWidth(36);
        unitTable.getColumnModel().getColumn(1).setPreferredWidth(48);
        unitTable.getColumnModel().getColumn(2).setPreferredWidth(56);
        unitTable.getColumnModel().getColumn(3).setPreferredWidth(36);
        for (int i = 0; i < componentColumns.size(); i++) {
            unitTable.getColumnModel().getColumn(4 + i).setPreferredWidth(36);
        }
        unitTable.setDefaultRenderer(Boolean.class, new ComponentCellRenderer());
        unitTable.getTableHeader().setDefaultRenderer(new ComponentHeaderRenderer());
        for (SacInventoryService.StationRow row : inventory) {
            Object[] values = new Object[4 + componentColumns.size()];
            values[0] = row.use;
            values[1] = row.station;
            values[2] = row.rateText();
            values[3] = row.gapsText();
            for (int i = 0; i < componentColumns.size(); i++) {
                String name = componentColumns.get(i);
                values[4 + i] = row.hasComponent(name) ? row.selected.contains(name) : null;
            }
            unitModel.addRow(values);
        }
        unitModel.addTableModelListener(e -> persistInventoryEdits());
        fillingTable = false;
    }

    private void persistInventoryEdits() {
        if (fillingTable || unitModel == null) {
            return;
        }
        for (int i = 0; i < inventory.size() && i < unitModel.getRowCount(); i++) {
            SacInventoryService.StationRow row = inventory.get(i);
            row.use = Boolean.TRUE.equals(unitModel.getValueAt(i, 0));
            row.selected.clear();
            for (int c = 0; c < componentColumns.size(); c++) {
                String name = componentColumns.get(c);
                if (row.hasComponent(name) && Boolean.TRUE.equals(unitModel.getValueAt(i, 4 + c))) {
                    row.selected.add(name);
                }
            }
        }
    }

    Set<String> disabledComponentColumns() {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Boolean> e : columnEnabled.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    boolean isComponentActive(String station, String component) {
        persistInventoryEdits();
        if (station == null) {
            return true;
        }
        String st = station.trim().replace(" ", "");
        String cmp = component == null ? "" : component.trim();
        for (SacInventoryService.StationRow row : inventory) {
            if (!row.station.equalsIgnoreCase(st)) {
                continue;
            }
            if (!row.use) {
                return false;
            }
            return row.usesComponent(cmp, disabledComponentColumns());
        }
        return true;
    }

    private final class ComponentCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private final JCheckBox box = new JCheckBox();

        ComponentCellRenderer() {
            box.setHorizontalAlignment(SwingConstants.CENTER);
            box.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            if (column >= 4 && value == null) {
                JLabel empty = new JLabel("");
                empty.setOpaque(true);
                empty.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return empty;
            }
            box.setSelected(Boolean.TRUE.equals(value));
            boolean colOff = column >= 4 && column - 4 < componentColumns.size()
                && !Boolean.TRUE.equals(columnEnabled.get(componentColumns.get(column - 4)));
            box.setEnabled(!colOff);
            box.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return box;
        }
    }

    private final class ComponentHeaderRenderer implements TableCellRenderer {
        private final JCheckBox box = new JCheckBox();
        private final JLabel label = new JLabel();

        ComponentHeaderRenderer() {
            box.setHorizontalAlignment(SwingConstants.CENTER);
            box.setOpaque(true);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JTableHeader header = table.getTableHeader();
            Color bg = header != null ? header.getBackground() : table.getBackground();
            if (column >= 4 && column - 4 < componentColumns.size()) {
                String name = componentColumns.get(column - 4);
                box.setText(name);
                box.setSelected(Boolean.TRUE.equals(columnEnabled.get(name)));
                box.setBackground(bg);
                box.setToolTipText("Use " + name + " for every station. Uncheck to drop this component.");
                return box;
            }
            label.setText(value == null ? "" : value.toString());
            label.setBackground(bg);
            return label;
        }
    }

    private JPanel createProgressSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        AppPanelStyle.setPanelBackground(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        outputDirField = new JTextField(outputDir);
        outputDirField.setEditable(false);
        outputDirField.setToolTipText("Output folder (hierarchy is mirrored below it)");
        AppPanelStyle.styleTextField(outputDirField);
        JButton btn = iconButton("FileView.directoryIcon", "📁", "Output folder");
        btn.addActionListener(e -> selectOutputDirectory());
        JPanel out = new JPanel(new BorderLayout(4, 0));
        out.setOpaque(false);
        out.add(btn, BorderLayout.WEST);
        out.add(outputDirField, BorderLayout.CENTER);
        panel.add(out, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 1);
        progressBar.setStringPainted(true);
        progressBar.setString("");
        statusLabel = new JLabel(" ");
        JPanel mid = new JPanel(new BorderLayout(0, 2));
        AppPanelStyle.setPanelBackground(mid);
        mid.add(progressBar, BorderLayout.CENTER);
        mid.add(statusLabel, BorderLayout.SOUTH);
        panel.add(mid, BorderLayout.CENTER);

        cancelButton = new JButton("Cancel");
        cancelButton.setFont(UiFonts.uiBold(12f));
        Dimension btnSize = new Dimension(96, 28);
        cancelButton.setPreferredSize(btnSize);
        cancelButton.setMinimumSize(btnSize);
        cancelButton.setMaximumSize(btnSize);
        cancelButton.setBackground(new Color(200, 50, 50));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        cancelButton.setOpaque(true);
        cancelButton.addActionListener(e -> {
            if (activeWorker != null && !activeWorker.isDone()) {
                activeWorker.cancel(true);
                statusLabel.setText("Cancelling…");
            }
        });
        JPanel cancelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        AppPanelStyle.setPanelBackground(cancelRow);
        cancelRow.add(cancelButton);
        panel.add(cancelRow, BorderLayout.SOUTH);
        return panel;
    }

    private static JButton iconButton(String iconKey, String fallbackText, String tooltip) {
        JButton b = new JButton();
        Icon icon = UIManager.getIcon(iconKey);
        if (icon != null) {
            b.setIcon(icon);
        } else {
            b.setText(fallbackText);
        }
        b.setToolTipText(tooltip);
        AppPanelStyle.stylePrimaryButton(b);
        return b;
    }

    private OperationForm buildEnvelopeForm() {
        OperationForm f = new OperationForm();
        f.filterMin = f.addField("Freq min (Hz)", "", FILTER_TIP);
        f.filterMax = f.addField("Freq max (Hz)", "", FILTER_TIP);
        f.eig = f.addField("Smooth (s)", "2", "Centred moving-average length for √(H1²+H2²).");
        return f;
    }

    private OperationForm buildCfForm() {
        OperationForm f = new OperationForm();
        f.filterMin = f.addField("Freq min (Hz)", "", FILTER_TIP);
        f.filterMax = f.addField("Freq max (Hz)", "", FILTER_TIP);
        f.eig = f.addField("Eigen (s)", "2", "Moving window of the horizontal eigen-analysis.");
        f.sta = f.addField("STA (s)", "0.5", "STA window");
        f.lta = f.addField("LTA (s)", "10", "LTA window; must be longer than STA.");
        return f;
    }

    private OperationForm buildCrossCorrelationForm() {
        OperationForm f = new OperationForm();
        f.ccWindow = f.addField("CC window (s)", "10", "Sliding-window length");
        f.ccOverlap = f.addField("Overlap", "0.25", "Window step = overlap × CC window.");
        f.ccMaxDist = f.addField("Max dist (km)", "300", "Max pair distance when a station file is set.");
        f.ccMinCoef = f.addField("Min |coef|", "", "Omit pairs below this |correlation|. Blank = keep all.");
        f.ccMinPairs = f.addField("Min pairs", "", "Min pairs per window. Blank = no minimum.");
        return f;
    }

    private JPanel createExecuteBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        AppPanelStyle.setPanelBackground(panel);
        Dimension btnSize = new Dimension(96, 28);
        runButton = new JButton("Run");
        runButton.setFont(UiFonts.uiBold(12f));
        runButton.setPreferredSize(btnSize);
        runButton.setMinimumSize(btnSize);
        runButton.setMaximumSize(btnSize);
        runButton.setToolTipText("Compute CF / envelope from the selected horizontal components and write SAC.");
        AppPanelStyle.styleSuccessButton(runButton);
        runButton.addActionListener(e -> runCf());
        panel.add(runButton);
        return panel;
    }

    private void refreshExecutionControls() {
        boolean running = activeWorker != null && !activeWorker.isDone();
        if (runButton != null) {
            runButton.setEnabled(!running);
        }
        if (xcorrRunButton != null) {
            xcorrRunButton.setEnabled(!running);
        }
        if (cancelButton != null) {
            cancelButton.setEnabled(running);
        }
    }

    // ------------------------------------------------------------------ directory handling

    public void setInputRoot(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File cur = browser.getRoot();
        if (cur != null && cur.getAbsoluteFile().equals(dir.getAbsoluteFile())) {
            return;
        }
        browser.setRoot(dir);
        if (inputRootListener != null) {
            inputRootListener.accept(dir);
        }
    }

    private void selectOutputDirectory() {
        File cur = null;
        if (!outputDir.isEmpty() && new File(outputDir).isDirectory()) {
            cur = new File(outputDir);
        }
        File dir = DirectoryChooserHelper.selectDirectory(leftPanel, "Select Output Directory", cur);
        if (dir != null) {
            setOutputDir(dir.getAbsolutePath());
        }
    }

    private void setOutputDir(String path) {
        outputDir = path == null ? "" : path;
        outputDirField.setText(outputDir);
        refreshLayoutHint();
    }

    private void onDirectorySelected(File dir) {
        refreshLayoutHint();
        scanInventory(dir);
        if (directoryPreviewListener != null) {
            directoryPreviewListener.accept(dir);
        }
    }

    private void refreshLayoutHint() {
        // Output path is the folder button in the progress strip.
    }

    private void scanInventory(File dir) {
        if (dir == null) {
            return;
        }
        final int gen = ++listingGeneration;
        final Path target = dir.toPath();
        targetLabel.setText("Reading SAC…");
        ViewerStatusBar.startLoading("Loading SAC...");
        SwingWorker<List<SacInventoryService.StationRow>, Void> w = new SwingWorker<>() {
            @Override
            protected List<SacInventoryService.StationRow> doInBackground() throws Exception {
                return SacInventoryService.scan(target);
            }

            @Override
            protected void done() {
                ViewerStatusBar.stopLoading();
                if (gen != listingGeneration) {
                    return;
                }
                try {
                    List<SacInventoryService.StationRow> rows = get();
                    inventory.clear();
                    inventory.addAll(rows);
                    rebuildInventoryTable();
                    targetLabel.setText(rows.size() + " station(s)");
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "SAC inventory failed: " + ex.getMessage(), ex);
                    targetLabel.setText("Read failed");
                }
            }
        };
        w.execute();
    }

    // ------------------------------------------------------------------ parameters

    private static double parseDouble(JTextField f) {
        return Double.parseDouble(f.getText().trim());
    }

    /**
     * Reads and validates the parameter set of {@code op}; returns {@code null} (after showing a dialog) on error.
     */
    private CrossCorrelationPickingService.Params readParams(Operation op, String title) {
        OperationForm f = forms.get(op);
        CrossCorrelationPickingService.Params p = new CrossCorrelationPickingService.Params();

        if (op == Operation.CROSS_CORRELATION) {
            p.signalKind = CrossCorrelationPickingService.SignalKind.RAW;
            p.treatAsPrepared = true;
            try {
                p.ccWindowSec = parseDouble(f.ccWindow);
                p.ccOverlapRate = parseDouble(f.ccOverlap);
                p.ccMaxDistKm = parseDouble(f.ccMaxDist);
                String thRaw = f.ccMinCoef.getText().trim();
                if (thRaw.isEmpty()) {
                    p.ccCoefThreshold = 0.0;
                } else {
                    p.ccCoefThreshold = Double.parseDouble(thRaw);
                    if (p.ccCoefThreshold < 0) {
                        JOptionPane.showMessageDialog(leftPanel, "Min |coef| must be ≥ 0.", title,
                            JOptionPane.ERROR_MESSAGE);
                        return null;
                    }
                }
                String mp = f.ccMinPairs.getText().trim();
                if (mp.isEmpty() || "inf".equalsIgnoreCase(mp) || "∞".equals(mp)) {
                    p.ccMinPairCount = null;
                } else {
                    p.ccMinPairCount = Integer.parseInt(mp);
                    if (p.ccMinPairCount < 1) {
                        JOptionPane.showMessageDialog(leftPanel,
                            "Min pairs (window) must be ≥ 1, or blank for no minimum.", title,
                            JOptionPane.ERROR_MESSAGE);
                        return null;
                    }
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(leftPanel, "Invalid numeric parameter: " + ex.getMessage(), title,
                    JOptionPane.ERROR_MESSAGE);
                return null;
            }
            if (p.ccWindowSec <= 0 || p.ccOverlapRate <= 0) {
                JOptionPane.showMessageDialog(leftPanel, "Check the CC window and the overlap rate.", title,
                    JOptionPane.ERROR_MESSAGE);
                return null;
            }
            return p;
        }

        FrequencySpec.Result fr = FrequencySpec.parse(f.filterMin.getText(), f.filterMax.getText());
        if (fr.error != null) {
            JOptionPane.showMessageDialog(leftPanel, fr.error, title, JOptionPane.ERROR_MESSAGE);
            return null;
        }
        p.filterFreqMin = fr.min;
        p.filterFreqMax = fr.max;
        p.signalKind = op == Operation.ENVELOPE
            ? CrossCorrelationPickingService.SignalKind.ENVELOPE
            : CrossCorrelationPickingService.SignalKind.CHARACTERISTIC;

        try {
            p.eigWindowSec = parseDouble(f.eig);
            if (p.signalKind == CrossCorrelationPickingService.SignalKind.CHARACTERISTIC) {
                p.staWindowSec = parseDouble(f.sta);
                p.ltaWindowSec = parseDouble(f.lta);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(leftPanel, "Invalid numeric parameter: " + ex.getMessage(), title,
                JOptionPane.ERROR_MESSAGE);
            return null;
        }

        if (p.eigWindowSec <= 0) {
            JOptionPane.showMessageDialog(leftPanel, "The eigen / smoothing window must be positive.", title,
                JOptionPane.ERROR_MESSAGE);
            return null;
        }
        if (p.signalKind == CrossCorrelationPickingService.SignalKind.CHARACTERISTIC
            && (p.staWindowSec <= 0 || p.ltaWindowSec <= 0 || p.ltaWindowSec <= p.staWindowSec)) {
            JOptionPane.showMessageDialog(leftPanel,
                "STA/LTA: use positive windows with LTA longer than STA.", title, JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return p;
    }

    // ------------------------------------------------------------------ presets (JSON)

    // ------------------------------------------------------------------ execution

    private void runCf() {
        if (activeWorker != null && !activeWorker.isDone()) {
            return;
        }
        persistInventoryEdits();
        Set<String> disabled = disabledComponentColumns();
        final Operation op = cfKind;
        final String title = op.getLabel();
        if (outputDir.isEmpty()) {
            JOptionPane.showMessageDialog(leftPanel, "Choose an output folder.", title,
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        File target = workingDir != null ? workingDir : browser.getSelectedDirectory();
        if (target == null) {
            JOptionPane.showMessageDialog(leftPanel, "Select a SAC directory first.", title,
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        final CrossCorrelationPickingService.Params params = readParams(op, title);
        if (params == null) {
            return;
        }
        List<CrossCorrelationPickingService.StationChannelPair> pairs = new ArrayList<>();
        for (SacInventoryService.StationRow row : inventory) {
            if (!row.use) {
                continue;
            }
            Path a = row.pathForRole(SacComponentClassifier.Role.H1, disabled);
            Path b = row.pathForRole(SacComponentClassifier.Role.H2, disabled);
            if (a == null || b == null || a.equals(b)) {
                continue;
            }
            pairs.add(new CrossCorrelationPickingService.StationChannelPair(row.station, a, b));
        }
        if (pairs.isEmpty()) {
            JOptionPane.showMessageDialog(leftPanel,
                "Enable two horizontal components (e.g. Ha and Hb) for at least one used station.", title,
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        final Path outDir = TraceProcessingService.outputDirFor(op, Paths.get(outputDir),
            target.toPath(), target.toPath());
        progressBar.setValue(0);
        progressBar.setMaximum(pairs.size());
        progressBar.setString("Running…");
        statusLabel.setText("Writing " + title);
        SwingWorker<Integer, Tick> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return CrossCorrelationPickingService.writeDerivedForStations(pairs, outDir, params,
                    tickingProgress(this::publish));
            }

            @Override
            protected void process(List<Tick> chunks) {
                if (!chunks.isEmpty()) {
                    applyTick(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                activeWorker = null;
                refreshExecutionControls();
                try {
                    int n = get();
                    String summary = n + " SAC file(s) → " + outDir;
                    statusLabel.setText(summary);
                    progressBar.setValue(progressBar.getMaximum());
                    progressBar.setString(summary);
                } catch (CancellationException ex) {
                    statusLabel.setText("Cancelled");
                } catch (Exception ex) {
                    Throwable c = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
                    logger.log(Level.SEVERE, title + " failed", c);
                    statusLabel.setText("Failed: " + c.getMessage());
                    JOptionPane.showMessageDialog(leftPanel,
                        c.getMessage() != null ? c.getMessage() : String.valueOf(c),
                        title, JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        activeWorker = worker;
        refreshExecutionControls();
        worker.execute();
    }

    private void runCrossCorr() {
        if (activeWorker != null && !activeWorker.isDone()) {
            return;
        }
        final String title = "Cross-corr";
        if (outputDir.isEmpty()) {
            JOptionPane.showMessageDialog(leftPanel, "Choose an output folder.", title,
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Path> files = new ArrayList<>();
        for (File f : selectedSacFiles) {
            if (f != null && f.isFile()) {
                files.add(f.toPath());
            }
        }
        if (files.size() < 2) {
            JOptionPane.showMessageDialog(leftPanel,
                "Select at least two CF SAC files in the left tree, then Run.",
                title, JOptionPane.WARNING_MESSAGE);
            return;
        }
        final CrossCorrelationPickingService.Params params = readParams(Operation.CROSS_CORRELATION, title);
        if (params == null) {
            return;
        }
        Map<String, CrossCorrelationPickingService.StationCoord> loaded = Collections.emptyMap();
        String supplied = stationFileSupplier == null ? null : stationFileSupplier.get();
        String st = supplied == null ? "" : supplied.trim();
        if (!st.isEmpty()) {
            try {
                loaded = CrossCorrelationPickingService.loadCoordsForDistanceFilter(Paths.get(st));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(leftPanel, "Cannot read station file: " + ex.getMessage(),
                    title, JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        final Map<String, CrossCorrelationPickingService.StationCoord> coords = loaded;
        File target = workingDir != null ? workingDir : new File(outputDir);
        final Path outDir = TraceProcessingService.outputDirFor(Operation.CROSS_CORRELATION,
            Paths.get(outputDir), target.toPath(), target.toPath());
        progressBar.setValue(0);
        progressBar.setMaximum(Math.max(1, files.size()));
        progressBar.setString("Loading…");
        statusLabel.setText("Cross-corr on " + files.size() + " files");
        SwingWorker<Void, Tick> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                CrossCorrelationPickingService.runCorrelationFromFiles(files, outDir, params, coords,
                    tickingProgress(this::publish));
                return null;
            }

            @Override
            protected void process(List<Tick> chunks) {
                if (!chunks.isEmpty()) {
                    applyTick(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                activeWorker = null;
                refreshExecutionControls();
                try {
                    get();
                    statusLabel.setText("Done → " + outDir);
                    progressBar.setString("Done");
                } catch (CancellationException ex) {
                    statusLabel.setText("Cancelled");
                } catch (Exception ex) {
                    Throwable c = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
                    logger.log(Level.SEVERE, "Cross-corr failed", c);
                    statusLabel.setText("Failed: " + c.getMessage());
                    JOptionPane.showMessageDialog(leftPanel,
                        c.getMessage() != null ? c.getMessage() : String.valueOf(c),
                        title, JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        activeWorker = worker;
        refreshExecutionControls();
        worker.execute();
    }
}
