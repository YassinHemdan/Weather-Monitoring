package org.example.application.ingestion;

import org.example.application.port.in.IngestWeatherEventUseCase;
import org.example.application.port.out.KeyValueWriter;
import org.example.domain.weather.WeatherEvent;

import java.io.IOException;

public class WeatherLoggingService implements IngestWeatherEventUseCase {
    private final KeyValueWriter writer;
    private final WeatherEventJsonCodec jsonCodec;
    public WeatherLoggingService(
            KeyValueWriter writer,
            WeatherEventJsonCodec jsonCodec
    ) {
        this.writer = writer;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public void ingest(WeatherEvent event) throws IOException {
        this.writer.set(Long.toString(event.getStation_id()), jsonCodec.encode(event));
    }
}
