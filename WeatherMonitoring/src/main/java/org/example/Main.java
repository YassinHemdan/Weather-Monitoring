package org.example;

public class Main {
    public static void main(String[] args) {
        try {
            WeatherEventsProducer producer = new WeatherEventsProducer();
            producer.start();
        } catch (Exception e) {
            throw new RuntimeException("Error!: " + e.getMessage());
        }
    }
}