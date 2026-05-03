package com.treloc.xtreloc.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consistent logging for solver / SwingWorker failures: unwraps wrapped exceptions,
 * chooses a log level by failure kind, and formats user-visible text without duplicating
 * the throwable message in the log record string (formatters append the exception line).
 */
public final class ExecutionFailureReporter {

    private ExecutionFailureReporter() {
    }

    /**
     * Unwraps {@link ExecutionException} / {@link CompletionException} chains to the root cause
     * for classification; the original {@code t} is still useful when passed to {@link Logger#log}.
     */
    public static Throwable unwrap(Throwable t) {
        if (t == null) {
            return null;
        }
        Throwable u = t;
        int guard = 0;
        while (guard++ < 16) {
            if (u instanceof ExecutionException || u instanceof CompletionException) {
                Throwable c = u.getCause();
                if (c == null || c == u) {
                    break;
                }
                u = c;
                continue;
            }
            break;
        }
        return u;
    }

    /**
     * Suggested {@link Level} for the unwrapped failure (caller may override).
     */
    public static Level suggestLevel(Throwable t) {
        Throwable r = unwrap(t);
        if (r == null) {
            return Level.INFO;
        }
        if (r instanceof Error) {
            return Level.SEVERE;
        }
        if (r instanceof IOException || r instanceof UncheckedIOException) {
            return Level.WARNING;
        }
        if (r instanceof IllegalArgumentException || r instanceof IllegalStateException
                || r instanceof SecurityException) {
            return Level.WARNING;
        }
        if (r instanceof InterruptedException) {
            return Level.FINE;
        }
        return Level.SEVERE;
    }

    /**
     * Logs {@code t} with {@code summary}. The summary must not repeat {@code t.getMessage()}
     * in full, or typical formatters will print the same text twice (record + exception line).
     */
    public static void log(Logger logger, String summary, Throwable t) {
        if (logger == null || t == null) {
            return;
        }
        logger.log(suggestLevel(t), summary, t);
    }

    public static String oneLine(Throwable t) {
        Throwable r = unwrap(t);
        if (r == null) {
            return "";
        }
        String m = r.getMessage();
        if (m == null || m.isEmpty()) {
            return r.getClass().getSimpleName();
        }
        return m.replace('\n', ' ').trim();
    }

    /** Multi-line text for the Execution log panel. */
    public static String formatExecutionLogBlock(Throwable t) {
        Throwable r = unwrap(t);
        if (r == null) {
            return "ERROR: Unknown failure (no exception)\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR: Execution failed\n");
        sb.append("  Type: ").append(r.getClass().getName()).append("\n");
        sb.append("  Message: ")
                .append(r.getMessage() != null ? r.getMessage().replace('\n', ' ') : "(none)")
                .append("\n");
        Throwable c = r.getCause();
        int depth = 0;
        while (c != null && depth++ < 8) {
            sb.append("  Caused by: ").append(c.getClass().getName()).append(": ")
                    .append(c.getMessage() != null ? c.getMessage().replace('\n', ' ') : "(none)")
                    .append("\n");
            c = c.getCause();
        }
        return sb.toString();
    }

    /** Short text for modal dialogs. */
    public static String dialogMessage(Throwable t) {
        Throwable r = unwrap(t);
        if (r == null) {
            return "An error occurred.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(r.getClass().getSimpleName());
        sb.append(": ");
        sb.append(r.getMessage() != null ? r.getMessage() : "(no message)");
        Throwable c = r.getCause();
        if (c != null) {
            sb.append("\nCaused by: ");
            sb.append(c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName());
        }
        return sb.toString();
    }
}
