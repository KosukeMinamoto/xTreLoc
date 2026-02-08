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
                    // CSV format: comma-separated
                    p = line.trim().split(",");
                } else {
                    // DAT形式またはLIST形式: 空白区切り
                    p = line.trim().split("\\s+");
                }
                
                if (p.length >= 4) {
                    // CSV形式の場合: time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid
                    // DAT format: time, latitude, longitude, depth order (error information is on separate line)
                    if (isCsv && p.length >= 8) {
                        // CSV format with error information included
                        double xerr = p.length > 4 ? Double.parseDouble(p[4].trim()) : 0.0;
                        double yerr = p.length > 5 ? Double.parseDouble(p[5].trim()) : 0.0;
                        double zerr = p.length > 6 ? Double.parseDouble(p[6].trim()) : 0.0;
                        double rms = p.length > 7 ? Double.parseDouble(p[7].trim()) : 0.0;
                        // Column order: time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid
                        String datFilePath = p.length > 8 ? p[8].trim() : null;
                        String type = p.length > 9 ? p[9].trim() : null;
                        // Load cid column (10th column, index 10)
                        Integer clusterId = null;
                        if (p.length > 10 && !p[10].trim().isEmpty()) {
                            try {
                                clusterId = Integer.parseInt(p[10].trim());
                            } catch (NumberFormatException e) {
                                // Keep null if cid is not numeric
                            }
                        }
                        list.add(new Hypocenter(
                                p[0].trim(),
                                Double.parseDouble(p[1].trim()),
                                Double.parseDouble(p[2].trim()),
                                Double.parseDouble(p[3].trim()),
                                xerr, yerr, zerr, rms, clusterId, datFilePath, type));
                    } else {
                        // DAT format or CSV format without error information
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
