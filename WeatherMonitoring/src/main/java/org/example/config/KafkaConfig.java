package org.example.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class KafkaConfig {
    public static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("src/main/resources/application.properties")){
            properties.load(fis);
        }
        return properties;
    }
}
