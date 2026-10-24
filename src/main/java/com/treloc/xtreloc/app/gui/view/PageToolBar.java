package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;

/** The tool bar under the menu: one set of buttons per page, with the frequent commands only. */
public class PageToolBar extends JPanel {

    private final CardLayout cards = new CardLayout();
    private final JPanel host = new JPanel(cards);
    private final Map<String, JToolBar> bars = new HashMap<>();
    private final JToolBar globalBar = new JToolBar();
    private String page;

    public PageToolBar() {
        super(new BorderLayout());
        AppPanelStyle.setPanelBackground(this);
        add(host, BorderLayout.CENTER);
        globalBar.setFloatable(false);
        globalBar.setRollover(true);
        add(globalBar, BorderLayout.EAST);
    }

    /** Adds a button that stays visible on every page (right end of the tool bar). */
    public JButton addGlobalButton(Action action) {
        JButton b = new JButton(action);
        b.setFocusable(false);
        b.setHideActionText(false);
        b.setMargin(new java.awt.Insets(2, 6, 2, 6));
        globalBar.add(b);
        return b;
    }

    /** The tool bar of {@code page}, created on first use. */
    public JToolBar bar(String pageName) {
        return bars.computeIfAbsent(pageName, n -> {
            JToolBar b = new JToolBar();
            b.setFloatable(false);
            b.setRollover(true);
            host.add(b, n);
            return b;
        });
    }

    /** Adds a button for {@code action} (icon and label) to the bar of {@code pageName}. */
    public JButton addButton(String pageName, Action action) {
        JButton b = new JButton(action);
        b.setFocusable(false);
        b.setHideActionText(false);
        b.setMargin(new java.awt.Insets(2, 6, 2, 6));
        bar(pageName).add(b);
        return b;
    }

    public void addSeparator(String pageName) {
        bar(pageName).addSeparator(new Dimension(8, 0));
    }

    public void addLabeledCombo(String pageName, String label, JComboBox<?> combo) {
        JToolBar b = bar(pageName);
        b.add(new JLabel(" " + label + " "));
        combo.setMaximumSize(combo.getPreferredSize());
        b.add(combo);
    }

    public void showPage(String pageName) {
        page = pageName;
        if (bars.containsKey(pageName)) {
            cards.show(host, pageName);
        }
    }

    String pageForTest() {
        return page;
    }

    JToolBar globalBarForTest() {
        return globalBar;
    }

}
