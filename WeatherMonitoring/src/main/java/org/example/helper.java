package org.example;

import org.example.model.Status;
import org.example.model.WeatherInfo;
import org.example.model.WeatherMessage;

import java.time.Instant;

public class helper {
    public static WeatherMessage generateWeatherMessage() {
        //TODO: We need to follow the project's requirements
        return new WeatherMessage(
                1,
                new WeatherInfo(3, 3, 3),
                Instant.now().getEpochSecond(),
                Status.LOW
        );
    }
}
