package org.example.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.bitcask.Database;
import org.example.bitcask.Entry;
import org.example.mapper.WeatherEventEntryMapper;
import org.example.model.WeatherEvent;

import java.io.IOException;

public class BitcaskService {
    private final Database database;
    private final WeatherEventEntryMapper mapper;
    public BitcaskService(Database database, WeatherEventEntryMapper mapper) {
        this.database = database;
        this.mapper = mapper;
    }

    public void write(ConsumerRecord<String, WeatherEvent> record) throws IOException {
        this.database.set(mapper.toEntry(record));
    }
    public Entry read(String key) throws IOException {
        return this.database.get(key);
    }
}
