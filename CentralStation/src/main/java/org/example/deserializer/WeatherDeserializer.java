package org.example.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;
import org.example.model.WeatherEvent;

public class WeatherDeserializer implements Deserializer<WeatherEvent> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WeatherEvent deserialize(String s, byte[] bytes) {
        if(bytes == null)
            return null;
        try {
            return objectMapper.readValue(bytes, WeatherEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing WeatherEvent from byte[]", e);
        }
    }
}
