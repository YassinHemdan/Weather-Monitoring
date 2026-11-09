package org.example.adapter.outbound.bitcask.record;

import java.util.Objects;

public record EncodedRecord(byte[] data, int valueOffset, int valueLength){
    public EncodedRecord {
        Objects.requireNonNull(data, "data");

        if (valueOffset < 0) {
            throw new IllegalArgumentException("valueOffset must be non-negative");
        }

        if (valueLength < 0) {
            throw new IllegalArgumentException("valueLength must be non-negative");
        }

        if ((long) valueOffset + valueLength > data.length) {
            throw new IllegalArgumentException(
                    "Value position exceeds record boundaries"
            );
        }

        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}