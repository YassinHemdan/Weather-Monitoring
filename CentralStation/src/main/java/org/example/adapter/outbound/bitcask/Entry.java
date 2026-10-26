package org.example.adapter.outbound.bitcask;

public class Entry {
    private final String key;
    private final String value;
    private final long timestamp;

    public Entry(long timestamp, String key, String value) {
        this.timestamp = timestamp;
        this.key = key;
        this.value = value;
    }
    public Entry(String key, String value) {
        this(System.currentTimeMillis(), key, value);
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
