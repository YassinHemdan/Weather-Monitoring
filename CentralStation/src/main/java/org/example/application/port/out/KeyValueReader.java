package org.example.application.port.out;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KeyValueReader {
    Optional<String> get(String key) throws IOException;
    Map<String, String> getAll() throws IOException;
}
