package com.treloc.xtreloc.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link TimeFormatConverter}.
 */
public class TimeFormatConverterTest {

    @Test
    public void toISO8601_emptyOrNull_returnsEmpty() {
        assertEquals("", TimeFormatConverter.toISO8601(null));
        assertEquals("", TimeFormatConverter.toISO8601(""));
    }

    @Test
    public void toISO8601_alreadyIso_returnsAsIs() {
        String iso = "2024-01-15T12:30:45";
        assertEquals(iso, TimeFormatConverter.toISO8601(iso));
    }

    @Test
    public void toISO8601_yymmddHhmmss_convertsCorrectly() {
        // yymmdd → yyyy-MM-dd (month then day); never yyyy-dd-MM
        assertEquals("2024-01-15T12:30:45", TimeFormatConverter.toISO8601("240115.123045"));
        assertEquals("1999-12-31T23:59:59", TimeFormatConverter.toISO8601("991231.235959"));
        assertEquals("2000-06-01T00:00:00", TimeFormatConverter.toISO8601("000601.000000"));
        assertEquals("2007-12-22T21:29:00", TimeFormatConverter.toISO8601("071222.212900"));
        assertEquals("2011-03-01T19:39:45", TimeFormatConverter.toISO8601("110301.193945"));
    }

    @Test
    public void toEpochMillis_rejectsNonCalendarStrings() {
        assertNull(TimeFormatConverter.toEpochMillis(null));
        assertNull(TimeFormatConverter.toEpochMillis(""));
        assertNull(TimeFormatConverter.toEpochMillis("not-a-date"));
        assertNull(TimeFormatConverter.toEpochMillis("071322.000000")); // month 13
        assertNotNull(TimeFormatConverter.toEpochMillis("071222.212900"));
        assertNotNull(TimeFormatConverter.toEpochMillis("2007-12-22T21:29:00"));
    }

    @Test
    public void formatEpochMillisAsDate_usesYearMonthDay() {
        Long ms = TimeFormatConverter.toEpochMillis("071222.212900");
        assertNotNull(ms);
        assertEquals("2007-12-22", TimeFormatConverter.formatEpochMillisAsDate(ms));
    }

    @Test
    public void toYymmddHhmmss_emptyOrNull_returnsEmpty() {
        assertEquals("", TimeFormatConverter.toYymmddHhmmss(null));
        assertEquals("", TimeFormatConverter.toYymmddHhmmss(""));
    }

    @Test
    public void toYymmddHhmmss_alreadyYymmdd_returnsAsIs() {
        String yymmdd = "240115.123045";
        assertEquals(yymmdd, TimeFormatConverter.toYymmddHhmmss(yymmdd));
    }

    @Test
    public void toYymmddHhmmss_iso_convertsCorrectly() {
        assertEquals("240115.123045", TimeFormatConverter.toYymmddHhmmss("2024-01-15T12:30:45"));
        assertEquals("991231.235959", TimeFormatConverter.toYymmddHhmmss("1999-12-31T23:59:59"));
    }

    @Test
    public void roundTrip_isoToYymmddToIso() {
        String iso = "2024-06-15T08:30:00";
        String yymmdd = TimeFormatConverter.toYymmddHhmmss(iso);
        assertEquals("240615.083000", yymmdd);
        assertEquals(iso, TimeFormatConverter.toISO8601(yymmdd));
    }

    @Test
    public void displayFormat_isConfigurable_andRestorable() {
        try {
            TimeFormatConverter.configureDisplay("yyyy/MM/dd", "HH:mm", " ", "Auto");
            assertEquals("2007/12/22 21:29", TimeFormatConverter.formatForDisplay("2007-12-22T21:29:00"));
            assertEquals("2007/12/22 21:29", TimeFormatConverter.formatForDisplay("071222.212900"));
            // not a calendar date: unchanged
            assertEquals("abc", TimeFormatConverter.formatForDisplay("abc"));

            TimeFormatConverter.configureDisplay("not a pattern {", "", null, "bogus");
            assertEquals("2007-12-22T21:29:00", TimeFormatConverter.formatForDisplay("2007-12-22T21:29:00"));
        } finally {
            TimeFormatConverter.configureDisplay(null, null, null, null);
        }
    }

    @Test
    public void colorBarLabels_autoSwitchesBetweenDateAndTime() {
        try {
            TimeFormatConverter.configureDisplay("yyyy-MM-dd", "HH:mm:ss", "T", "Auto");
            double d0 = TimeFormatConverter.toEpochMillis("2007-12-22T00:00:00");
            double dayEnd = TimeFormatConverter.toEpochMillis("2007-12-22T23:00:00");
            double nextDay = TimeFormatConverter.toEpochMillis("2007-12-23T12:00:00");
            double far = TimeFormatConverter.toEpochMillis("2008-01-22T00:00:00");
            double t = TimeFormatConverter.toEpochMillis("2007-12-22T21:29:05");

            assertEquals("21:29:05", TimeFormatConverter.formatColorBarTime(t, d0, dayEnd));
            assertEquals("2007-12-22 21:29:05", TimeFormatConverter.formatColorBarTime(t, d0, nextDay));
            assertEquals("2007-12-22", TimeFormatConverter.formatColorBarTime(t, d0, far));

            TimeFormatConverter.configureDisplay("yyyy-MM-dd", "HH:mm:ss", "T", "Date");
            assertEquals("2007-12-22", TimeFormatConverter.formatColorBarTime(t, d0, dayEnd));
            TimeFormatConverter.configureDisplay("yyyy-MM-dd", "HH:mm:ss", "T", "Time");
            assertEquals("21:29:05", TimeFormatConverter.formatColorBarTime(t, d0, far));
            TimeFormatConverter.configureDisplay("yyyy-MM-dd", "HH:mm:ss", "T", "Date + time");
            assertEquals("2007-12-22 21:29:05", TimeFormatConverter.formatColorBarTime(t, d0, far));
        } finally {
            TimeFormatConverter.configureDisplay(null, null, null, null);
        }
    }
}
