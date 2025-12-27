package com.treloc.xtreloc.app.gui.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 走時差データをExcel形式で表示するパネル
 */
public class TravelTimeDataPanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel titleLabel;
    
    public TravelTimeDataPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("走時差データ"));
        
        // タイトルラベル
        titleLabel = new JLabel("ファイルパス列をクリックして走時差データを表示");
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel, BorderLayout.NORTH);
        
        // テーブルモデルの作成
        String[] columnNames = {"観測点1", "観測点2", "走時差 (秒)", "重み"};
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
     * 走時差データを設定
     */
    public void setData(double[][] lagTable, String[] codeStrings) {
        tableModel.setRowCount(0);
        
        if (lagTable == null || codeStrings == null) {
            titleLabel.setText("データがありません");
            return;
        }
        
        // データを追加
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
        
        titleLabel.setText("走時差データ: " + lagTable.length + " ペア");
    }
    
    /**
     * データをクリア
     */
    public void clearData() {
        tableModel.setRowCount(0);
        titleLabel.setText("ファイルパス列をクリックして走時差データを表示");
    }
}

