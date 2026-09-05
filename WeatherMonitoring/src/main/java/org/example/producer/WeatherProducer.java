package org.example.producer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.example.model.WeatherEvent;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class WeatherProducer implements MessageProducer<WeatherEvent>{
    private final KafkaProducer<String, WeatherEvent> kafkaProducer;
    private final String kafkaTopic;

    public WeatherProducer(Properties properties, String topic) {
        this.kafkaTopic = topic;
        this.kafkaProducer = new KafkaProducer<>(properties);
    }
    @Override
    public void send(WeatherEvent message) throws ExecutionException, InterruptedException {
        ProducerRecord<String, WeatherEvent> record = new ProducerRecord<>(kafkaTopic, message);

        kafkaProducer.send(record, (metadata, exception) ->{
           if(exception != null) {
               System.err.println("Error while sending message: " + exception.getMessage());
           }
        }).get();
        kafkaProducer.flush();
    }

    @Override
    public void close() {
        kafkaProducer.close();
    }
}
