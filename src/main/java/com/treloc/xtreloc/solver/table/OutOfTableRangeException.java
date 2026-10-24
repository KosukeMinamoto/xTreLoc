package com.treloc.xtreloc.solver.table;

/**
 * A source position lies outside the range of the travel-time table. The message contains the phrase
 * "Depth may be outside model range" so that existing solver code (e.g. triple-difference relocation) treats it as an
 * out-of-range event and marks it {@code ERR}.
 */
public class OutOfTableRangeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OutOfTableRangeException(String detail) {
        super("Depth may be outside model range (travel-time table): " + detail);
    }
}
