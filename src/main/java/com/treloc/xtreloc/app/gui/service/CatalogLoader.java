package com.treloc.xtreloc.app.gui.service;

import com.treloc.xtreloc.app.gui.model.Hypocenter;

import java.io.*;
import java.util.*;

public class CatalogLoader {

    public static List<Hypocenter> load(File file) throws IOException {
        List<Hypocenter> list = new ArrayList<>();
        String fileName = file.getName().toLowerCase();
        boolean isCsv = fileName.endsWith(".csv");

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (line.isBlank())
                    continue;
                
                // CSVファイルの場合はヘッダー行をスキップ
                // ヘッダー形式: time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid
                if (isCsv && isFirstLine) {
                    isFirstLine = false;
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains("time") || lowerLine.contains("時刻") || 
                        lowerLine.contains("latitude") || lowerLine.contains("緯度")) {
                        continue; // ヘッダー行をスキップ
                    }
                }
                
                String[] p;
                if (isCsv) {
                    // CSV形式: カンマ区切り
                    p = line.trim().split(",");
                } else {
                    // DAT形式またはLIST形式: 空白区切り
                    p = line.trim().split("\\s+");
                }
                
                if (p.length >= 4) {
                    // CSV形式の場合: time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid
                    // DAT形式の場合: 時刻、緯度、経度、深度の順（エラー情報は別行）
                    if (isCsv && p.length >= 8) {
                        // CSV形式でエラー情報が含まれている場合
                        double xerr = p.length > 4 ? Double.parseDouble(p[4].trim()) : 0.0;
                        double yerr = p.length > 5 ? Double.parseDouble(p[5].trim()) : 0.0;
                        double zerr = p.length > 6 ? Double.parseDouble(p[6].trim()) : 0.0;
                        double rms = p.length > 7 ? Double.parseDouble(p[7].trim()) : 0.0;
                        // カラム順序: time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid
                        String datFilePath = p.length > 8 ? p[8].trim() : null;
                        String type = p.length > 9 ? p[9].trim() : null;
                        // cidカラム（10番目、インデックス10）を読み込む
                        Integer clusterId = null;
                        if (p.length > 10 && !p[10].trim().isEmpty()) {
                            try {
                                clusterId = Integer.parseInt(p[10].trim());
                            } catch (NumberFormatException e) {
                                // cidが数値でない場合はnullのまま
                            }
                        }
                        list.add(new Hypocenter(
                                p[0].trim(),
                                Double.parseDouble(p[1].trim()),
                                Double.parseDouble(p[2].trim()),
                                Double.parseDouble(p[3].trim()),
                                xerr, yerr, zerr, rms, clusterId, datFilePath, type));
                    } else {
                        // DAT形式またはエラー情報がないCSV形式
                        list.add(new Hypocenter(
                                p[0].trim(),
                                Double.parseDouble(p[1].trim()),
                                Double.parseDouble(p[2].trim()),
                                Double.parseDouble(p[3].trim())));
                    }
                }
            }
        }
        return list;
    }
}
