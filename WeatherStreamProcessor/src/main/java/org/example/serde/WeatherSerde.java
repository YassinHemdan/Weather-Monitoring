package org.example.serde;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.example.model.WeatherEvent;

public class WeatherSerde implements Serde<WeatherEvent> {
    @Override
    public Serializer<WeatherEvent> serializer() {
        return new WeatherSerializer();
    }

    @Override
    public Deserializer<WeatherEvent> deserializer() {
        return new WeatherDeserializer();
    }
}
