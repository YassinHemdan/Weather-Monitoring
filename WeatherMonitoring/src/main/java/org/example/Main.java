package org.example;
import org.example.config.KafkaConfig;
import org.example.model.WeatherMessage;
import org.example.producer.WeatherProducer;

import static org.example.helper.generateWeatherMessage;

public class Main {
    public static void main(String[] args) {
        WeatherMessage message = generateWeatherMessage();
        WeatherProducer producer = new WeatherProducer(KafkaConfig.loadProperties(), "weather");

        try {
            producer.send(message);
            producer.close();
        } catch (Exception e) {
            System.out.println("PROBLEM!!");
        }
    }
}