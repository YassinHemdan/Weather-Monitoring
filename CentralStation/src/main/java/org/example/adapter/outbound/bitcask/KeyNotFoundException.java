package org.example.adapter.outbound.bitcask;

import java.util.NoSuchElementException;

public final class KeyNotFoundException extends NoSuchElementException {
    public KeyNotFoundException(String key) {
        super("No entry found for key '" + key + "'");
    }
}