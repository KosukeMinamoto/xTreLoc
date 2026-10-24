package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.BundledImageLoader;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;

/**
 * The commands of the main window. The menu bar and the tool bars share these {@link Action}s, so
 * enabling or disabling one updates both. The handlers are set by the code that builds the window.
 */
public final class MainCommands {

    /** An action that runs a replaceable handler. */
    public static final class Command extends AbstractAction {
        private Runnable handler = () -> { };

        Command(String name, String icon, KeyStroke accelerator, String tip) {
            super(name);
            if (icon != null) {
                putValue(Action.SMALL_ICON, smallIcon(icon));
            }
            if (accelerator != null) {
                putValue(Action.ACCELERATOR_KEY, accelerator);
            }
            if (tip != null) {
                putValue(Action.SHORT_DESCRIPTION, tip);
            }
        }

        public Command on(Runnable h) {
            this.handler = h == null ? () -> { } : h;
            return this;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            handler.run();
        }
    }

    // File
    public final Command openSacFolder;
    public final Command openCatalog;
    public final Command exportPicks;
    public final Command exit;
    // Edit
    public final Command undo;
    public final Command redo;
    public final Command applyAll;
    public final Command discard;
    // View
    /** The page commands (radio items of the View menu), keyed by the names of {@link NavigationPanel#PAGES}. */
    public final java.util.Map<String, Command> pages = new java.util.LinkedHashMap<>();
    public final Command toggleNavigator;
    public final Command toggleOutput;
    public final Command toggleStatusBar;
    // Tools
    public final Command runAutoPick;
    public final Command pickAllToPool;
    public final Command runAssociate;
    public final Command addToMap;
    public final Command exportAbsolute;
    public final Command relocate;
    public final Command clearWorking;
    public final Command openSettings;
    // Window / Help
    public final Command resetLayout;
    public final Command checkUpdates;
    public final Command about;

    public MainCommands() {
        int m = menuMask();
        openSacFolder = new Command("Open SAC Folder\u2026", "Add.png", ks(KeyEvent.VK_O, m),
            "Add a folder of SAC files to the Seismic directory tree");
        openCatalog = new Command("Open Catalog\u2026", "Viewer.png", ks(KeyEvent.VK_O, m | InputEvent.SHIFT_DOWN_MASK),
            "Load a hypocenter catalog (CSV) into the Model page");
        exportPicks = new Command("Export Picks\u2026", "Save.png", ks(KeyEvent.VK_S, m),
            "Write the picks of the open event (.obs / differential .dat)");
        exit = new Command("Exit", null, ks(KeyEvent.VK_Q, m), null);

        undo = new Command("Undo", "Refresh.png", ks(KeyEvent.VK_Z, m), "Undo the last pick edit");
        redo = new Command("Redo", null, ks(KeyEvent.VK_Y, m), "Redo the pick edit that was undone");
        applyAll = new Command("Apply All Proposals", "Check.png", null,
            "Make every automatic proposal a pick (one Undo step)");
        discard = new Command("Discard Proposals", "Remove.png", null,
            "Throw the automatic proposals away; the picks are not changed");

        int key = KeyEvent.VK_1;
        for (String page : NavigationPanel.PAGES) {
            pages.put(page, new Command(page, null, ks(key++, m), null));
        }
        toggleNavigator = new Command("Navigator", null, null, "Show or hide the left pane");
        toggleOutput = new Command("Output", null, null, "Show or hide the log pane");
        toggleStatusBar = new Command("Status Bar", null, null, null);

        runAutoPick = new Command("Run Auto-pick", "ManPick.png", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
            "Auto-pick the displayed traces with the selected method; the result is a proposal");
        pickAllToPool = new Command("Pick All Peaks \u2192 Pool", null, null,
            "Pick every peak above the thresholds (event or continuous data) and add it to the pick pool");
        runAssociate = new Command("Associate Picks (REAL)", "Solver.png", null,
            "Associate the picks of the pool into events with REAL");
        addToMap = new Command("Add Events to Map", "Viewer.png", null,
            "Show the associated events as a catalog layer on the Model page");
        exportAbsolute = new Command("Export Events and Picks\u2026", "Save.png", null,
            "Write the selected formats of the Export page");
        relocate = new Command("Relocate Event \u2192 Working", "Solver.png", null,
            "Locate the open event from its picks and show the result on the map");

        clearWorking = new Command("Clear Working Layer", "Remove.png", null,
            "Remove the events relocated from edited picks from the Working layer of the map");
        openSettings = new Command("Settings\u2026", "Settings.png", ks(KeyEvent.VK_COMMA, m),
            "Open the application settings window");

        resetLayout = new Command("Reset Layout", null, null, "Restore the default pane sizes");
        checkUpdates = new Command("Check for Updates\u2026", null, null, null);
        about = new Command("About xTreLoc", "Help.png", null, null);

        for (Command c : new Command[] {toggleNavigator, toggleOutput, toggleStatusBar}) {
            c.putValue(Action.SELECTED_KEY, Boolean.FALSE);
        }
        toggleNavigator.putValue(Action.SELECTED_KEY, Boolean.TRUE);
        toggleOutput.putValue(Action.SELECTED_KEY, Boolean.TRUE);
        toggleStatusBar.putValue(Action.SELECTED_KEY, Boolean.TRUE);
        pages.values().forEach(c -> c.putValue(Action.SELECTED_KEY, Boolean.FALSE));
        pages.get(NavigationPanel.MODEL).putValue(Action.SELECTED_KEY, Boolean.TRUE);
        clearWorking.setEnabled(false);
        addToMap.setEnabled(false);
        showPage(NavigationPanel.MODEL, null);
    }

    /**
     * Marks the page radio items and enables the commands of the page.
     *
     * @param page the page shown (a name of {@link NavigationPanel#PAGES})
     * @param pick the Pick page's viewer ({@code null}: the pick commands stay disabled)
     */
    public void showPage(String page, PickViewerPanel pick) {
        pages.forEach((name, c) -> c.putValue(Action.SELECTED_KEY, name.equals(page)));
        boolean waveforms = NavigationPanel.isWaveformPage(page) && pick != null;
        boolean picking = NavigationPanel.PICK.equals(page) && pick != null;
        openSacFolder.setEnabled(waveforms);
        exportPicks.setEnabled(picking);
        runAutoPick.setEnabled(picking);
        pickAllToPool.setEnabled(picking);
        undo.setEnabled(picking && pick.canUndoPick());
        redo.setEnabled(picking && pick.canRedoPick());
        applyAll.setEnabled(picking && pick.hasProposals());
        discard.setEnabled(picking && pick.hasProposals());
        relocate.setEnabled(picking && pick.canRelocate());
        exportAbsolute.setEnabled(NavigationPanel.EXPORT.equals(page));
    }

    static int menuMask() {
        return java.awt.GraphicsEnvironment.isHeadless()
            ? InputEvent.CTRL_DOWN_MASK : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private static KeyStroke ks(int key, int mask) {
        return KeyStroke.getKeyStroke(key, mask);
    }

    private static final java.util.Map<String, javax.swing.Icon> ICONS = new java.util.HashMap<>();

    /** 16 px icon of a bundled image, cached (loading is slow on the event thread). */
    static synchronized javax.swing.Icon smallIcon(String name) {
        return ICONS.computeIfAbsent(name, n -> {
            try {
                ImageIcon raw = BundledImageLoader.loadImageIcon(n);
                if (raw == null || raw.getImage() == null) {
                    return null;
                }
                return new ImageIcon(raw.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
            } catch (RuntimeException e) {
                return null;
            }
        });
    }
}
