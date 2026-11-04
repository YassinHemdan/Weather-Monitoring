package org.example.adapter.outbound.bitcask;

public record Entry(long timestamp, String key, String value) {
    public Entry(String key, String value) {
        this(System.currentTimeMillis(), key, value);
    }
}