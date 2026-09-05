package org.example;

import org.example.model.Status;
import org.example.model.WeatherEvent;
import org.example.model.WeatherInfo;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;


public class WeatherEventsGenerator {

    public static WeatherEvent getWeatherEvent() {
        if(ThreadLocalRandom.current().nextInt(100) < 10)
            return null;

        WeatherInfo info = new WeatherInfo(
                ThreadLocalRandom.current().nextInt(13, 40),
                ThreadLocalRandom.current().nextInt(50, 110),
                ThreadLocalRandom.current().nextInt(101)
        );

        // TODO:  the station id should be unique per each service in our k8s cluster
        return new WeatherEvent(
                ThreadLocalRandom.current().nextInt(11),
                info,
                Instant.now().getEpochSecond(),
                getBatteryStatus()

        );
    }
    private static Status getBatteryStatus() {
        int rand = ThreadLocalRandom.current().nextInt(100);

        if(rand < 30)
            return Status.LOW;
        else if(rand < 70)
            return Status.MEDIUM;
        else
            return Status.HIGH;
    }
}
