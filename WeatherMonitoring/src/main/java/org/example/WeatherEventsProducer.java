package org.example;

import org.example.config.KafkaConfig;
import org.example.model.WeatherEvent;
import org.example.producer.WeatherProducer;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.example.WeatherEventsGenerator.getWeatherEvent;
public class WeatherEventsProducer implements AutoCloseable{
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor();

    private final WeatherProducer producer;
    public WeatherEventsProducer() throws Exception {
        Properties prop = KafkaConfig.loadProperties();
        producer = new WeatherProducer(prop, prop.getProperty("kafka.topic"));
    }

    public void start() {
        executor.scheduleAtFixedRate(() -> {
            WeatherEvent event = getWeatherEvent();
            if(event != null) {
                emit(event);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    private void emit(WeatherEvent event) {
        // TODO: Better handling for exceptions
        try {
            producer.send(event);
        } catch (Exception e) {
            System.out.println("Error: Can't send an event");
        }
    }
    @Override
    public void close() throws Exception {
        executor.shutdown();
        producer.close();
    }
}
