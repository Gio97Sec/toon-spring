package com.toonformat.spring;

/**
 * Supported delimiters for TOON array values.
 */
public enum ToonDelimiter {
    COMMA(','),
    TAB('\t'),
    PIPE('|');

    private final char character;

    ToonDelimiter(char character) {
        this.character = character;
    }

    public char getCharacter() {
        return character;
    }

    /**
     * Returns the delimiter symbol to embed inside bracket segments.
     * Comma is the default and is not explicitly shown.
     */
    public String getBracketSuffix() {
        return this == COMMA ? "" : String.valueOf(character);
    }
}
