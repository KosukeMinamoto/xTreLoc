package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Layer tree of the Model tab (GIS-style table of contents). It replaces the former
 * Model / Station / Catalog / Shapefile tabs: selecting a node shows the matching detail panel, and
 * catalog / shapefile nodes carry a visibility checkbox.
 *
 * <p>The tree holds no state of its own beyond what it displays. Visibility is owned by the
 * catalog and shapefile tables; this panel only reports clicks through {@link Listener} and is
 * refreshed through {@link #setCatalogs} / {@link #setShapefiles}.</p>
 */
public class LayerTreePanel extends JPanel {

    /** What a tree node stands for. */
    public enum Kind {
        MODEL, SOLVER, STATIONS, CATALOGS, CATALOG, SHAPEFILES, SHAPEFILE
    }

    /** Receives selection changes and checkbox clicks. */
    public interface Listener {
        /** A node was selected; {@code index} is the row inside its group, or -1 for a group or leaf node. */
        void onSelect(Kind kind, int index);

        /** The checkbox of a catalog or shapefile node was clicked. */
        void onToggle(Kind kind, int index, boolean visible);
    }

    private static final class Node {
        final Kind kind;
        final String title;
        final int index;
        Boolean visible;

        Node(Kind kind, String title, int index, Boolean visible) {
            this.kind = kind;
            this.title = title;
            this.index = index;
            this.visible = visible;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Layers");
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final JTree tree = new JTree(model);
    private final Renderer renderer = new Renderer();
    private final List<String> catalogNames = new ArrayList<>();
    private final List<Boolean> catalogVisible = new ArrayList<>();
    private final List<String> shapefileNames = new ArrayList<>();
    private final List<Boolean> shapefileVisible = new ArrayList<>();
    private Listener listener;
    private boolean solverAvailable;
    private boolean updating;

    public LayerTreePanel() {
        setLayout(new BorderLayout());
        AppPanelStyle.setPanelBackground(this);
        setBorder(AppPanelStyle.createTitledSectionBorder("Layers"));

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(22);
        tree.setCellRenderer(renderer);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setToggleClickCount(0);
        AppPanelStyle.styleTree(tree);
        tree.addTreeSelectionListener(e -> {
            if (updating || listener == null) {
                return;
            }
            Node n = selectedNode();
            if (n != null) {
                listener.onSelect(n.kind, n.index);
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleClick(e);
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        AppPanelStyle.styleScrollPane(scroll);
        add(scroll, BorderLayout.CENTER);
        rebuild();
        select(Kind.MODEL, -1);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Adds the Solver node once a Solver panel exists. */
    public void setSolverAvailable(boolean available) {
        if (solverAvailable != available) {
            solverAvailable = available;
            rebuild();
        }
    }

    public void setCatalogs(List<String> names, List<Boolean> visible) {
        catalogNames.clear();
        catalogVisible.clear();
        if (names != null) {
            for (int i = 0; i < names.size(); i++) {
                catalogNames.add(names.get(i));
                catalogVisible.add(visible != null && i < visible.size() && Boolean.TRUE.equals(visible.get(i)));
            }
        }
        rebuild();
    }

    public void setShapefiles(List<String> names, List<Boolean> visible) {
        shapefileNames.clear();
        shapefileVisible.clear();
        if (names != null) {
            for (int i = 0; i < names.size(); i++) {
                shapefileNames.add(names.get(i));
                shapefileVisible.add(visible != null && i < visible.size() && Boolean.TRUE.equals(visible.get(i)));
            }
        }
        rebuild();
    }

    /** Selects a node without notifying the listener. */
    public void select(Kind kind, int index) {
        boolean prev = updating;
        updating = true;
        try {
            TreePath path = findPath(kind, index);
            if (path != null) {
                tree.setSelectionPath(path);
            } else {
                tree.clearSelection();
            }
        } finally {
            updating = prev;
        }
    }

    /** Titles of the top-level nodes, in display order. */
    public List<String> topLevelTitles() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            out.add(((Node) ((DefaultMutableTreeNode) root.getChildAt(i)).getUserObject()).title);
        }
        return out;
    }

    /** Titles of the children of a group node (CATALOGS or SHAPEFILES). */
    public List<String> childTitles(Kind group) {
        List<String> out = new ArrayList<>();
        DefaultMutableTreeNode g = findGroup(group);
        if (g != null) {
            for (int i = 0; i < g.getChildCount(); i++) {
                out.add(((Node) ((DefaultMutableTreeNode) g.getChildAt(i)).getUserObject()).title);
            }
        }
        return out;
    }

    /** Visibility flag of a catalog or shapefile child node; false when the node does not exist. */
    public boolean isChildVisible(Kind kind, int index) {
        DefaultMutableTreeNode n = findNode(kind, index);
        return n != null && Boolean.TRUE.equals(((Node) n.getUserObject()).visible);
    }

    /** Programmatic equivalent of clicking a checkbox (used by tests). */
    void clickCheckbox(Kind kind, int index) {
        DefaultMutableTreeNode n = findNode(kind, index);
        if (n == null) {
            return;
        }
        Node node = (Node) n.getUserObject();
        if (node.visible == null) {
            return;
        }
        toggle(node);
    }

    // ------------------------------------------------------------------ internals

    private void rebuild() {
        Node sel = selectedNode();
        boolean prev = updating;
        updating = true;
        try {
            root.removeAllChildren();
            root.add(leaf(Kind.MODEL, "Model"));
            if (solverAvailable) {
                root.add(leaf(Kind.SOLVER, "Solver"));
            }
            root.add(leaf(Kind.STATIONS, "Stations"));
            DefaultMutableTreeNode cats = leaf(Kind.CATALOGS, "Hypocenters");
            for (int i = 0; i < catalogNames.size(); i++) {
                cats.add(new DefaultMutableTreeNode(
                    new Node(Kind.CATALOG, catalogNames.get(i), i, catalogVisible.get(i))));
            }
            root.add(cats);
            DefaultMutableTreeNode shp = leaf(Kind.SHAPEFILES, "Shapefiles");
            for (int i = 0; i < shapefileNames.size(); i++) {
                shp.add(new DefaultMutableTreeNode(
                    new Node(Kind.SHAPEFILE, shapefileNames.get(i), i, shapefileVisible.get(i))));
            }
            root.add(shp);
            model.reload();
            for (int r = 0; r < tree.getRowCount(); r++) {
                tree.expandRow(r);
            }
            TreePath restore = sel == null ? null : findPath(sel.kind, sel.index);
            if (restore != null) {
                tree.setSelectionPath(restore);
            }
        } finally {
            updating = prev;
        }
    }

    private static DefaultMutableTreeNode leaf(Kind kind, String title) {
        return new DefaultMutableTreeNode(new Node(kind, title, -1, null));
    }

    private Node selectedNode() {
        TreePath p = tree.getSelectionPath();
        if (p == null) {
            return null;
        }
        Object o = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
        return o instanceof Node ? (Node) o : null;
    }

    private DefaultMutableTreeNode findGroup(Kind kind) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) root.getChildAt(i);
            if (((Node) n.getUserObject()).kind == kind) {
                return n;
            }
        }
        return null;
    }

    private DefaultMutableTreeNode findNode(Kind kind, int index) {
        if (kind == Kind.CATALOG || kind == Kind.SHAPEFILE) {
            DefaultMutableTreeNode g = findGroup(kind == Kind.CATALOG ? Kind.CATALOGS : Kind.SHAPEFILES);
            if (g != null) {
                for (int i = 0; i < g.getChildCount(); i++) {
                    DefaultMutableTreeNode n = (DefaultMutableTreeNode) g.getChildAt(i);
                    if (((Node) n.getUserObject()).index == index) {
                        return n;
                    }
                }
            }
            return null;
        }
        return findGroup(kind);
    }

    private TreePath findPath(Kind kind, int index) {
        DefaultMutableTreeNode n = findNode(kind, index);
        if (n == null && (kind == Kind.CATALOG || kind == Kind.SHAPEFILE)) {
            n = findGroup(kind == Kind.CATALOG ? Kind.CATALOGS : Kind.SHAPEFILES);
        }
        return n == null ? null : new TreePath(n.getPath());
    }

    private void handleClick(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(o instanceof Node)) {
            return;
        }
        Node n = (Node) o;
        if (n.visible == null) {
            return;
        }
        Rectangle b = tree.getPathBounds(path);
        if (b != null && e.getX() <= b.x + renderer.checkWidth()) {
            toggle(n);
        }
    }

    private void toggle(Node n) {
        boolean next = !Boolean.TRUE.equals(n.visible);
        n.visible = next;
        if (n.kind == Kind.CATALOG && n.index < catalogVisible.size()) {
            catalogVisible.set(n.index, next);
        } else if (n.kind == Kind.SHAPEFILE && n.index < shapefileVisible.size()) {
            shapefileVisible.set(n.index, next);
        }
        tree.repaint();
        if (listener != null) {
            listener.onToggle(n.kind, n.index, next);
        }
    }

    /** Plain label for group and fixed nodes, a checkbox with the title for catalog / shapefile nodes. */
    private final class Renderer implements TreeCellRenderer {
        private final DefaultTreeCellRenderer label = new DefaultTreeCellRenderer();
        private final JCheckBox check = new JCheckBox();
        private final int checkWidth = new JCheckBox().getPreferredSize().width;

        Renderer() {
            label.setLeafIcon(null);
            label.setOpenIcon(null);
            label.setClosedIcon(null);
            label.setBackgroundSelectionColor(AppPanelStyle.getPrimaryButtonColor());
            label.setTextSelectionColor(Color.WHITE);
            label.setBorderSelectionColor(null);
            check.setOpaque(true);
        }

        int checkWidth() {
            return checkWidth;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int rowIdx,
                                                      boolean hasFocus) {
            Object o = value instanceof DefaultMutableTreeNode
                ? ((DefaultMutableTreeNode) value).getUserObject() : value;
            Node n = o instanceof Node ? (Node) o : null;
            Color bg = selected ? AppPanelStyle.getPrimaryButtonColor() : t.getBackground();
            Color fg = selected ? Color.WHITE : t.getForeground();
            if (n != null && n.visible != null) {
                check.setText(n.title);
                check.setSelected(Boolean.TRUE.equals(n.visible));
                check.setBackground(bg);
                check.setForeground(fg);
                check.setFont(t.getFont());
                return check;
            }
            boolean group = n != null && (n.kind == Kind.CATALOGS || n.kind == Kind.SHAPEFILES);
            label.setBackgroundNonSelectionColor(t.getBackground());
            label.setTextNonSelectionColor(t.getForeground());
            Component c = label.getTreeCellRendererComponent(t, n == null ? String.valueOf(o) : n.title,
                selected, expanded, true, rowIdx, false);
            c.setFont(group ? t.getFont().deriveFont(java.awt.Font.BOLD) : t.getFont());
            return c;
        }
    }
}
