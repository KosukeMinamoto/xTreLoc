package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper;
import com.treloc.xtreloc.app.gui.util.UiFonts;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Multi-root directory tree. {@code +} adds a SAC folder, {@code −} removes the selected root.
 * Expanding a folder lists subdirectories and waveform files ({@code .sac}, {@code .s}).
 * Ctrl/Command-click selects files or several folders; those waveforms are stacked, not overlaid.
 */
public class SacDirectoryBrowser extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(SacDirectoryBrowser.class.getName());
    private static final String LOADING = "Loading...";

    private final DefaultMutableTreeNode hiddenRoot = new DefaultMutableTreeNode("SAC");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(hiddenRoot);
    private final JTree tree;
    private final List<File> roots = new ArrayList<>();
    private final Map<Path, String> badges = new HashMap<>();

    private File currentDir;
    private File[] currentFiles = new File[0];

    private Consumer<File> directoryListener;
    private Consumer<List<File>> fileSelectionListener;

    public SacDirectoryBrowser() {
        setLayout(new BorderLayout());
        AppPanelStyle.setPanelBackground(this);

        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        AppPanelStyle.styleTree(tree);
        tree.setCellRenderer(new BadgeRenderer());

        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (node.getUserObject() instanceof File && hasPlaceholderChild(node)) {
                    node.removeAllChildren();
                    loadChildren(node, (File) node.getUserObject());
                    treeModel.nodeStructureChanged(node);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // nothing
            }
        });

        tree.addTreeSelectionListener(e -> onSelectionChanged());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        AppPanelStyle.setPanelBackground(bar);
        JButton add = compactMark("+", "Add a SAC directory");
        JButton remove = compactMark("\u2212", "Remove the selected directory");
        add.addActionListener(e -> chooseAndAddRoot());
        remove.addActionListener(e -> removeSelectedRoot());
        bar.add(add);
        bar.add(remove);

        JPanel treePanel = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(treePanel);
        treePanel.setBorder(AppPanelStyle.createTitledSectionBorder("Directories"));
        JScrollPane treeScroll = new JScrollPane(tree);
        AppPanelStyle.styleScrollPane(treeScroll);
        treePanel.add(bar, BorderLayout.NORTH);
        treePanel.add(treeScroll, BorderLayout.CENTER);
        add(treePanel, BorderLayout.CENTER);
    }

    static boolean isSacFileName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".sac") || n.endsWith(".s");
    }

    static boolean isSacFile(File f) {
        return f != null && f.isFile() && isSacFileName(f.getName());
    }

    public void setDirectoryListener(Consumer<File> listener) {
        this.directoryListener = listener;
    }

    public void setFileSelectionListener(Consumer<List<File>> listener) {
        this.fileSelectionListener = listener;
    }

    /** Replaces all roots with {@code dir} (preset / single-folder callers). */
    public void setRoot(File dir) {
        hiddenRoot.removeAllChildren();
        roots.clear();
        badges.clear();
        treeModel.reload();
        addRoot(dir);
    }

    public void addRoot(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File abs = dir.getAbsoluteFile();
        for (File existing : roots) {
            if (existing.getAbsoluteFile().equals(abs)) {
                selectRoot(existing);
                return;
            }
        }
        roots.add(abs);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(abs);
        if (hasExpandableContent(abs)) {
            node.add(new DefaultMutableTreeNode(LOADING));
        }
        hiddenRoot.add(node);
        treeModel.reload(hiddenRoot);
        tree.expandPath(new TreePath(hiddenRoot));
        TreePath path = new TreePath(new Object[] { hiddenRoot, node });
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
        tree.expandPath(path);
    }

    public File getRoot() {
        return roots.isEmpty() ? null : roots.get(0);
    }

    public File getSelectedDirectory() {
        return currentDir != null ? currentDir : (roots.isEmpty() ? null : roots.get(0));
    }

    public List<File> getSacFiles() {
        return Collections.unmodifiableList(Arrays.asList(currentFiles));
    }

    public void setDividerLocation(double proportion) {
        // file list removed; kept so callers compile
    }

    private void chooseAndAddRoot() {
        File cur = getSelectedDirectory();
        File dir = DirectoryChooserHelper.selectDirectory(this, "Add SAC directory", cur);
        if (dir != null) {
            addRoot(dir);
        }
    }

    private void removeSelectedRoot() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return;
        }
        Set<File> toRemove = new LinkedHashSet<>();
        for (TreePath path : paths) {
            File root = rootOf(path);
            if (root != null) {
                toRemove.add(root.getAbsoluteFile());
            }
        }
        if (toRemove.isEmpty()) {
            return;
        }
        List<DefaultMutableTreeNode> drop = new ArrayList<>();
        for (int i = 0; i < hiddenRoot.getChildCount(); i++) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) hiddenRoot.getChildAt(i);
            if (n.getUserObject() instanceof File
                && toRemove.contains(((File) n.getUserObject()).getAbsoluteFile())) {
                drop.add(n);
            }
        }
        for (DefaultMutableTreeNode n : drop) {
            hiddenRoot.remove(n);
        }
        roots.removeIf(r -> toRemove.contains(r.getAbsoluteFile()));
        treeModel.reload(hiddenRoot);
        currentDir = roots.isEmpty() ? null : roots.get(0);
        currentFiles = listSacFiles(currentDir);
        if (directoryListener != null && currentDir != null) {
            directoryListener.accept(currentDir);
        }
        if (fileSelectionListener != null) {
            fileSelectionListener.accept(List.of());
        }
    }

    private File rootOf(TreePath path) {
        if (path == null || path.getPathCount() < 2) {
            return null;
        }
        Object o = ((DefaultMutableTreeNode) path.getPathComponent(1)).getUserObject();
        return o instanceof File ? (File) o : null;
    }

    private void selectRoot(File dir) {
        for (int i = 0; i < hiddenRoot.getChildCount(); i++) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) hiddenRoot.getChildAt(i);
            if (n.getUserObject() instanceof File
                && ((File) n.getUserObject()).getAbsoluteFile().equals(dir.getAbsoluteFile())) {
                TreePath path = new TreePath(new Object[] { hiddenRoot, n });
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    private void onSelectionChanged() {
        File lead = leadDirectory();
        if (lead != null) {
            currentDir = lead;
            currentFiles = listSacFiles(lead);
            if (directoryListener != null) {
                directoryListener.accept(lead);
            }
        }
        List<File> plot = collectPlotFiles();
        if (fileSelectionListener != null && !plot.isEmpty()) {
            fileSelectionListener.accept(plot);
        }
    }

    private File leadDirectory() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return currentDir;
        }
        Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (o instanceof File) {
            File f = (File) o;
            return f.isDirectory() ? f : f.getParentFile();
        }
        return currentDir;
    }

    private List<File> collectPlotFiles() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return List.of();
        }
        Set<File> files = new LinkedHashSet<>();
        List<File> dirs = new ArrayList<>();
        for (TreePath path : paths) {
            Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (!(o instanceof File)) {
                continue;
            }
            File f = (File) o;
            if (isSacFile(f)) {
                files.add(f.getAbsoluteFile());
            } else if (f.isDirectory()) {
                dirs.add(f);
            }
        }
        if (files.isEmpty() && dirs.size() < 2) {
            return List.of();
        }
        if (dirs.size() >= 2 || !files.isEmpty() && !dirs.isEmpty()) {
            for (File dir : dirs) {
                Collections.addAll(files, listSacFiles(dir));
            }
        }
        List<File> out = new ArrayList<>(files);
        out.sort((a, b) -> a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));
        return out;
    }

    static File[] listSacFiles(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return new File[0];
        }
        File[] sac = dir.listFiles((d, name) -> isSacFileName(name));
        if (sac == null) {
            return new File[0];
        }
        Arrays.sort(sac, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return sac;
    }

    private static boolean hasPlaceholderChild(DefaultMutableTreeNode node) {
        return node.getChildCount() == 1
            && node.getChildAt(0) instanceof DefaultMutableTreeNode
            && LOADING.equals(((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject());
    }

    private boolean isUnderARoot(File f) {
        if (f == null) {
            return false;
        }
        try {
            Path p = f.toPath().toAbsolutePath().normalize();
            for (File root : roots) {
                if (p.startsWith(root.toPath().toAbsolutePath().normalize())) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Path check failed for " + f + ": " + e.getMessage());
        }
        return false;
    }

    private boolean isVisibleDirectory(File f) {
        return f.isDirectory() && !f.isHidden() && f.canRead() && isUnderARoot(f);
    }

    private boolean hasExpandableContent(File dir) {
        if (dir == null || !dir.isDirectory() || !dir.canRead()) {
            return false;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return false;
        }
        for (File c : children) {
            if (isVisibleDirectory(c) || isSacFile(c)) {
                return true;
            }
        }
        return false;
    }

    private void loadChildren(DefaultMutableTreeNode node, File dir) {
        if (dir == null || !dir.isDirectory() || !dir.canRead() || !isUnderARoot(dir)) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File c : children) {
            if (isVisibleDirectory(c)) {
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(c);
                if (hasExpandableContent(c)) {
                    child.add(new DefaultMutableTreeNode(LOADING));
                }
                node.add(child);
            } else if (isSacFile(c)) {
                node.add(new DefaultMutableTreeNode(c));
            }
        }
    }

    private static JButton compactMark(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setFont(UiFonts.uiBold(14f));
        b.setToolTipText(tooltip);
        Dimension d = new Dimension(28, 28);
        b.setPreferredSize(d);
        b.setMinimumSize(d);
        b.setMaximumSize(d);
        b.setMargin(new java.awt.Insets(0, 0, 0, 0));
        AppPanelStyle.stylePrimaryButton(b);
        return b;
    }

    private final class BadgeRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, hasFocus);
            Object o = ((DefaultMutableTreeNode) value).getUserObject();
            if (o instanceof File) {
                File f = (File) o;
                String text = f.isDirectory() && rootsContain(f) ? f.getAbsolutePath() : f.getName();
                String badge = badges.get(f.toPath().toAbsolutePath().normalize());
                setText(badge != null ? text + "  " + badge : text);
            }
            return this;
        }
    }

    private boolean rootsContain(File f) {
        File abs = f.getAbsoluteFile();
        for (File r : roots) {
            if (r.getAbsoluteFile().equals(abs)) {
                return true;
            }
        }
        return false;
    }
}
