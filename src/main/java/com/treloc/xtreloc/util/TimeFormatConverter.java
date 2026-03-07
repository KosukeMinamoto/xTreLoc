package com.treloc.xtreloc.util;

/**
 * Time format conversion utility class
 * Provides conversion between yymmdd.hhmmss and ISO 8601 (YYYY-MM-DDTHH:MM:SS) formats
 */
public class TimeFormatConverter {
    
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
        
        if (timeStr.contains("T") && timeStr.contains("-")) {
            return timeStr;
        }
        
        try {
            if (timeStr.length() >= 13 && timeStr.contains(".")) {
                String datePart = timeStr.substring(0, 6);
                String timePart = timeStr.substring(7, 13);
                
                int yy = Integer.parseInt(datePart.substring(0, 2));
                int mm = Integer.parseInt(datePart.substring(2, 4));
                int dd = Integer.parseInt(datePart.substring(4, 6));
                int hh = Integer.parseInt(timePart.substring(0, 2));
                int min = Integer.parseInt(timePart.substring(2, 4));
                int ss = Integer.parseInt(timePart.substring(4, 6));
                
                int year = (yy < 50) ? (2000 + yy) : (1900 + yy);
                
                return String.format("%04d-%02d-%02dT%02d:%02d:%02d", year, mm, dd, hh, min, ss);
            }
        } catch (Exception e) {
        }
        
        return timeStr;
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

