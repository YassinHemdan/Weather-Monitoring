package org.example.application.port.out;

import java.io.IOException;

public interface KeyValueWriter {
    void set(String key, String value, long timeStamp) throws IOException;
}
