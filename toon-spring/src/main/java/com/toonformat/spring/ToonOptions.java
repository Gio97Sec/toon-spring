package com.toonformat.spring;

/**
 * Configuration options for TOON serialization and deserialization.
 */
public class ToonOptions {

    private ToonDelimiter delimiter = ToonDelimiter.COMMA;
    private int indent = 2;
    private boolean strict = true;
    private boolean lengthMarker = false;

    public ToonOptions() {}

    public ToonDelimiter getDelimiter() {
        return delimiter;
    }

    public ToonOptions setDelimiter(ToonDelimiter delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public int getIndent() {
        return indent;
    }

    public ToonOptions setIndent(int indent) {
        this.indent = indent;
        return this;
    }

    public boolean isStrict() {
        return strict;
    }

    public ToonOptions setStrict(boolean strict) {
        this.strict = strict;
        return this;
    }

    public boolean isLengthMarker() {
        return lengthMarker;
    }

    public ToonOptions setLengthMarker(boolean lengthMarker) {
        this.lengthMarker = lengthMarker;
        return this;
    }
}
