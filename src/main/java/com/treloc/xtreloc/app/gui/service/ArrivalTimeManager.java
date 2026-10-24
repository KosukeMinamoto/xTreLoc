package com.treloc.xtreloc.app.gui.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages arrival times for P and S phases.
 * Handles reading and writing .obs files in the standard format.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class ArrivalTimeManager {
    private static final Logger LOG = Logger.getLogger(ArrivalTimeManager.class.getName());

    private HashMap<String, ArrivalTime> arrivalTimeMap;

    public ArrivalTimeManager() {
        arrivalTimeMap = new HashMap<>();
    }

    private String createKey(String stationName, String phase) {
        return stationName + "_" + phase;
    }

    /**
     * Removes an arrival time for a station and phase.
     * 
     * @param stationName the station name
     * @param component the component
     * @param phase the phase
     */
    public void removeArrivalTime(String stationName, String component, String phase) {
        stationName = stationName.replace(" ", "");
        String key = createKey(stationName, phase);
        if (arrivalTimeMap.containsKey(key)) {
            LOG.fine("Removed: " + stationName + " (" + phase + ") " + arrivalTimeMap.get(key).getArrivalTime());
            arrivalTimeMap.remove(key);
        }
    }

    /**
     * Gets an arrival time for a station and phase.
     * 
     * @param stationName the station name
     * @param phase the phase
     * @return the ArrivalTime object, or null if not found
     */
    public ArrivalTime getArrivalTime(String stationName, String phase) {
        String key = createKey(stationName, phase);
        return arrivalTimeMap.get(key);
    }

    /**
     * Gets the arrival time map.
     * 
     * @return the map of arrival times
     */
    public HashMap<String, ArrivalTime> getArrivalTimeMap() {
        return arrivalTimeMap;
    }

    /**
     * Sets an arrival time (replaces existing if present).
     * 
     * @param stationName the station name
     * @param component the component
     * @param phase the phase
     * @param arrivalTime the arrival time
     */
    public void setArrivalTime(String stationName, String component, String phase, Date arrivalTime) {
        String key = createKey(stationName, phase);
        arrivalTimeMap.put(key, new ArrivalTime(stationName, component, phase, arrivalTime));
    }

    /**
     * Writes arrival times to an .obs file.
     * 
     * @param fileName the output file name
     */
    public void outputToObs(String fileName) {
        File outputFile = new File(fileName);
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            for (ArrivalTime arrivalTime : arrivalTimeMap.values()) {
                Date date = arrivalTime.getArrivalTime();
                writer.println(String.format("%-6s %-4s %-4s %-1s %-6s %-1s %s %s %9.4f %-3s %9.2e %9.2e %9.2e %9.2e",
                    arrivalTime.getStationName(),
                    arrivalTime.getInstrument(),
                    arrivalTime.getComponent(),
                    arrivalTime.getPPhaseOnset(),
                    arrivalTime.getPhaseDescriptor(),
                    arrivalTime.getFirstMotion(),
                    new SimpleDateFormat("yyyyMMdd").format(date),
                    new SimpleDateFormat("HHmm").format(date),
                    Float.parseFloat(new SimpleDateFormat("ss.SSSS").format(date)),
                    arrivalTime.getErr(),
                    arrivalTime.getErrMag(),
                    arrivalTime.getCodaDuration(),
                    arrivalTime.getAmplitude(),
                    arrivalTime.getPeriod()));
            }
            LOG.info("Pick data written to " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error writing .obs file: " + fileName, e);
        }
    }

    /**
     * Writes the station-pair differential table ({@code stnk,stnl,lagt,coef}) for one phase; see
     * {@link DifferentialDatWriter} for the sign / orientation convention (same as cross-correlation output).
     *
     * @param fileName  output path (e.g. {@code yyMMdd.HHmmss.dat})
     * @param phase     {@code "P"} or {@code "S"}
     * @param coords    optional station coordinates for the pair-distance filter (may be null/empty)
     * @param maxDistKm maximum pair distance in km ({@code <= 0}: no filter)
     * @return number of pair rows written, or 0 if fewer than two stations have a pick of that phase
     */
    public int outputDifferentialDat(String fileName, String phase,
                                     Map<String, CrossCorrelationPickingService.StationCoord> coords,
                                     double maxDistKm) throws IOException {
        List<DifferentialDatWriter.Row> rows = DifferentialDatWriter.buildPairRows(
            arrivalTimeMap.values(), phase, coords, maxDistKm);
        if (rows.isEmpty()) {
            LOG.warning("Differential .dat (" + phase + "): no station pair (need ≥2 stations with a "
                + phase + " pick within the distance limit); skipping file.");
            return 0;
        }
        try {
            int n = DifferentialDatWriter.write(Paths.get(fileName), rows, coords);
            LOG.info("Differential .dat (" + phase + ") written: " + fileName + " (" + n + " rows)");
            return n;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error writing differential .dat: " + fileName, e);
            throw e;
        }
    }

    /** Number of stations that have a pick of the given phase. */
    public int countStations(String phase) {
        int n = 0;
        for (ArrivalTime a : arrivalTimeMap.values()) {
            if (phase.equals(a.getPhaseDescriptor())) {
                n++;
            }
        }
        return n;
    }

    /**
     * Earliest arrival of the given phase among stored picks, or null if none.
     */
    public Date earliestTime(String phase) {
        Date best = null;
        for (ArrivalTime a : arrivalTimeMap.values()) {
            if (!phase.equals(a.getPhaseDescriptor())) {
                continue;
            }
            Date t = a.getArrivalTime();
            if (best == null || t.before(best)) {
                best = t;
            }
        }
        return best;
    }

    /**
     * Reads arrival times from an .obs file.
     * 
     * @param fileName the input file name
     * @return this ArrivalTimeManager instance
     */
    public ArrivalTimeManager readFromObs(String fileName) {
        File inputFile = new File(fileName);
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] columns = line.split("\\s+");
                if (columns.length < 9) {
                    continue;
                }
                String stationName = columns[0];
                String component = columns[2];
                String phase = columns[4];
                
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd HHmm");
                LocalDateTime localDateTime = LocalDateTime.parse(columns[6] + " " + columns[7], formatter);

                double seconds = Double.parseDouble(columns[8]);
                localDateTime = localDateTime.plusSeconds((long)seconds);
                localDateTime = localDateTime.plusNanos((long)((seconds % 1) * 1e9));

                Date date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
                setArrivalTime(stationName, component, phase, date);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error reading .obs file: " + fileName, e);
        }
        return this;
    }

    /**
     * Clears all arrival times.
     */
    public void clearArrivalTimes() {
        arrivalTimeMap.clear();
    }
}

