package com.treloc.xtreloc.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Time format conversion utility class
 * Provides conversion between yymmdd.hhmmss and ISO 8601 ({@code yyyy-MM-dd'T'HH:mm:ss}) formats.
 * Calendar fields are always year–month–day (never year–day–month).
 */
public class TimeFormatConverter {

    private static final DateTimeFormatter ISO_LOCAL =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private static final DateTimeFormatter ISO_DATE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);
    
    /**
     * Converts yymmdd.hhmmss format to ISO 8601 format (YYYY-MM-DDTHH:MM:SS)
     * Returns as-is if already in ISO 8601 format
     * 
     * @param timeStr time string in yymmdd.hhmmss or ISO 8601 format
     * @return time string in ISO 8601 format
     */
    public static String toISO8601(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "";
        }

        LocalDateTime ldt = parseToLocalDateTime(timeStr);
        if (ldt != null) {
            return ldt.format(ISO_LOCAL);
        }
        return timeStr;
    }

    /**
     * Parses a catalog / file time string to {@link LocalDateTime}, or {@code null} if it is not a
     * calendar date (so map Time-colouring must not be applied).
     */
    public static LocalDateTime parseToLocalDateTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        String s = timeStr.trim();
        try {
            if (s.contains("T") && s.contains("-")) {
                int dot = s.indexOf('.');
                if (dot > 0) {
                    s = s.substring(0, dot);
                }
                if (s.length() == 10) {
                    return LocalDateTime.parse(s + "T00:00:00", ISO_LOCAL);
                }
                if (s.length() >= 19) {
                    return LocalDateTime.parse(s.substring(0, 19), ISO_LOCAL);
                }
                return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            if (s.length() >= 13 && s.indexOf('.') == 6) {
                String datePart = s.substring(0, 6);
                String timePart = s.substring(7, 13);
                int yy = Integer.parseInt(datePart.substring(0, 2));
                int mm = Integer.parseInt(datePart.substring(2, 4));
                int dd = Integer.parseInt(datePart.substring(4, 6));
                int hh = Integer.parseInt(timePart.substring(0, 2));
                int min = Integer.parseInt(timePart.substring(2, 4));
                int ss = Integer.parseInt(timePart.substring(4, 6));
                if (mm < 1 || mm > 12 || dd < 1 || dd > 31 || hh > 23 || min > 59 || ss > 59) {
                    return null;
                }
                int year = (yy < 50) ? (2000 + yy) : (1900 + yy);
                return LocalDateTime.of(year, mm, dd, hh, min, ss);
            }
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    /**
     * Epoch millis (UTC) for colouring by Time, or {@code null} when the string is not a calendar date.
     */
    public static Long toEpochMillis(String timeStr) {
        LocalDateTime ldt = parseToLocalDateTime(timeStr);
        if (ldt == null) {
            return null;
        }
        return ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /** Colour-bar label from an epoch-millis value produced by {@link #toEpochMillis}. */
    public static String formatEpochMillisAsDate(double epochMillis) {
        if (!Double.isFinite(epochMillis)) {
            return "";
        }
        LocalDateTime ldt = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(Math.round(epochMillis)), ZoneOffset.UTC);
        return ldt.format(display.date);
    }

    // ------------------------------------------------------------------ display format (user setting)

    public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";
    public static final String DEFAULT_TIME_PATTERN = "HH:mm:ss";
    public static final String DEFAULT_DATE_TIME_SEPARATOR = "T";
    /** Colour-bar time label modes. */
    public static final String COLORBAR_AUTO = "Auto";
    public static final String COLORBAR_DATE = "Date";
    public static final String COLORBAR_DATE_TIME = "Date + time";
    public static final String COLORBAR_TIME = "Time";

    /** Auto mode: ranges at least this long (ms) show the date only. */
    private static final long AUTO_DATE_ONLY_SPAN_MS = 2L * 24 * 3600 * 1000;

    private static final class DisplayFormat {
        final DateTimeFormatter date;
        final DateTimeFormatter time;
        final String separator;
        final String colorBarMode;

        DisplayFormat(DateTimeFormatter date, DateTimeFormatter time, String separator, String colorBarMode) {
            this.date = date;
            this.time = time;
            this.separator = separator;
            this.colorBarMode = colorBarMode;
        }
    }

    private static volatile DisplayFormat display = new DisplayFormat(
        ISO_DATE, DateTimeFormatter.ofPattern(DEFAULT_TIME_PATTERN, Locale.US),
        DEFAULT_DATE_TIME_SEPARATOR, COLORBAR_AUTO);

    /** @return true if {@code pattern} is a usable {@link DateTimeFormatter} pattern (non-empty). */
    public static boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return false;
        }
        try {
            DateTimeFormatter.ofPattern(pattern.trim(), Locale.US).format(LocalDateTime.of(2000, 1, 2, 3, 4, 5));
            return true;
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            return false;
        }
    }

    /**
     * Sets how times are shown in the GUI (catalog table Time column, map colour bar). Invalid patterns fall back
     * to the defaults. Stored values and file formats are never affected.
     *
     * @param datePattern  e.g. {@code yyyy-MM-dd}, {@code yyyy/MM/dd}, {@code dd/MM/yyyy}
     * @param timePattern  e.g. {@code HH:mm:ss}, {@code HH:mm}
     * @param separator    text between date and time in the table, e.g. {@code T} or a space
     * @param colorBarMode one of the {@code COLORBAR_*} constants
     */
    public static void configureDisplay(String datePattern, String timePattern, String separator,
                                        String colorBarMode) {
        String dp = isValidPattern(datePattern) ? datePattern.trim() : DEFAULT_DATE_PATTERN;
        String tp = isValidPattern(timePattern) ? timePattern.trim() : DEFAULT_TIME_PATTERN;
        String sep = separator != null ? separator : DEFAULT_DATE_TIME_SEPARATOR;
        String mode = COLORBAR_AUTO;
        for (String m : new String[] {COLORBAR_AUTO, COLORBAR_DATE, COLORBAR_DATE_TIME, COLORBAR_TIME}) {
            if (m.equalsIgnoreCase(colorBarMode != null ? colorBarMode.trim() : "")) {
                mode = m;
            }
        }
        display = new DisplayFormat(DateTimeFormatter.ofPattern(dp, Locale.US),
            DateTimeFormatter.ofPattern(tp, Locale.US), sep, mode);
    }

    /**
     * Formats a catalog time string for display using the configured date/time patterns.
     * Strings that are not calendar dates are returned unchanged.
     */
    public static String formatForDisplay(String timeStr) {
        LocalDateTime ldt = parseToLocalDateTime(timeStr);
        if (ldt == null) {
            return timeStr;
        }
        DisplayFormat d = display;
        return ldt.format(d.date) + d.separator + ldt.format(d.time);
    }

    /**
     * Colour-bar label for an epoch-millis tick. In {@link #COLORBAR_AUTO} mode, ranges of two days or more show
     * the date, shorter ranges inside one calendar day show the time, and other short ranges show date and time.
     *
     * @param value     tick value (epoch millis)
     * @param rangeMin  colour-bar minimum (epoch millis)
     * @param rangeMax  colour-bar maximum (epoch millis)
     */
    public static String formatColorBarTime(double value, double rangeMin, double rangeMax) {
        if (!Double.isFinite(value)) {
            return "";
        }
        DisplayFormat d = display;
        LocalDateTime ldt = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(Math.round(value)), ZoneOffset.UTC);
        String mode = d.colorBarMode;
        if (COLORBAR_AUTO.equals(mode)) {
            double span = rangeMax - rangeMin;
            if (!Double.isFinite(span) || span >= AUTO_DATE_ONLY_SPAN_MS) {
                mode = COLORBAR_DATE;
            } else {
                LocalDateTime lo = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(Math.round(rangeMin)), ZoneOffset.UTC);
                LocalDateTime hi = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(Math.round(rangeMax)), ZoneOffset.UTC);
                mode = lo.toLocalDate().equals(hi.toLocalDate()) ? COLORBAR_TIME : COLORBAR_DATE_TIME;
            }
        }
        switch (mode) {
            case COLORBAR_TIME:
                return ldt.format(d.time);
            case COLORBAR_DATE_TIME:
                return ldt.format(d.date) + " " + ldt.format(d.time);
            case COLORBAR_DATE:
            default:
                return ldt.format(d.date);
        }
    }
    
    /**
     * Converts ISO 8601 format to yymmdd.hhmmss format
     * Returns as-is if already in yymmdd.hhmmss format
     * 
     * @param timeStr time string in ISO 8601 or yymmdd.hhmmss format
     * @return time string in yymmdd.hhmmss format
     */
    public static String toYymmddHhmmss(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "";
        }
        
        if (timeStr.contains(".") && timeStr.length() >= 13 && !timeStr.contains("T")) {
            return timeStr;
        }
        
        try {
            if (timeStr.contains("T") && timeStr.contains("-")) {
                int dotIndex = timeStr.indexOf('.');
                if (dotIndex > 0) {
                    timeStr = timeStr.substring(0, dotIndex);
                }
                
                String[] parts = timeStr.split("T");
                if (parts.length == 2) {
                    String datePart = parts[0];
                    String timePart = parts[1];
                    
                    String[] dateParts = datePart.split("-");
                    String[] timeParts = timePart.split(":");
                    
                    if (dateParts.length == 3 && timeParts.length >= 3) {
                        int year = Integer.parseInt(dateParts[0]);
                        int mm = Integer.parseInt(dateParts[1]);
                        int dd = Integer.parseInt(dateParts[2]);
                        int hh = Integer.parseInt(timeParts[0]);
                        int min = Integer.parseInt(timeParts[1]);
                        int ss = Integer.parseInt(timeParts[2]);
                        
                        int yy = (year >= 2000) ? (year - 2000) : (year - 1900);
                        if (yy < 0) yy = 0;
                        if (yy > 99) yy = 99;
                        
                        return String.format("%02d%02d%02d.%02d%02d%02d", yy, mm, dd, hh, min, ss);
                    }
                }
            }
        } catch (Exception e) {
        }
        
        return timeStr;
    }
    
    /**
     * Normalizes time string for comparison
     * Supports both ISO 8601 and yymmdd.hhmmss formats
     * Removes seconds for minute-level comparison
     * 
     * @param time time string
     * @return normalized time string (minute-level, ISO 8601 format)
     */
    public static String normalizeTimeForComparison(String time) {
        if (time == null || time.isEmpty()) {
            return "";
        }
        
        String isoTime = toISO8601(time);
        
        if (isoTime.contains("T") && isoTime.contains("-")) {
            int dotIndex = isoTime.indexOf('.');
            if (dotIndex > 0) {
                isoTime = isoTime.substring(0, dotIndex);
            }
            
            int lastColon = isoTime.lastIndexOf(':');
            if (lastColon > 0) {
                return isoTime.substring(0, lastColon);
            }
            return isoTime;
        }
        
        return time;
    }
}

