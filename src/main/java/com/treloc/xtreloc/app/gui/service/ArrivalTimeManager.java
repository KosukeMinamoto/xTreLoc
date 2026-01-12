package com.treloc.xtreloc.app.gui.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

/**
 * Manages arrival times for P and S phases.
 * Handles reading and writing .obs files in the standard format.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class ArrivalTimeManager {
    private HashMap<String, ArrivalTime> arrivalTimeMap;

    public ArrivalTimeManager() {
        arrivalTimeMap = new HashMap<>();
    }

    private String createKey(String stationName, String phase) {
        return stationName + "_" + phase;
    }

    /**
     * Updates or adds an arrival time for a station and phase.
     * 
     * @param stationName the station name
     * @param component the component (e.g., "Z", "N", "E")
     * @param phase the phase ("P" or "S")
     * @param arrivalTime the arrival time
     */
    public void updateArrivalTime(String stationName, String component, String phase, Date arrivalTime) {
        stationName = stationName.replace(" ", "");
        String key = createKey(stationName, phase);
        if (arrivalTimeMap.containsKey(key)) {
            System.out.println("Updated: " + stationName + " (" + phase + ") " + arrivalTime);
            arrivalTimeMap.get(key).setArrivalTime(arrivalTime);
        } else {
            System.out.println("Added:   " + stationName + " (" + phase + ") " + arrivalTime);
            arrivalTimeMap.put(key, new ArrivalTime(stationName, component, phase, arrivalTime));
        }
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
            System.out.println("Removed: " + stationName + " (" + phase + ") " + arrivalTimeMap.get(key).getArrivalTime());
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
     * Prints all arrival times to the console.
     */
    public void printAllArrivalTimes() {
        for (ArrivalTime arrival : arrivalTimeMap.values()) {
            System.out.println(arrival);
        }
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
            System.out.println("Pick data written to " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error writing .obs file: " + e.getMessage());
            e.printStackTrace();
        }
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
            System.err.println("Error reading .obs file: " + e.getMessage());
            e.printStackTrace();
        }
        return this;
    }

    /**
     * Clears all arrival times.
     */
    public void clearArrivalTimes() {
        arrivalTimeMap.clear();
    }

    /**
     * Checks if an arrival time exists for the given key.
     * 
     * @param key the key (stationName_phase)
     * @return true if the arrival time exists
     */
    public boolean containsArrivalTime(String key) {
        return arrivalTimeMap.containsKey(key);
    }
}

