package org.example.adapter.outbound.bitcask.record;

import java.io.IOException;

public final class CorruptRecordException extends IOException {
    public CorruptRecordException(String message) {
        super(message);
    }
    public CorruptRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}