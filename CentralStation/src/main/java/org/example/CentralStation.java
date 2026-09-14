package org.example;

import org.example.bitcask.Database;
import org.example.config.KafkaConfig;
import org.example.consumer.WeatherConsumer;
import org.example.mapper.WeatherEventEntryMapper;
import org.example.services.WeatherLoggerService;

import java.io.IOException;
import java.util.Properties;

public class CentralStation implements AutoCloseable{
    private final WeatherLoggerService weatherLoggerService;
    private final WeatherConsumer weatherConsumer;

    public CentralStation() throws IOException {
        Properties prop = KafkaConfig.loadProperties();
        this.weatherConsumer = new WeatherConsumer(prop, prop.getProperty("kafka.topic"));
        this.weatherLoggerService = new WeatherLoggerService(
                new Database("central-station-data"),
                new WeatherEventEntryMapper()
        );
    }

    public void start() {
        this.weatherConsumer.start(this.weatherLoggerService::append);
    }
    
    @Override
    public void close() throws Exception {
        weatherLoggerService.close();
        weatherConsumer.close();
    }
}
