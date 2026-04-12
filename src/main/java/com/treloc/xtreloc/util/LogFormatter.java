package com.treloc.xtreloc.util;

import java.util.Calendar;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Custom log formatter for xTreLoc
 */
public class LogFormatter extends Formatter {

    private final Calendar calendar = Calendar.getInstance();

    @Override
    public synchronized String format(LogRecord record) {

        calendar.setTimeInMillis(record.getMillis());

        StringBuilder sb = new StringBuilder(128);

        sb.append(String.format(
                "%1$tF %1$tT ",
                calendar));

        sb.append(record.getLevel().getName());
        sb.append(" ");

        if (record.getSourceClassName() != null) {
            sb.append(record.getSourceClassName());
        } else {
            sb.append(record.getLoggerName());
        }

        if (record.getSourceMethodName() != null) {
            sb.append(" [")
                    .append(record.getSourceMethodName())
                    .append("]");
        }

        sb.append(" - ");
        sb.append(formatMessage(record));
        sb.append(System.lineSeparator());

        if (record.getThrown() != null) {
            sb.append(record.getThrown());
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }
}
