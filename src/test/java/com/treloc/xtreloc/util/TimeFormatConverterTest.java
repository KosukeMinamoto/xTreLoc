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
        assertEquals("2024-01-15T12:30:45", TimeFormatConverter.toISO8601("240115.123045"));
        assertEquals("1999-12-31T23:59:59", TimeFormatConverter.toISO8601("991231.235959"));
        assertEquals("2000-06-01T00:00:00", TimeFormatConverter.toISO8601("000601.000000"));
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
}
