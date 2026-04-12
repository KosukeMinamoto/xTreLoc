package com.treloc.xtreloc.app.gui.service;

import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.util.TimeFormatConverter;
import com.treloc.xtreloc.util.ModeNameMapper;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Exports hypocenter lists to CSV for the GUI (Execution Log result catalog, Report panel).
 * Column order: time, latitude, longitude, depth, xerr, yerr, zerr, rms, file, mode, cid.
 */
public class CsvExporter {

    /**
     * Writes hypocenters to a CSV file with header.
     *
     * @param hypocenters list to export (may be empty)
     * @param outputFile destination file (overwritten)
     * @throws IOException if the file cannot be written
     */
    public static void exportHypocenters(List<Hypocenter> hypocenters, java.io.File outputFile) 
            throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid\n");
            
            for (Hypocenter h : hypocenters) {
                String timeISO8601 = TimeFormatConverter.toISO8601(h.time);
                String modeAbbrev = "";
                if (h.type != null && !h.type.isEmpty()) {
                    modeAbbrev = ModeNameMapper.normalizeToAbbreviation(h.type);
                }
                
                writer.write(String.format("%s,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%s,%s\n",
                    timeISO8601, h.lat, h.lon, h.depth, h.xerr, h.yerr, h.zerr, h.rms,
                    h.datFilePath != null ? h.datFilePath : "",
                    modeAbbrev,
                    h.clusterId != null ? String.valueOf(h.clusterId) : ""));
            }
        }
    }
}

