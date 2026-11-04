package org.example.application.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.domain.weather.WeatherEvent;

public class WeatherEventJsonCodec {
    private final ObjectMapper objectMapper;

    public WeatherEventJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(WeatherEvent weatherEvent) throws JsonProcessingException {
        return objectMapper.writeValueAsString(weatherEvent);
    }

    public WeatherEvent decode(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, WeatherEvent.class);
    }
}
