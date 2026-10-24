package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.UiFonts;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The status bar at the bottom of the main window: message, page, linked event and pick counts. */
public class MainStatusBar extends JPanel {

    private final JLabel message = new JLabel("Ready");
    private final JLabel page = cell();
    private final JLabel event = cell();
    private final JLabel picks = cell();

    public MainStatusBar() {
        super(new BorderLayout());
        AppPanelStyle.setPanelBackground(this);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppPanelStyle.getBorderColor()));
        message.setFont(UiFonts.uiPlain(11f));
        message.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(message, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        AppPanelStyle.setPanelBackground(right);
        right.add(event);
        right.add(picks);
        right.add(page);
        add(right, BorderLayout.EAST);
    }

    private static JLabel cell() {
        JLabel l = new JLabel(" ");
        l.setFont(UiFonts.uiPlain(11f));
        l.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(128, 128, 128, 110)),
            BorderFactory.createEmptyBorder(2, 10, 2, 10)));
        return l;
    }

    public void setMessage(String text) {
        message.setText(text == null || text.isBlank() ? "Ready" : text);
    }

    public void setPage(String name) {
        page.setText(name);
    }

    /** {@code null}: no event linked. */
    public void setEvent(String text) {
        event.setText(text == null ? "No event" : text);
    }

    public void setPicks(int p, int s) {
        picks.setText("Picks: P " + p + " / S " + s);
    }

    String messageForTest() {
        return message.getText();
    }

    String picksForTest() {
        return picks.getText();
    }
}
