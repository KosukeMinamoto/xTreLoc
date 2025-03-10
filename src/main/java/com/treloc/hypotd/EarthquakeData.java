package com.treloc.hypotd;

import org.locationtech.jts.geom.Coordinate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class EarthquakeData {
    private final Coordinate coordinate;
    private final LocalDateTime time;
    private final double depth;

    public EarthquakeData(Coordinate coordinate, String timeStr, double depth) {
        this.coordinate = coordinate;
        this.time = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.depth = depth;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public String getTimeString() {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public double getDepth() {
        return depth;
    }
} 