package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.volume3d.Map3DIcons;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;

/**
 * Right-hand icon strip and pop-up cards, matching the 2-D / 3-D map chrome.
 * Items may open a card, stay latched as a mode, or fire once.
 */
public final class ViewerSideChrome {

    public static final int STRIP_WIDTH = 46;
    private static final int CARD_WIDTH = 240;
    private static final int CARD_HEADER = 36;

    public enum Style {
        CARD,
        TOGGLE,
        ACTION
    }

    private ViewerSideChrome() {
    }

    public static Host wrap(JComponent content, Item... items) {
        Host root = new Host();
        AppPanelStyle.setPanelBackground(root);
        JLayeredPane area = new JLayeredPane();
        JPanel strip = new JPanel();
        JPanel cardHost = new JPanel(new BorderLayout());
        JLabel cardTitle = new JLabel(" ");
        JScrollPane cardScroll = new JScrollPane();
        Map<String, JComponent> cards = new LinkedHashMap<>();
        final String[] open = {null};
        Runnable layoutNow = () -> layout(area, content, strip, cardHost, cardScroll, open[0] != null);

        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setBackground(AppPanelStyle.getPanelBg());
        strip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, AppPanelStyle.getBorderColor()));
        strip.add(Box.createVerticalStrut(6));
        for (Item it : items) {
            if (it.body != null) {
                cards.put(it.name, it.body);
            }
            JToggleButton b = new JToggleButton(new Map3DIcons(it.kind, 24, AppPanelStyle.getContentTextColor()));
            b.setToolTipText(it.tip);
            b.setFocusPainted(false);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            Dimension d = new Dimension(STRIP_WIDTH - 6, 38);
            b.setPreferredSize(d);
            b.setMaximumSize(d);
            b.addActionListener(e -> {
                if (it.style == Style.ACTION) {
                    b.setSelected(false);
                    if (it.action != null) {
                        it.action.run();
                    }
                    return;
                }
                if (it.style == Style.TOGGLE) {
                    if (it.toggle != null) {
                        it.toggle.accept(b.isSelected());
                    }
                    return;
                }
                if (!b.isSelected() || it.name.equals(open[0])) {
                    open[0] = null;
                    cardHost.setVisible(false);
                } else {
                    open[0] = it.name;
                    cardTitle.setText(it.name);
                    cardScroll.setViewportView(cards.get(it.name));
                    cardHost.setVisible(true);
                }
                for (Map.Entry<String, JToggleButton> e2 : root.buttons.entrySet()) {
                    Item other = root.items.get(e2.getKey());
                    if (other != null && other.style == Style.CARD) {
                        e2.getValue().setSelected(e2.getKey().equals(open[0]));
                    }
                }
                layoutNow.run();
                area.revalidate();
                area.repaint();
            });
            root.buttons.put(it.name, b);
            root.items.put(it.name, it);
            strip.add(b);
            strip.add(Box.createVerticalStrut(4));
        }
        strip.add(Box.createVerticalGlue());

        cardTitle.setFont(AppPanelStyle.getSectionTitleFont());
        cardTitle.setForeground(AppPanelStyle.getTitleColor());
        cardTitle.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        JButton close = new JButton("\u00D7");
        close.setToolTipText("Close");
        close.setFocusPainted(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.addActionListener(e -> {
            open[0] = null;
            cardHost.setVisible(false);
            for (Map.Entry<String, JToggleButton> e2 : root.buttons.entrySet()) {
                Item other = root.items.get(e2.getKey());
                if (other != null && other.style == Style.CARD) {
                    e2.getValue().setSelected(false);
                }
            }
            layoutNow.run();
            area.revalidate();
            area.repaint();
        });
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppPanelStyle.getPanelBg());
        header.add(cardTitle, BorderLayout.CENTER);
        header.add(close, BorderLayout.EAST);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppPanelStyle.getBorderColor()));
        cardScroll.setBorder(null);
        cardScroll.getViewport().setBackground(AppPanelStyle.getPanelBg());
        cardHost.setBackground(AppPanelStyle.getPanelBg());
        cardHost.setBorder(BorderFactory.createLineBorder(AppPanelStyle.getBorderColor()));
        cardHost.add(header, BorderLayout.NORTH);
        cardHost.add(cardScroll, BorderLayout.CENTER);
        cardHost.setVisible(false);

        area.add(content, JLayeredPane.DEFAULT_LAYER);
        area.add(strip, JLayeredPane.PALETTE_LAYER);
        area.add(cardHost, JLayeredPane.MODAL_LAYER);
        area.addPropertyChangeListener("ancestor", ev -> layoutNow.run());
        area.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutNow.run();
            }
        });

        root.add(area, BorderLayout.CENTER);
        return root;
    }

    private static void layout(JLayeredPane area, JComponent content, JPanel strip, JPanel cardHost,
            JScrollPane cardScroll, boolean cardOpen) {
        int w = area.getWidth();
        int h = area.getHeight();
        content.setBounds(0, 0, Math.max(0, w - STRIP_WIDTH), h);
        strip.setBounds(Math.max(0, w - STRIP_WIDTH), 0, STRIP_WIDTH, h);
        if (!cardOpen) {
            return;
        }
        Component body = cardScroll.getViewport().getView();
        Dimension pref = body != null ? body.getPreferredSize() : new Dimension(CARD_WIDTH, 80);
        int maxW = Math.max(120, w - STRIP_WIDTH - 16);
        int width = Math.min(Math.max(CARD_WIDTH, pref.width + 24), maxW);
        int height = Math.min(Math.max(CARD_HEADER + pref.height + 8, 80), Math.max(80, h - 16));
        int x = Math.max(6, w - STRIP_WIDTH - width - 6);
        cardHost.setBounds(x, 8, width, height);
    }

    public static final class Host extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Map<String, JToggleButton> buttons = new LinkedHashMap<>();
        private final Map<String, Item> items = new LinkedHashMap<>();

        Host() {
            setLayout(new BorderLayout());
        }

        public void setToggle(String name, boolean on) {
            JToggleButton b = buttons.get(name);
            if (b != null) {
                b.setSelected(on);
            }
        }

        public boolean isToggleOn(String name) {
            JToggleButton b = buttons.get(name);
            return b != null && b.isSelected();
        }
    }

    public static final class Item {
        final String name;
        final Map3DIcons.Kind kind;
        final String tip;
        final JComponent body;
        final Style style;
        final Consumer<Boolean> toggle;
        final Runnable action;

        public Item(String name, Map3DIcons.Kind kind, String tip, JComponent body) {
            this(name, kind, tip, body, Style.CARD, null, null);
        }

        private Item(String name, Map3DIcons.Kind kind, String tip, JComponent body,
                     Style style, Consumer<Boolean> toggle, Runnable action) {
            this.name = name;
            this.kind = kind;
            this.tip = tip;
            this.body = body;
            this.style = style;
            this.toggle = toggle;
            this.action = action;
        }

        public static Item toggle(String name, Map3DIcons.Kind kind, String tip, Consumer<Boolean> onChange) {
            return new Item(name, kind, tip, null, Style.TOGGLE, onChange, null);
        }

        public static Item action(String name, Map3DIcons.Kind kind, String tip, Runnable run) {
            return new Item(name, kind, tip, null, Style.ACTION, null, run);
        }
    }
}
