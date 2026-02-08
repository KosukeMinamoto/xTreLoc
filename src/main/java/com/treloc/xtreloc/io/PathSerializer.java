package com.treloc.xtreloc.io;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Custom serializer for Path type to JSON string.
 */
public class PathSerializer extends StdScalarSerializer<Path> {

    private static final long serialVersionUID = 1L;

    public PathSerializer() {
        super(Path.class);
    }

    @Override
    public void serialize(Path value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(value.toString());
        }
    }
}
