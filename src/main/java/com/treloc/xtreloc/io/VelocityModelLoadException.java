package com.treloc.xtreloc.io;

/**
 * Failed to load the velocity model used by {@link com.treloc.xtreloc.solver.Raytrace1D}.
 */
public class VelocityModelLoadException extends Exception {

    public VelocityModelLoadException(String message) {
        super(message);
    }

    public VelocityModelLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
