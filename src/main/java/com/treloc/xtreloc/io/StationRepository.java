package com.treloc.xtreloc.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Repository for station information
 */
public final class StationRepository {

    private final double[][] stationTable;
    private final String[] codes;
    private final double bottomDepth;

    private StationRepository(List<Station> stations) {
        int n = stations.size();
        stationTable = new double[n][5];
        codes = new String[n];

        double bottom = 0;
        for (int i = 0; i < n; i++) {
            Station s = stations.get(i);
            codes[i] = s.getCode();
            stationTable[i][0] = s.getLat();
            stationTable[i][1] = s.getLon();
            // Convert depth from meters to kilometers (also invert sign)
            // Input: -m (meters, negative value) → Output: km (kilometers, positive value, underground depth)
            double depKm = -s.getDep() / 1000.0;
            stationTable[i][2] = depKm;
            stationTable[i][3] = s.getPc();
            stationTable[i][4] = s.getSc();
            bottom = Math.max(bottom, depKm);
        }
        this.bottomDepth = bottom + 0.01;
    }

    public static StationRepository load(Path file) {
        List<Station> list = new ArrayList<>();

        try (Scanner sc = new Scanner(new File(file.toString()))) {
            int lineNum = 0;
            while (sc.hasNextLine()) {
                lineNum++;
                String line = sc.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] p = line.split("\\s+");
                if (p.length < 6) {
                    throw new ConfigException(
                        String.format("Station file %s line %d: expected at least 6 columns (code lat lon dep pc sc), got %d. Line: %s",
                            file, lineNum, p.length, line));
                }
                try {
                    list.add(new Station(
                            p[0],
                            Double.parseDouble(p[1]),
                            Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]),
                            Double.parseDouble(p[4]),
                            Double.parseDouble(p[5])));
                } catch (NumberFormatException e) {
                    throw new ConfigException(
                        String.format("Station file %s line %d: invalid number. Line: %s", file, lineNum, line), e);
                }
            }
        } catch (FileNotFoundException e) {
            throw new ConfigException("Station file not found: " + file, e);
        }

        return new StationRepository(list);
    }

    /**
     * Creates a StationRepository from a list of stations.
     */
    public static StationRepository fromList(List<Station> stations) {
        return new StationRepository(stations);
    }

    public double[][] getStationTable() {
        return stationTable;
    }

    public String[] getCodes() {
        return codes;
    }

    public double getBottomDepth() {
        return bottomDepth;
    }
}
