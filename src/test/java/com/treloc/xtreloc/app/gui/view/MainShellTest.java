package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The menu bar / tool bar / navigator / status bar of the main window. */
public class MainShellTest {

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    @Test
    public void menuBarHasTheClassicMenusInOrder() throws Exception {
        System.setProperty("java.awt.headless", "true");
        JMenuBar[] bar = new JMenuBar[1];
        edt(() -> bar[0] = AppMenuBar.create(new MainCommands()));
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < bar[0].getMenuCount(); i++) {
            titles.add(bar[0].getMenu(i).getText());
        }
        assertEquals(List.of("File", "Edit", "View", "Tools", "Window", "Help"), titles);
        JMenu view = bar[0].getMenu(2);
        List<String> radios = new ArrayList<>();
        for (int i = 0; i < view.getItemCount(); i++) {
            if (view.getItem(i) instanceof javax.swing.JRadioButtonMenuItem) {
                radios.add(view.getItem(i).getText());
            }
        }
        assertEquals("one radio item per page, in workflow order", NavigationPanel.PAGES, radios);
        assertEquals("Model", view.getItem(0).getText());
        JMenu tools = bar[0].getMenu(3);
        List<String> toolItems = new ArrayList<>();
        for (int i = 0; i < tools.getItemCount(); i++) {
            if (tools.getItem(i) != null) {
                toolItems.add(tools.getItem(i).getText());
            }
        }
        assertTrue(toolItems.toString(), toolItems.contains("Associate Picks (REAL)"));
        assertEquals("Settings\u2026", toolItems.get(toolItems.size() - 1));
    }

    @Test
    public void pickCommandsFollowThePageAndTheEditor() throws Exception {
        System.setProperty("java.awt.headless", "true");
        MainCommands c = new MainCommands();
        PickViewerPanel[] pick = new PickViewerPanel[1];
        edt(() -> pick[0] = new PickViewerPanel());

        assertFalse("disabled before the Pick page", c.runAutoPick.isEnabled());
        edt(() -> c.showPage(NavigationPanel.PICK, pick[0]));
        assertTrue(c.runAutoPick.isEnabled());
        assertTrue(c.exportPicks.isEnabled());
        assertFalse("nothing to undo yet", c.undo.isEnabled());
        assertFalse("no proposals", c.applyAll.isEnabled());
        assertFalse("no event linked", c.relocate.isEnabled());

        edt(() -> pick[0].pickEditorForTest().set("ST01", "Z", "P", new Date(1_000_000)));
        edt(() -> c.showPage(NavigationPanel.PICK, pick[0]));
        assertTrue(c.undo.isEnabled());
        assertEquals(1, pick[0].pickCounts()[0]);

        edt(() -> c.showPage(NavigationPanel.FUNCTION, pick[0]));
        assertTrue("SAC folders can be opened on the Relative pages", c.openSacFolder.isEnabled());
        assertFalse(c.undo.isEnabled());
        assertFalse(c.runAutoPick.isEnabled());

        edt(() -> c.showPage(NavigationPanel.EXPORT, pick[0]));
        assertFalse(c.openSacFolder.isEnabled());
        assertTrue(c.exportAbsolute.isEnabled());
        assertEquals("only the shown page is selected", 1,
            c.pages.values().stream().filter(a -> Boolean.TRUE.equals(a.getValue(javax.swing.Action.SELECTED_KEY))).count());
        assertEquals(Boolean.TRUE, c.pages.get(NavigationPanel.EXPORT).getValue(javax.swing.Action.SELECTED_KEY));
    }

    @Test
    public void stateListenersHearPickEdits() throws Exception {
        System.setProperty("java.awt.headless", "true");
        PickViewerPanel[] pick = new PickViewerPanel[1];
        int[] calls = {0};
        edt(() -> {
            pick[0] = new PickViewerPanel();
            pick[0].addStateListener(() -> calls[0]++);
            pick[0].pickEditorForTest().set("ST01", "Z", "P", new Date(1_000_000));
        });
        assertTrue(calls[0] > 0);
        assertTrue(pick[0].canUndoPick());
        edt(pick[0]::undoPick);
        assertFalse(pick[0].canUndoPick());
        assertTrue(pick[0].canRedoPick());
    }

    @org.junit.Rule
    public org.junit.rules.TemporaryFolder tmp = new org.junit.rules.TemporaryFolder();

    @Test
    public void statusListenersHearTheStatusLine() throws Exception {
        System.setProperty("java.awt.headless", "true");
        java.io.File empty = tmp.newFolder("no_sac");
        List<String> got = new ArrayList<>();
        edt(() -> {
            PickViewerPanel p = new PickViewerPanel();
            p.addStatusListener(got::add);
            p.previewDirectory(empty);
        });
        assertEquals(List.of("No SAC files in no_sac"), got);
    }

    @Test
    public void toolBarButtonsShareTheActionsOfTheMenu() throws Exception {
        System.setProperty("java.awt.headless", "true");
        MainCommands c = new MainCommands();
        PageToolBar[] tb = new PageToolBar[1];
        JButton[] undo = new JButton[1];
        edt(() -> {
            tb[0] = new PageToolBar();
            undo[0] = tb[0].addButton(NavigationPanel.PICK, c.undo);
            tb[0].showPage(NavigationPanel.PICK);
        });
        assertSame(c.undo, undo[0].getAction());
        assertFalse(undo[0].isEnabled());
        c.undo.setEnabled(true);
        assertTrue(undo[0].isEnabled());
        assertEquals(NavigationPanel.PICK, tb[0].pageForTest());
    }

    @Test
    public void navigatorSwitchesPagesAndShowsTheContextOfTheCurrentPage() throws Exception {
        System.setProperty("java.awt.headless", "true");
        NavigationPanel[] nav = new NavigationPanel[1];
        List<String> heard = new ArrayList<>();
        JPanel context = new JPanel();
        edt(() -> {
            nav[0] = new NavigationPanel();
            nav[0].setPageListener(heard::add);
            nav[0].setContext(NavigationPanel.PICK, context);
        });
        assertEquals(NavigationPanel.MODEL, nav[0].selectedPage());
        assertNull(nav[0].currentContext());

        edt(() -> nav[0].clickPageForTest(NavigationPanel.PICK));
        assertEquals(List.of("Pick"), heard);
        assertSame(context, nav[0].currentContext());
        assertSame(nav[0].currentContext().getParent().getParent(), nav[0]);

        edt(() -> nav[0].selectPage(NavigationPanel.MODEL));
        assertEquals("selectPage does not call the listener", 1, heard.size());
        assertEquals(NavigationPanel.MODEL, nav[0].selectedPage());
        assertNull(nav[0].currentContext());
        assertNotNull(nav[0].treeForTest().getSelectionPath());
        assertEquals(NavigationPanel.MODEL,
            String.valueOf(((javax.swing.tree.DefaultMutableTreeNode)
                nav[0].treeForTest().getSelectionPath().getLastPathComponent()).getUserObject()));
    }

    @Test
    public void headingsOpenTheirFirstStage() throws Exception {
        System.setProperty("java.awt.headless", "true");
        NavigationPanel[] nav = new NavigationPanel[1];
        List<String> heard = new ArrayList<>();
        edt(() -> {
            nav[0] = new NavigationPanel();
            nav[0].setPageListener(heard::add);
            nav[0].setContext(NavigationPanel.PICK, new JPanel());
            nav[0].setContext(NavigationPanel.FUNCTION, new JPanel());
        });
        // rows: 0 root, 1 Model, 2 Absolute, 3 Pick, 4 Associate, 5 Export, 6 Relative, 7 Function, 8 Xcorr
        assertEquals(9, nav[0].treeForTest().getRowCount());
        edt(() -> nav[0].treeForTest().setSelectionRow(6));
        assertEquals(List.of("Function"), heard);
        assertEquals(NavigationPanel.FUNCTION, nav[0].selectedPage());
        edt(() -> nav[0].treeForTest().setSelectionRow(4));
        assertEquals(List.of("Function", "Associate"), heard);
        assertNull("the Associate page has no folder tree", nav[0].currentContext());
        assertEquals(NavigationPanel.ABSOLUTE, NavigationPanel.groupOf(NavigationPanel.EXPORT));
        assertEquals(NavigationPanel.RELATIVE, NavigationPanel.groupOf(NavigationPanel.XCORR));
        assertNull(NavigationPanel.groupOf(NavigationPanel.MODEL));
    }

    @Test
    public void clickingTheRootHeadingKeepsThePage() throws Exception {
        System.setProperty("java.awt.headless", "true");
        NavigationPanel[] nav = new NavigationPanel[1];
        List<String> heard = new ArrayList<>();
        edt(() -> {
            nav[0] = new NavigationPanel();
            nav[0].setPageListener(heard::add);
            nav[0].treeForTest().setSelectionRow(0);
        });
        assertTrue(heard.isEmpty());
        assertEquals(NavigationPanel.MODEL, nav[0].selectedPage());
        assertEquals(1, nav[0].treeForTest().getSelectionRows()[0]);
    }

    @Test
    public void statusBarShowsMessageAndCounts() throws Exception {
        System.setProperty("java.awt.headless", "true");
        MainStatusBar[] bar = new MainStatusBar[1];
        edt(() -> {
            bar[0] = new MainStatusBar();
            bar[0].setPicks(3, 2);
            bar[0].setMessage("   ");
        });
        assertEquals("Picks: P 3 / S 2", bar[0].picksForTest());
        assertEquals("Ready", bar[0].messageForTest());
        edt(() -> bar[0].setMessage("Applied 3 pick(s)."));
        assertEquals("Applied 3 pick(s).", bar[0].messageForTest());
    }

    @Test
    public void settingsButtonStaysOnEveryPageAndOpensThroughTheCommand() throws Exception {
        System.setProperty("java.awt.headless", "true");
        MainCommands c = new MainCommands();
        PageToolBar[] bar = new PageToolBar[1];
        int[] opened = {0};
        edt(() -> {
            bar[0] = new PageToolBar();
            bar[0].addButton(NavigationPanel.MODEL, c.openCatalog);
            bar[0].addGlobalButton(c.openSettings);
            c.openSettings.on(() -> opened[0]++);
        });
        for (String page : new String[] {NavigationPanel.MODEL, NavigationPanel.PICK}) {
            edt(() -> bar[0].showPage(page));
            assertEquals(1, bar[0].globalBarForTest().getComponentCount());
            assertTrue(bar[0].globalBarForTest().isVisible());
        }
        edt(() -> ((javax.swing.JButton) bar[0].globalBarForTest().getComponent(0)).doClick());
        assertEquals(1, opened[0]);
    }
}
