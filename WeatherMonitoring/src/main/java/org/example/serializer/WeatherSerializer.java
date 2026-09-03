package org.example.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;
import org.example.model.WeatherMessage;

import java.util.Map;

public class WeatherSerializer implements Serializer<WeatherMessage> {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
//        Serializer.super.configure(configs, isKey);
    }

    @Override
    public byte[] serialize(String s, WeatherMessage data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing WeatherMessage object to byte[]", e);
        }
    }

    @Override
    public void close() {
//        Serializer.super.close();
    }
}
