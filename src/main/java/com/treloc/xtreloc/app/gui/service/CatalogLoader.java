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
                if (isCsv && isFirstLine) {
                    isFirstLine = false;
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains("time") || lowerLine.contains("latitude")) {
                        continue;
                    }
                }
                
                String[] p;
                if (isCsv) {
                    p = line.trim().split(",");
                } else {
                    p = line.trim().split("\\s+");
                }
                
                if (p.length >= 4) {
                    if (isCsv && p.length >= 8) {
                        // CSV format with error information included
                        double xerr = p.length > 4 ? Double.parseDouble(p[4].trim()) : 0.0;
                        double yerr = p.length > 5 ? Double.parseDouble(p[5].trim()) : 0.0;
                        double zerr = p.length > 6 ? Double.parseDouble(p[6].trim()) : 0.0;
                        double rms = p.length > 7 ? Double.parseDouble(p[7].trim()) : 0.0;
                        String datFilePath = p.length > 8 ? p[8].trim() : null;
                        String type = p.length > 9 ? p[9].trim() : null;
                        Integer clusterId = null;
                        if (p.length > 10 && !p[10].trim().isEmpty()) {
                            try {
                                clusterId = Integer.parseInt(p[10].trim());
                            } catch (NumberFormatException e) {
                            }
                        }
                        list.add(new Hypocenter(
                                p[0].trim(),
                                Double.parseDouble(p[1].trim()),
                                Double.parseDouble(p[2].trim()),
                                Double.parseDouble(p[3].trim()),
                                xerr, yerr, zerr, rms, clusterId, datFilePath, type));
                    } else {
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
