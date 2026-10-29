package org.example.adapter.outbound.bitcask;

import org.example.application.port.out.KeyValueReader;
import org.example.application.port.out.KeyValueWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BitcaskStore implements KeyValueReader, KeyValueWriter {
    private final Database database;

    public BitcaskStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public Optional<String> get(String key) throws IOException {
        return database.find(key).map(Entry::getValue);
    }

    @Override
    public Map<String, String> getAll() throws IOException {
        Map<String, String> result = new HashMap<>();
        Map<String, Entry> entries = database.getAll();
        entries.forEach((key, entry) -> result.put(key, entry.getValue()));
        return result;
    }

    @Override
    public void set(String key, String value) throws IOException {
        database.set(new Entry(key, value));
    }
}