package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.VelocityModelSpec;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.FileChooserHelper;
import com.treloc.xtreloc.app.gui.util.SharedFileManager;
import com.treloc.xtreloc.io.VelocityModelCatalog;
import com.treloc.xtreloc.solver.table.TravelTimeTables;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;

/**
 * Velocity + station picker for the Model tab (Solver-style combo / file path).
 * A 1-D model is pushed to the Solver only while {@link #setApplyToSolver(boolean) apply-to-solver} is on
 * (Source = Velocity). Travel-time source does not bind a velocity. Station is forwarded to the Station tab / map.
 */
public class VelocityModelTablePanel extends JPanel {

    static final String SELECT_FROM_FILE = "Select from file...";
    static final String NONE_ITEM = "";
    static final String NAKAJIMA_ITEM = "3D: " + VelocityModelSpec.NAKAJIMA_LABEL;

    private final List<Consumer<VelocityModelSpec>> listeners = new ArrayList<>();
    private final List<Consumer<File>> stationListeners = new ArrayList<>();
    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JTextField stationField = new JTextField();

    private VelocityModelSpec visualized;
    private String previousItem = NONE_ITEM;
    private boolean applyToSolver;
    private boolean updating;
    private boolean updatingStation;
    private File lastDir;

    public VelocityModelTablePanel() {
        setLayout(new GridBagLayout());
        setBackground(AppPanelStyle.getPanelBg());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(AppPanelStyle.GAP, AppPanelStyle.GAP, AppPanelStyle.GAP, AppPanelStyle.GAP);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(label("Velocity"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        rebuildCombo(NONE_ITEM);
        AppPanelStyle.styleComboBox(modelCombo);
        modelCombo.setToolTipText("Bundled 1-D model, 3-D Nakajima grid, or a file (.nd / .tvel / WIN .aob / 3-D .csv)");
        modelCombo.addActionListener(e -> onModelPicked());
        add(modelCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        JLabel stationLabel = label("Station");
        stationLabel.setToolTipText("Loaded into the Station tab and drawn on the map.");
        add(stationLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        AppPanelStyle.styleTextField(stationField);
        stationField.setToolTipText("Station file (.tbl) or NonLinLoc header. Applied to the Station tab and map.");
        stationField.addActionListener(e -> applyStationPath(stationField.getText()));
        JButton browse = fileBrowseButton("Select station file");
        browse.addActionListener(e -> browseStation());
        JPanel stationRow = new JPanel(new BorderLayout());
        stationRow.setBackground(AppPanelStyle.getPanelBg());
        stationRow.add(browse, BorderLayout.WEST);
        stationRow.add(stationField, BorderLayout.CENTER);
        add(stationRow, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setBackground(AppPanelStyle.getPanelBg());
        add(filler, gbc);

        SharedFileManager.getInstance().addStationFileListener(file -> {
            if (updatingStation || file == null) {
                return;
            }
            updatingStation = true;
            try {
                stationField.setText(file.getAbsolutePath());
            } finally {
                updatingStation = false;
            }
        });
        File existing = SharedFileManager.getInstance().getStationFile();
        if (existing != null) {
            stationField.setText(existing.getAbsolutePath());
        }
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppPanelStyle.getLabelFont());
        l.setForeground(AppPanelStyle.getContentTextColor());
        return l;
    }

    private static JButton fileBrowseButton(String tip) {
        JButton b = new JButton();
        try {
            Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
            if (fileIcon != null) {
                b.setIcon(fileIcon);
            } else {
                b.setText("📄");
            }
        } catch (RuntimeException e) {
            b.setText("📄");
        }
        b.setToolTipText(tip);
        AppPanelStyle.stylePrimaryButton(b);
        return b;
    }

    public void addVisualizeListener(Consumer<VelocityModelSpec> listener) {
        listeners.add(listener);
    }

    public void addStationFileListener(Consumer<File> listener) {
        stationListeners.add(listener);
    }

    public VelocityModelSpec getVisualized() {
        return visualized;
    }

    /**
     * When true (Source = Velocity), a 1-D selection is written to {@link SharedFileManager#setTaupFile}.
     * Travel-time source turns this off so a loaded table is not paired with a velocity model.
     */
    public void setApplyToSolver(boolean apply) {
        applyToSolver = apply;
        if (apply && visualized != null && visualized.kind == VelocityModelSpec.Kind.MODEL_1D) {
            SharedFileManager.getInstance().setTaupFile(visualized.ref);
        }
    }

    public boolean isApplyToSolver() {
        return applyToSolver;
    }

    /** Selects a 1-D model (bundled name or file). Travel-time table specs are ignored. */
    public void selectModel(String ref) {
        if (ref == null || ref.trim().isEmpty() || SELECT_FROM_FILE.equals(ref.trim())) {
            return;
        }
        if (TravelTimeTables.isTableSpec(ref.trim())) {
            return;
        }
        String item = comboItemFor(ref.trim());
        rebuildCombo(item);
        applySpec(specForItem(item), applyToSolver);
    }

    public String getStationPath() {
        return stationField.getText() != null ? stationField.getText().trim() : "";
    }

    JComboBox<String> comboForTest() {
        return modelCombo;
    }

    private void onModelPicked() {
        if (updating) {
            return;
        }
        String selected = (String) modelCombo.getSelectedItem();
        if (SELECT_FROM_FILE.equals(selected)) {
            if (!browseVelocityFile()) {
                updating = true;
                try {
                    modelCombo.setSelectedItem(previousItem);
                } finally {
                    updating = false;
                }
            }
            return;
        }
        if (selected == null || NONE_ITEM.equals(selected)) {
            applySpec(null, applyToSolver);
            return;
        }
        applySpec(specForItem(selected), applyToSolver);
    }

    private boolean browseVelocityFile() {
        JFileChooser fc = new JFileChooser(lastDir);
        FileChooserHelper.setDefaultDirectory(fc);
        fc.setDialogTitle("Select velocity model (.nd, .tvel, WIN struct/.aob, or 3D .csv / .csv.gz)");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        File f = fc.getSelectedFile();
        if (f == null) {
            return false;
        }
        lastDir = f.getParentFile();
        String path = f.getAbsolutePath();
        rebuildCombo(path);
        applySpec(VelocityModelSpec.fromFile(f), applyToSolver);
        return true;
    }

    private void browseStation() {
        JFileChooser fc = new JFileChooser(lastDir);
        FileChooserHelper.setDefaultDirectory(fc);
        fc.setDialogTitle("Select Station File");
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Station / NonLinLoc (*.tbl, *.hdr)", "tbl", "hdr"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (f == null) {
            return;
        }
        lastDir = f.isDirectory() ? f : f.getParentFile();
        applyStationFile(f);
    }

    private void applyStationPath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        applyStationFile(new File(path.trim()));
    }

    void applyStationFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        updatingStation = true;
        try {
            stationField.setText(file.getAbsolutePath());
        } finally {
            updatingStation = false;
        }
        for (Consumer<File> l : new ArrayList<>(stationListeners)) {
            l.accept(file);
        }
    }

    private void applySpec(VelocityModelSpec spec, boolean pushToSolver) {
        boolean same = spec == null ? visualized == null : spec.equals(visualized);
        visualized = spec;
        previousItem = spec == null ? NONE_ITEM : itemForSpec(spec);
        if (!same) {
            for (Consumer<VelocityModelSpec> l : new ArrayList<>(listeners)) {
                l.accept(spec);
            }
        }
        if (pushToSolver) {
            if (spec != null && spec.kind == VelocityModelSpec.Kind.MODEL_1D) {
                SharedFileManager.getInstance().setTaupFile(spec.ref);
            } else if (spec == null) {
                SharedFileManager.getInstance().setTaupFile(null);
            }
        }
    }

    private void rebuildCombo(String select) {
        updating = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            model.addElement(NONE_ITEM);
            for (String name : VelocityModelCatalog.comboModels()) {
                model.addElement(name);
            }
            model.addElement(NAKAJIMA_ITEM);
            if (select != null && !select.isEmpty() && !SELECT_FROM_FILE.equals(select)
                && model.getIndexOf(select) < 0 && !NAKAJIMA_ITEM.equals(select)) {
                model.addElement(select);
            }
            model.addElement(SELECT_FROM_FILE);
            modelCombo.setModel(model);
            if (select != null && model.getIndexOf(select) >= 0) {
                modelCombo.setSelectedItem(select);
                previousItem = select;
            } else {
                modelCombo.setSelectedItem(NONE_ITEM);
                previousItem = NONE_ITEM;
            }
        } finally {
            updating = false;
        }
    }

    private static String comboItemFor(String ref) {
        if (VelocityModelSpec.NAKAJIMA_RESOURCE.equals(ref) || NAKAJIMA_ITEM.equals(ref)) {
            return NAKAJIMA_ITEM;
        }
        String bundled = VelocityModelCatalog.bundledFileName(ref);
        return bundled != null ? bundled : ref;
    }

    private static String itemForSpec(VelocityModelSpec spec) {
        if (spec.kind == VelocityModelSpec.Kind.BUNDLED_3D) {
            return NAKAJIMA_ITEM;
        }
        return spec.ref;
    }

    private static VelocityModelSpec specForItem(String item) {
        if (NAKAJIMA_ITEM.equals(item)) {
            return VelocityModelSpec.bundledNakajima();
        }
        File f = new File(item);
        if (f.isAbsolute() || item.contains(File.separator) || item.contains("/")) {
            return VelocityModelSpec.fromFile(f);
        }
        return VelocityModelSpec.forModelRef(item);
    }
}
