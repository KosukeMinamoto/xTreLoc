package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * The left pane of the main window, like the folder pane of Windows Explorer: a small tree of the
 * workflow whose leaves switch the page, and below it the tree that belongs to the current page
 * (for the picking pages: the SAC directories).
 *
 * <pre>
 * Model
 * Absolute   Pick -&gt; Associate -&gt; Export      (absolute arrival times)
 * Relative   Function -&gt; Xcorr                 (station-pair travel-time differences)
 * </pre>
 */
public class NavigationPanel extends JPanel {

    public static final String MODEL = "Model";
    public static final String ABSOLUTE = "Absolute";
    public static final String RELATIVE = "Relative";
    // Absolute stages
    public static final String PICK = "Pick";
    public static final String ASSOCIATE = "Associate";
    public static final String EXPORT = "Export";
    // Relative stages
    public static final String FUNCTION = "Function";
    public static final String XCORR = "Xcorr";

    /** All pages in tree order. */
    public static final java.util.List<String> PAGES =
        java.util.List.of(MODEL, PICK, ASSOCIATE, EXPORT, FUNCTION, XCORR);

    /** The heading a page belongs to ({@code null} for Model). */
    public static String groupOf(String page) {
        return switch (page) {
            case PICK, ASSOCIATE, EXPORT -> ABSOLUTE;
            case FUNCTION, XCORR -> RELATIVE;
            default -> null;
        };
    }

    /** True for the pages that show SAC waveforms (the directory tree is their context). */
    public static boolean isWaveformPage(String page) {
        return PICK.equals(page) || FUNCTION.equals(page) || XCORR.equals(page);
    }

    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("xTreLoc");
    private final Map<String, DefaultMutableTreeNode> pageNodes = new LinkedHashMap<>();
    private final Map<String, String> firstPageOfGroup = new HashMap<>();
    private final Map<String, JComponent> contexts = new HashMap<>();
    private final JTree tree;
    private final JPanel contextHost = new JPanel(new BorderLayout());
    private Consumer<String> pageListener;
    private String page = MODEL;
    private boolean adjusting;

    public NavigationPanel() {
        super(new BorderLayout());
        AppPanelStyle.setPanelBackground(this);

        Map<String, Icon> icons = new HashMap<>();
        DefaultMutableTreeNode absolute = new DefaultMutableTreeNode(ABSOLUTE);
        DefaultMutableTreeNode relative = new DefaultMutableTreeNode(RELATIVE);
        for (String name : PAGES) {
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(name);
            pageNodes.put(name, n);
            String group = groupOf(name);
            if (group == null) {
                root.add(n);
            } else {
                DefaultMutableTreeNode parent = ABSOLUTE.equals(group) ? absolute : relative;
                if (parent.getParent() == null) {
                    root.add(parent);
                }
                parent.add(n);
                firstPageOfGroup.putIfAbsent(group, name);
            }
        }
        icons.put(MODEL, icon("Connection.png"));
        icons.put(ABSOLUTE, icon("ManPick.png"));
        icons.put(RELATIVE, icon("Solver.png"));
        icons.put("xTreLoc", icon("logo.png"));

        tree = new JTree(new DefaultTreeModel(root));
        tree.setRootVisible(true);
        tree.setShowsRootHandles(false);
        tree.setRowHeight(22);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        AppPanelStyle.styleTree(tree);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean focus) {
                super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
                Icon i = icons.get(String.valueOf(((DefaultMutableTreeNode) value).getUserObject()));
                if (i != null) {
                    setIcon(i);
                }
                return this;
            }
        });
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        tree.addTreeSelectionListener(e -> {
            if (adjusting || e.getNewLeadSelectionPath() == null) {
                return;
            }
            Object o = ((DefaultMutableTreeNode) e.getNewLeadSelectionPath().getLastPathComponent()).getUserObject();
            String chosen = String.valueOf(o);
            if (pageNodes.containsKey(chosen)) {
                showPage(chosen, true);
            } else if (firstPageOfGroup.containsKey(chosen)) {
                showPage(firstPageOfGroup.get(chosen), true); // a heading opens its first stage
            } else {
                selectNode(page); // the root is only a heading
            }
        });
        tree.setPreferredSize(new Dimension(120, 9 * 22 + 8));
        JPanel top = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(top);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppPanelStyle.getBorderColor()));
        top.add(tree, BorderLayout.CENTER);

        AppPanelStyle.setPanelBackground(contextHost);
        add(top, BorderLayout.NORTH);
        add(contextHost, BorderLayout.CENTER);
        setMinimumSize(new Dimension(100, 0));
        setPreferredSize(new Dimension(170, 400));
        selectNode(page);
    }

    private static Icon icon(String name) {
        return MainCommands.smallIcon(name);
    }

    /** Called with the page name when the user picks a page in the tree. */
    public void setPageListener(Consumer<String> listener) {
        this.pageListener = listener;
    }

    public String selectedPage() {
        return page;
    }

    /** Selects a page from code (menu, "Open in Seismic"); the page listener is not called. */
    public void selectPage(String name) {
        if (pageNodes.containsKey(name)) {
            showPage(name, false);
        }
    }

    /** The component shown under the tree while {@code pageName} is selected ({@code null}: nothing). */
    public void setContext(String pageName, JComponent component) {
        if (component == null) {
            contexts.remove(pageName);
        } else {
            contexts.put(pageName, component);
        }
        if (pageName.equals(page)) {
            showContext();
        }
    }

    /** The component currently shown under the tree, or {@code null}. */
    public JComponent currentContext() {
        return contexts.get(page);
    }

    private void showPage(String name, boolean notify) {
        boolean changed = !name.equals(page);
        page = name;
        selectNode(name);
        showContext();
        if (notify && changed && pageListener != null) {
            pageListener.accept(name);
        }
    }

    private void selectNode(String name) {
        adjusting = true;
        try {
            tree.setSelectionPath(new TreePath(pageNodes.get(name).getPath()));
        } finally {
            adjusting = false;
        }
    }

    private void showContext() {
        contextHost.removeAll();
        JComponent c = contexts.get(page);
        if (c != null) {
            contextHost.add(c, BorderLayout.CENTER);
        }
        contextHost.revalidate();
        contextHost.repaint();
    }

    // ------------------------------------------------------------------ test hooks

    JTree treeForTest() {
        return tree;
    }

    void clickPageForTest(String name) {
        tree.setSelectionPath(new TreePath(pageNodes.get(name).getPath()));
    }
}
