package org.example.adapter.outbound.bitcask.record;

import java.util.Objects;

public record RecordMetadata(
        String key,
        long timestamp,
        int valueOffset,
        int valueLength,
        int recordLength
) {
    public RecordMetadata {
        Objects.requireNonNull(key, "key");

        if (valueOffset < 0 || valueLength < 0 || recordLength < 0) {
            throw new IllegalArgumentException(
                    "Record metadata contains negative lengths or offsets"
            );
        }

        if ((long) valueOffset + valueLength > recordLength) {
            throw new IllegalArgumentException(
                    "Value position exceeds record boundaries"
            );
        }
    }
}
