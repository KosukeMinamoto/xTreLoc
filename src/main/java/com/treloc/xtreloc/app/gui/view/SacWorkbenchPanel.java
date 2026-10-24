package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/**
 * Left two columns of the Seismic workbench: directory tree ({@code +}/{@code −}) and
 * the Absolute / Correlation settings. Waveforms stay on the right pane of the main window.
 */
public class SacWorkbenchPanel extends JPanel {

    private final PickViewerPanel pickViewer;

    public SacWorkbenchPanel(PickViewerPanel pick, TraceProcessingPanel crossCorr) {
        this(pick, crossCorr, true);
    }

    /**
     * @param embedBrowser {@code true}: the directory tree is the left column of this panel;
     *                     {@code false}: the caller puts {@link PickViewerPanel#getBrowserPanel()} elsewhere
     *                     (the main window's navigator)
     */
    public SacWorkbenchPanel(PickViewerPanel pick, TraceProcessingPanel crossCorr, boolean embedBrowser) {
        this.pickViewer = pick;
        setLayout(new BorderLayout());
        AppPanelStyle.setPanelBackground(this);
        pick.embedCrossCorr(crossCorr);

        if (embedBrowser) {
            JSplitPane cols = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                pick.getBrowserPanel(), pick.getSettingsPanel());
            cols.setResizeWeight(0.0);
            cols.setDividerLocation(240);
            cols.setOneTouchExpandable(true);
            cols.setContinuousLayout(true);
            cols.setBackground(AppPanelStyle.getPanelBg());
            add(cols, BorderLayout.CENTER);
        } else {
            add(pick.getSettingsPanel(), BorderLayout.CENTER);
        }

        pick.setInputRootListener(dir -> {
            if (dir != null) {
                crossCorr.setInputRoot(dir);
            }
        });
        crossCorr.setInputRootListener(dir -> {
            if (dir != null) {
                pick.setInputRoot(dir);
            }
        });
        crossCorr.setDirectoryPreviewListener(pick::previewDirectory);
        AppPanelStyle.applyThemeTo(this);
    }

    List<String> leftSectionTitles() {
        return pickViewer.leftSectionTitles();
    }

    boolean hasModeTabs() {
        return false;
    }

    boolean hasSettingsTabs() {
        return true;
    }
}
