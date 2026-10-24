package com.treloc.xtreloc.app.gui.view;

import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;

/** The classic menu bar: File, Edit, View, Tools, Window, Help. */
public final class AppMenuBar {

    private AppMenuBar() {}

    public static JMenuBar create(MainCommands c) {
        JMenuBar bar = new JMenuBar();

        JMenu file = menu("File", 'F');
        file.add(item(c.openSacFolder));
        file.add(item(c.openCatalog));
        file.addSeparator();
        file.add(item(c.exportPicks));
        file.addSeparator();
        file.add(item(c.exit));

        JMenu edit = menu("Edit", 'E');
        edit.add(item(c.undo));
        edit.add(item(c.redo));
        edit.addSeparator();
        edit.add(item(c.applyAll));
        edit.add(item(c.discard));

        JMenu view = menu("View", 'V');
        ButtonGroup pages = new ButtonGroup();
        for (String page : NavigationPanel.PAGES) {
            String group = NavigationPanel.groupOf(page);
            if (group == null || page.equals(firstOf(group))) {
                if (view.getMenuComponentCount() > 0) {
                    view.addSeparator();
                }
                if (group != null) {
                    JMenuItem heading = new JMenuItem(group);
                    heading.setEnabled(false);
                    view.add(heading);
                }
            }
            JRadioButtonMenuItem r = new JRadioButtonMenuItem(c.pages.get(page));
            pages.add(r);
            view.add(r);
        }
        view.addSeparator();
        view.add(new JCheckBoxMenuItem(c.toggleNavigator));
        view.add(new JCheckBoxMenuItem(c.toggleOutput));
        view.add(new JCheckBoxMenuItem(c.toggleStatusBar));

        JMenu tools = menu("Tools", 'T');
        tools.add(item(c.runAutoPick));
        tools.add(item(c.pickAllToPool));
        tools.addSeparator();
        tools.add(item(c.runAssociate));
        tools.add(item(c.addToMap));
        tools.add(item(c.exportAbsolute));
        tools.addSeparator();
        tools.add(item(c.relocate));
        tools.add(item(c.clearWorking));
        tools.addSeparator();
        tools.add(item(c.openSettings));

        JMenu window = menu("Window", 'W');
        window.add(item(c.resetLayout));

        JMenu help = menu("Help", 'H');
        help.add(item(c.checkUpdates));
        help.addSeparator();
        help.add(item(c.about));

        bar.add(file);
        bar.add(edit);
        bar.add(view);
        bar.add(tools);
        bar.add(window);
        bar.add(help);
        return bar;
    }

    private static String firstOf(String group) {
        for (String p : NavigationPanel.PAGES) {
            if (group.equals(NavigationPanel.groupOf(p))) {
                return p;
            }
        }
        return null;
    }

    private static JMenu menu(String title, char mnemonic) {
        JMenu m = new JMenu(title);
        m.setMnemonic(mnemonic);
        return m;
    }

    private static JMenuItem item(Action a) {
        return new JMenuItem(a);
    }
}
