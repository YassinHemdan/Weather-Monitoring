
package org.example;

import org.example.config.ApplicationConfig;
import org.example.config.KafkaConfig;

import java.io.IOException;
import java.util.Properties;

public class Main {

    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException {
        Properties properties = KafkaConfig.loadProperties();
        ApplicationConfig application = ApplicationConfig.create(properties);

        LOG.log(System.Logger.Level.INFO, "CentralStation initialized on port " + application.port());

        Thread shutdownHook = new Thread(() -> {

            LOG.log(System.Logger.Level.INFO, "CentralStation shutdown requested");

            try {
                application.close();
            } catch (IOException e) {
                LOG.log(System.Logger.Level.ERROR, "Error while shutting down CentralStation", e);
            }

        }, "central-station-shutdown");

        boolean hookRegistered = false;

        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            hookRegistered = true;
            application.start();
        } finally {
            try {
                application.close();
            } finally {
                if (hookRegistered) {
                    try {
                        Runtime.getRuntime().removeShutdownHook(shutdownHook);
                    } catch (IllegalStateException ignored) {
                        // ignore
                    }
                }
            }
        }
    }

}
