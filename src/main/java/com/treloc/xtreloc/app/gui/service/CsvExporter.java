package com.treloc.xtreloc.app.gui.service;

import com.treloc.xtreloc.app.gui.model.Hypocenter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * CSV出力機能
 */
public class CsvExporter {
    
    /**
     * 震源データをCSVファイルに出力
     * @param hypocenters 震源データのリスト
     * @param outputFile 出力ファイル
     * @throws IOException ファイル出力エラー
     */
    public static void exportHypocenters(List<Hypocenter> hypocenters, java.io.File outputFile) 
            throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            // ヘッダー（catalog_ground_truth.csvと同じ形式: time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid）
            writer.write("time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid\n");
            
            // データ
            for (Hypocenter h : hypocenters) {
                writer.write(String.format("%s,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%s,%s\n",
                    h.time, h.lat, h.lon, h.depth, h.xerr, h.yerr, h.zerr, h.rms,
                    h.datFilePath != null ? h.datFilePath : "",
                    h.type != null ? h.type : "",
                    h.clusterId != null ? String.valueOf(h.clusterId) : ""));
            }
        }
    }
}

