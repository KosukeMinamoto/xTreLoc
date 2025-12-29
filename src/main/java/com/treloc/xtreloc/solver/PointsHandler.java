package com.treloc.xtreloc.solver;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class PointsHandler {
    private static final Logger logger = Logger.getLogger(PointsHandler.class.getName());
    private Point mainPoint;
    private Map<String, Integer> codeIndexMap;

    public PointsHandler() {
        this.mainPoint = null;
        this.codeIndexMap = new HashMap<>();
    }

    public void readDatFile(String datFile, String[] codeStrings, double threshold) throws IOException {
        codeIndexMap.clear();
        for (int i = 0; i < codeStrings.length; i++) {
            codeIndexMap.put(codeStrings[i], i);
        }

        try (BufferedReader br = new BufferedReader(new FileReader(datFile))) {
            String line1 = br.readLine();
            if (line1 == null) {
                throw new IOException("Empty file: " + datFile);
            }
            String[] parts1 = line1.trim().split("\\s+");
            double lat = Double.parseDouble(parts1[0]);
            double lon = Double.parseDouble(parts1[1]);
            double dep = Double.parseDouble(parts1[2]);
            String type = parts1.length > 3 ? parts1[3] : "";

            String line2 = br.readLine();
            double elat = 0.0;
            double elon = 0.0;
            double edep = 0.0;
            double res = 0.0;
            boolean hasErrorLine = false;
            
            if (line2 != null && !line2.trim().isEmpty()) {
                String[] parts2 = line2.trim().split("\\s+");
                try {
                    Double.parseDouble(parts2[0]);
                    if (parts2.length >= 4) {
                        elat = Double.parseDouble(parts2[0]);
                        elon = Double.parseDouble(parts2[1]);
                        edep = Double.parseDouble(parts2[2]);
                        res = Double.parseDouble(parts2[3]);
                        hasErrorLine = true;
                    }
                } catch (NumberFormatException e) {
                    hasErrorLine = false;
                }
            }

            List<double[]> lagList = new ArrayList<>();
            List<Integer> usedIdxList = new ArrayList<>();
            
            String line;
            if (hasErrorLine) {
                line = br.readLine();
            } else {
                line = line2;
            }
            
            while (line != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3) continue;

                String code1 = parts[0];
                String code2 = parts[1];
                double lagTime = Double.parseDouble(parts[2]);
                double weight = parts.length > 3 ? Double.parseDouble(parts[3]) : 1.0;

                Integer idx1 = codeIndexMap.get(code1);
                Integer idx2 = codeIndexMap.get(code2);

                if (idx1 != null && idx2 != null) {
                    if (threshold <= 0.0 || weight >= threshold) {
                        lagList.add(new double[]{idx1, idx2, lagTime, weight});
                        if (!usedIdxList.contains(idx1)) usedIdxList.add(idx1);
                        if (!usedIdxList.contains(idx2)) usedIdxList.add(idx2);
                    }
                }
                
                line = br.readLine();
            }
            double[][] lagTable = lagList.toArray(new double[lagList.size()][]);
            int[] usedIdx = usedIdxList.stream().mapToInt(i -> i).toArray();

            mainPoint = new Point("", lat, lon, dep, elat, elon, edep, res, datFile, type, -1);
            mainPoint.setLagTable(lagTable);
            mainPoint.setUsedIdx(usedIdx);
        }
    }

    public Point getMainPoint() {
        return mainPoint;
    }

    public void setMainPoint(Point point) {
        this.mainPoint = point;
    }

    public void writeDatFile(String outFile, String[] codeStrings) throws IOException {
        if (mainPoint == null) {
            throw new IllegalStateException("No point data to write");
        }

        java.io.File outputFile = new java.io.File(outFile);
        java.io.File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            throw new IOException(
                String.format("Output directory does not exist: %s\n" +
                    "  Please create the directory before running the task.",
                    parentDir.getAbsolutePath()));
        }
        if (parentDir != null && !parentDir.isDirectory()) {
            throw new IOException(
                String.format("Output path is not a directory: %s\n" +
                    "  Please check the output directory path.",
                    parentDir.getAbsolutePath()));
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
            writer.printf("%.3f %.3f %.3f %s%n",
                mainPoint.getLat(), mainPoint.getLon(), mainPoint.getDep(), mainPoint.getType());

            writer.printf("%.3f %.3f %.3f %.3f%n",
                mainPoint.getElat(), mainPoint.getElon(), mainPoint.getEdep(), mainPoint.getRes());

            double[][] lagTable = mainPoint.getLagTable();
            if (lagTable != null) {
                for (int i = 0; i < lagTable.length; i++) {
                    double[] lag = lagTable[i];
                    int idx1 = (int) lag[0];
                    int idx2 = (int) lag[1];
                    double lagTime = lag[2];
                    double weight = lag.length > 3 ? lag[3] : 1.0;

                    if (idx1 < codeStrings.length && idx2 < codeStrings.length) {
                        writer.printf("%s %s %.3f %.3f%n",
                            codeStrings[idx1], codeStrings[idx2], lagTime, weight);
                    }
                }
            }
        }
    }
}

