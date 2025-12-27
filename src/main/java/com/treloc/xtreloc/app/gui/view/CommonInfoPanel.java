package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.SharedFileManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * 共通情報（観測点ファイル、速度構造）を表示するパネル
 */
public class CommonInfoPanel extends JPanel {
    private JLabel stationFileLabel;
    private JLabel taupFileLabel;
    
    public CommonInfoPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setBorder(BorderFactory.createTitledBorder("共通設定"));
        
        // 観測点ファイル表示
        stationFileLabel = new JLabel("観測点ファイル: 未選択");
        stationFileLabel.setForeground(Color.GRAY);
        add(stationFileLabel);
        
        add(Box.createHorizontalStrut(20));
        
        // 速度構造表示
        taupFileLabel = new JLabel("速度構造: 未設定");
        taupFileLabel.setForeground(Color.GRAY);
        add(taupFileLabel);
        
        // 共有ファイルマネージャーのリスナーを登録
        SharedFileManager.getInstance().addStationFileListener(file -> {
            if (file != null) {
                stationFileLabel.setText("観測点ファイル: " + file.getName());
                stationFileLabel.setForeground(Color.BLACK);
            } else {
                stationFileLabel.setText("観測点ファイル: 未選択");
                stationFileLabel.setForeground(Color.GRAY);
            }
        });
        
        // 既に設定されている観測点ファイルがあれば表示
        File existingFile = SharedFileManager.getInstance().getStationFile();
        if (existingFile != null && existingFile.exists()) {
            stationFileLabel.setText("観測点ファイル: " + existingFile.getName());
            stationFileLabel.setForeground(Color.BLACK);
        }
        
        // 速度構造ファイルのリスナーを登録
        SharedFileManager.getInstance().addTaupFileListener(file -> {
            if (file != null && !file.isEmpty()) {
                taupFileLabel.setText("速度構造: " + file);
                taupFileLabel.setForeground(Color.BLACK);
            } else {
                taupFileLabel.setText("速度構造: 未設定");
                taupFileLabel.setForeground(Color.GRAY);
            }
        });
        
        // 既に設定されている速度構造ファイルがあれば表示
        String existingTaupFile = SharedFileManager.getInstance().getTaupFile();
        if (existingTaupFile != null && !existingTaupFile.isEmpty()) {
            taupFileLabel.setText("速度構造: " + existingTaupFile);
            taupFileLabel.setForeground(Color.BLACK);
        }
    }
    
    public void setTaupFile(String taupFile) {
        if (taupFile != null && !taupFile.isEmpty()) {
            taupFileLabel.setText("速度構造: " + taupFile);
            taupFileLabel.setForeground(Color.BLACK);
            // 共有ファイルマネージャーにも設定
            SharedFileManager.getInstance().setTaupFile(taupFile);
        } else {
            taupFileLabel.setText("速度構造: 未設定");
            taupFileLabel.setForeground(Color.GRAY);
        }
    }
}

