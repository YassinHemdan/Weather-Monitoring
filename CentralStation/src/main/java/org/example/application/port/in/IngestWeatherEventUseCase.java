package org.example.application.port.in;

import org.example.domain.weather.WeatherEvent;

import java.io.IOException;

public interface IngestWeatherEventUseCase{
    void ingest(WeatherEvent event) throws IOException;
}
