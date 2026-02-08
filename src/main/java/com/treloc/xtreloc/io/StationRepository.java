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

    // private final List<Station> stations;
    private final double[][] stationTable;
    private final String[] codes;
    private final double bottomDepth;

    private StationRepository(List<Station> stations) {
        // this.stations = stations;

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
            while (sc.hasNextLine()) {
                String[] p = sc.nextLine().split("\\s+");
                list.add(new Station(
                        p[0],
                        Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]),
                        Double.parseDouble(p[4]),
                        Double.parseDouble(p[5])));
            }
        } catch (FileNotFoundException e) {
            throw new ConfigException("Station file not found: " + file, e);
        }

        return new StationRepository(list);
    }

    /**
     * List<Station>からStationRepositoryを作成
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
