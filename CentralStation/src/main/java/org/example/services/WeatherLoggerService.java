package org.example.services;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.bitcask.Database;
import org.example.mapper.WeatherEventEntryMapper;
import org.example.model.WeatherEvent;

import java.io.IOException;

public class WeatherLoggerService implements AutoCloseable{
    private final Database database;
    private final WeatherEventEntryMapper mapper;
    public WeatherLoggerService(Database database, WeatherEventEntryMapper mapper) {
        this.database = database;
        this.mapper = mapper;
    }

    public void append(ConsumerRecord<String, WeatherEvent> record) throws IOException {
        database.set(mapper.toEntry(record));
    }

    @Override
    public void close() throws Exception {
        this.database.close();
    }
}
