package org.example.adapter.outbound.bitcask;

import java.util.Objects;

public record Entry(long timestamp, String key, String value) {
    public Entry(String key, String value) {
        this(System.currentTimeMillis(), key, value);
    }
    public Entry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
    }
}