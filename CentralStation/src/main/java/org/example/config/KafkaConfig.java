
package org.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class KafkaConfig {

    private KafkaConfig() {
    }

    public static Properties loadProperties() throws IOException {
        Properties properties = new Properties();

        try (InputStream input = KafkaConfig.class.getResourceAsStream("/application.properties")) {
            if (input == null)
                throw new IOException("application.properties was not found on the classpath");

            properties.load(input);
        }

        return properties;
    }

    public static boolean isKafkaEnabled(Properties properties) {
        String value = properties.getProperty("kafka.enabled", "false").trim();

        if (value.equalsIgnoreCase("true")) {
            return true;
        }

        if (value.equalsIgnoreCase("false")) {
            return false;
        }

        throw new IllegalArgumentException("kafka.enabled must be either true or false");
    }

    public static String getKafkaTopic(Properties properties) {
        String topic = properties.getProperty("kafka.topic", "").trim();

        if (topic.isEmpty())
            throw new IllegalArgumentException("kafka.topic must not be empty when Kafka is enabled");

        return topic;
    }

    public static Properties consumerProperties(Properties applicationProperties) {

        Properties consumerProperties = new Properties();

        for (String name : applicationProperties.stringPropertyNames()) {
            if (name.startsWith("kafka.") || name.startsWith("tcp.") || name.startsWith("bitcask."))
                continue;

            consumerProperties.setProperty(name, applicationProperties.getProperty(name));
        }

        requireProperty(consumerProperties, "bootstrap.servers");
        requireProperty(consumerProperties, "group.id");
        requireProperty(consumerProperties, "key.deserializer");
        requireProperty(consumerProperties, "value.deserializer");

        consumerProperties.setProperty("enable.auto.commit", "false");

        return consumerProperties;
    }

    private static void requireProperty(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing required Kafka property: " + name);
    }
}
