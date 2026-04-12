package com.treloc.xtreloc.app.gui.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Panel for displaying travel time difference data in table format
 */
public class TravelTimeDataPanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel titleLabel;
    
    public TravelTimeDataPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Travel Time Difference Data"));
        
        titleLabel = new JLabel("Click file path column to display travel time difference data");
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel, BorderLayout.NORTH);
        
        String[] columnNames = {"Station 1", "Station 2", "Travel Time Diff (sec)", "Weight"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFillsViewportHeight(true);
        
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
    }
    
    /**
     * Sets travel time difference data
     */
    public void setData(double[][] lagTable, String[] codeStrings) {
        tableModel.setRowCount(0);
        
        if (lagTable == null || codeStrings == null) {
            titleLabel.setText("No data available");
            return;
        }
        for (double[] lag : lagTable) {
            int idx1 = (int) lag[0];
            int idx2 = (int) lag[1];
            double lagTime = lag[2];
            double weight = lag.length > 3 ? lag[3] : 1.0;
            
            String code1 = idx1 < codeStrings.length ? codeStrings[idx1] : "?";
            String code2 = idx2 < codeStrings.length ? codeStrings[idx2] : "?";
            
            tableModel.addRow(new Object[]{
                code1,
                code2,
                String.format("%.3f", lagTime),
                String.format("%.3f", weight)
            });
        }
        
        titleLabel.setText("Travel Time Difference Data: " + lagTable.length + " pairs");
    }
    
    /**
     * Clears data
     */
    public void clearData() {
        tableModel.setRowCount(0);
        titleLabel.setText("Click file path column to display travel time difference data");
    }
}

