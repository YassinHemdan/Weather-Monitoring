package org.example.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serializer;
import org.example.model.WeatherEvent;

public class WeatherSerializer implements Serializer<WeatherEvent> {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public byte[] serialize(String s, WeatherEvent weatherEvent) {
        if (weatherEvent == null)
                return null;
        try {
            return objectMapper.writeValueAsBytes(weatherEvent);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing WeatherEvent object to byte[]", e);
        }
    }
}
