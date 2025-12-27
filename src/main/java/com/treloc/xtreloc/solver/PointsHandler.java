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

/**
 * datファイルの読み書きを行うクラス
 */
public class PointsHandler {
    private static final Logger logger = Logger.getLogger(PointsHandler.class.getName());
    private Point mainPoint;
    private Map<String, Integer> codeIndexMap;

    public PointsHandler() {
        this.mainPoint = null;
        this.codeIndexMap = new HashMap<>();
    }

    /**
     * datファイルを読み込む
     * @param datFile datファイルのパス
     * @param codeStrings 観測点コードの配列
     * @param threshold 閾値
     */
    public void readDatFile(String datFile, String[] codeStrings, double threshold) throws IOException {
        codeIndexMap.clear();
        for (int i = 0; i < codeStrings.length; i++) {
            codeIndexMap.put(codeStrings[i], i);
        }

        try (BufferedReader br = new BufferedReader(new FileReader(datFile))) {
            // 1行目: 緯度 経度 深度 タイプ
            String line1 = br.readLine();
            if (line1 == null) {
                throw new IOException("Empty file: " + datFile);
            }
            String[] parts1 = line1.trim().split("\\s+");
            double lat = Double.parseDouble(parts1[0]);
            double lon = Double.parseDouble(parts1[1]);
            double dep = Double.parseDouble(parts1[2]);
            String type = parts1.length > 3 ? parts1[3] : "";

            // 2行目: エラー情報（elat, elon, edep, res）または観測点ペア
            String line2 = br.readLine();
            double elat = 0.0;
            double elon = 0.0;
            double edep = 0.0;
            double res = 0.0;
            boolean hasErrorLine = false;
            
            if (line2 != null && !line2.trim().isEmpty()) {
                String[] parts2 = line2.trim().split("\\s+");
                // 2行目が数値のみ（エラー情報）か、観測点コードを含むかで判定
                try {
                    // 最初の要素が数値かどうかで判定
                    Double.parseDouble(parts2[0]);
                    // 数値のみの場合（エラー情報行）
                    if (parts2.length >= 4) {
                        elat = Double.parseDouble(parts2[0]);
                        elon = Double.parseDouble(parts2[1]);
                        edep = Double.parseDouble(parts2[2]);
                        res = Double.parseDouble(parts2[3]);
                        hasErrorLine = true;
                    }
                } catch (NumberFormatException e) {
                    // 2行目が観測点ペアの場合（エラー情報行がない形式）
                    // line2は後で観測点ペアとして処理する
                    hasErrorLine = false;
                }
            }

            // 観測点ペアの情報を読み込む
            List<double[]> lagList = new ArrayList<>();
            List<Integer> usedIdxList = new ArrayList<>();
            
            // エラー情報行がない場合、line2から処理開始
            String line;
            if (hasErrorLine) {
                // エラー情報行がある場合、次の行から読み始める
                line = br.readLine();
            } else {
                // エラー情報行がない場合、line2を観測点ペアとして処理
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
                    // thresholdが0以下の場合はすべてのデータを使用
                    // thresholdはweight（重み）と比較される（マニュアルに記載通り）
                    // weight >= threshold の場合にデータを使用
                    if (threshold <= 0.0 || weight >= threshold) {
                        lagList.add(new double[]{idx1, idx2, lagTime, weight});
                        if (!usedIdxList.contains(idx1)) usedIdxList.add(idx1);
                        if (!usedIdxList.contains(idx2)) usedIdxList.add(idx2);
                    }
                }
                
                // 次の行を読み込む
                line = br.readLine();
            }

            // Pointオブジェクトを作成
            double[][] lagTable = lagList.toArray(new double[lagList.size()][]);
            int[] usedIdx = usedIdxList.stream().mapToInt(i -> i).toArray();

            mainPoint = new Point("", lat, lon, dep, elat, elon, edep, res, datFile, type, -1);
            mainPoint.setLagTable(lagTable);
            mainPoint.setUsedIdx(usedIdx);
        }
    }

    /**
     * メインのPointを取得
     */
    public Point getMainPoint() {
        return mainPoint;
    }

    /**
     * メインのPointを設定
     */
    public void setMainPoint(Point point) {
        this.mainPoint = point;
    }

    /**
     * datファイルに書き込む
     * @param outFile 出力ファイルのパス
     * @param codeStrings 観測点コードの配列
     */
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
            // 1行目: 緯度 経度 深度 タイプ
            writer.printf("%.3f %.3f %.3f %s%n",
                mainPoint.getLat(), mainPoint.getLon(), mainPoint.getDep(), mainPoint.getType());

            // 2行目: エラー情報
            writer.printf("%.3f %.3f %.3f %.3f%n",
                mainPoint.getElat(), mainPoint.getElon(), mainPoint.getEdep(), mainPoint.getRes());

            // 3行目以降: 観測点ペアの情報（重みは残差の逆数）
            double[][] lagTable = mainPoint.getLagTable();
            if (lagTable != null) {
                for (int i = 0; i < lagTable.length; i++) {
                    double[] lag = lagTable[i];
                    int idx1 = (int) lag[0];
                    int idx2 = (int) lag[1];
                    double lagTime = lag[2];
                    // 4列目: 残差の逆数の重み（既にlagTableに設定されている）
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

