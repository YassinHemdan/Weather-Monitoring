package org.example.adapter.outbound.bitcask;

import java.io.IOException;

public final class CorruptEntryException extends IOException {
    public CorruptEntryException(String message) {
        super(message);
    }

    public CorruptEntryException(String message, Throwable cause) {
        super(message, cause);
    }
}