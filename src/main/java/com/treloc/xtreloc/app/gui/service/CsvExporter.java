package com.treloc.xtreloc.app.gui.service;

import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.util.TimeFormatConverter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * CSV export functionality
 */
public class CsvExporter {
    
    /**
     * Exports hypocenter data to CSV file
     * @param hypocenters list of hypocenter data
     * @param outputFile output file
     * @throws IOException file output error
     */
    public static void exportHypocenters(List<Hypocenter> hypocenters, java.io.File outputFile) 
            throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid\n");
            
            for (Hypocenter h : hypocenters) {
                String timeISO8601 = TimeFormatConverter.toISO8601(h.time);
                
                writer.write(String.format("%s,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%s,%s\n",
                    timeISO8601, h.lat, h.lon, h.depth, h.xerr, h.yerr, h.zerr, h.rms,
                    h.datFilePath != null ? h.datFilePath : "",
                    h.type != null ? h.type : "",
                    h.clusterId != null ? String.valueOf(h.clusterId) : ""));
            }
        }
    }
}

