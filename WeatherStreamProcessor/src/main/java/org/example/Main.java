package org.example;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.example.model.WeatherEvent;
import org.example.model.WeatherInfo;
import org.example.serde.WeatherSerde;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Main {
    public static void main(String[] args) throws IOException {
        Properties streamProperties = new Properties();
        try(FileInputStream fis = new FileInputStream("src/main/resources/application.properties")){
            streamProperties.load(fis);
        }
        streamProperties.put(StreamsConfig.APPLICATION_ID_CONFIG, "basic-streams");

        final String inputTopic = streamProperties.getProperty("kafka.input.topic");
        final String outputTopic  = streamProperties.getProperty("kafka.output.topic");

        final WeatherSerde weatherSerde = new WeatherSerde();
        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, WeatherEvent> firstStream =
                builder.stream(inputTopic, Consumed.with(Serdes.String(), weatherSerde));

        firstStream
                .filter((key, value) -> {
                    WeatherInfo info = value.getWeather();
                    return info != null && info.getHumidity() > 70;
                })
                .mapValues(value -> "Expect rain throughout the day. Carry an umbrella.")
                .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams streams = new KafkaStreams(builder.build(), streamProperties);
        streams.start();
    }
}