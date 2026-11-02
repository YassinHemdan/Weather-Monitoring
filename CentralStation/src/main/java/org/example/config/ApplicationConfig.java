
package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.example.adapter.inbond.kafka.consumer.WeatherConsumer;
import org.example.adapter.inbond.resp.RespHandler;
import org.example.adapter.inbond.tcp.TcpServer;
import org.example.adapter.outbound.bitcask.BitcaskStore;
import org.example.adapter.outbound.bitcask.Database;

import org.example.application.command.CommandDispatcher;
import org.example.application.command.CommandHandler;
import org.example.application.command.handler.GetAllCommandHandler;
import org.example.application.command.handler.GetCommandHandler;
import org.example.application.ingestion.WeatherEventJsonCodec;
import org.example.application.ingestion.WeatherLoggingService;
import org.example.application.port.out.KeyValueReader;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public final class ApplicationConfig implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ApplicationConfig.class.getName());

    private final Database database;
    private final TcpServer tcpServer;
    private final WeatherConsumer weatherConsumer;

    private final Object lifecycleLock = new Object();

    private volatile boolean started;
    private volatile boolean closed;

    private volatile Thread kafkaThread;

    private ApplicationConfig(
            Database database,
            TcpServer tcpServer,
            WeatherConsumer weatherConsumer) {

        this.database = database;
        this.tcpServer = tcpServer;
        this.weatherConsumer = weatherConsumer;
    }

    public static ApplicationConfig create(Properties properties) throws IOException {

        Objects.requireNonNull(properties, "properties");
        Database database = prepareDatabase(properties);

        try {
            BitcaskStore store = new BitcaskStore(database);

            ObjectMapper objectMapper = new ObjectMapper();

            WeatherEventJsonCodec jsonCodec = new WeatherEventJsonCodec(objectMapper);

            WeatherLoggingService loggingService = new WeatherLoggingService(store, jsonCodec);

            WeatherConsumer weatherConsumer = prepareKafka(properties, loggingService);

            CommandDispatcher dispatcher = prepareCmdDispatcher(store);

            TcpServer server = prepareTcpServer(properties, dispatcher);

            return new ApplicationConfig(database, server, weatherConsumer);

        } catch (IOException | RuntimeException | Error failure) {

            try {
                database.close();

            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }

            throw failure;
        }
    }

    public void start() throws IOException {

        synchronized (lifecycleLock) {
            if (closed)
                throw new IllegalStateException("Application has already been closed");

            if (started)
                throw new IllegalStateException("Application has already been started");

            started = true;

            if (weatherConsumer != null) {
                kafkaThread = new Thread(this::runKafkaConsumer, "central-station-kafka-consumer");
                kafkaThread.start();
            }
        }

        try {
            tcpServer.serve();
        } catch (IllegalStateException e) {
            if (closed)
                return;

            closeAfterFailure(e);
            throw e;
        } catch (IOException | RuntimeException | Error failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    private void runKafkaConsumer() {

        try {
            LOG.log(System.Logger.Level.INFO, "Starting Kafka consumer");

            weatherConsumer.consume();

            LOG.log(System.Logger.Level.INFO, "Kafka consumer stopped");

        } catch (IOException | RuntimeException e) {
            if (!closed)
                LOG.log(System.Logger.Level.ERROR, "Kafka consumer terminated unexpectedly", e);
        }
    }

    @Override
    public synchronized void close() throws IOException {

        synchronized (lifecycleLock) {
            if (closed)
                return;
            closed = true;
        }

        IOException failure = null;
        try {
            tcpServer.close();
        } catch (IOException e) {
            failure = e;
        }
        if (weatherConsumer != null) {
            weatherConsumer.close();
        }

        boolean interrupted = joinKafkaThread();
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        try {
            database.close();
        } catch (IOException e) {

            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private boolean joinKafkaThread() {
        Thread thread = kafkaThread;

        if (thread == null || thread == Thread.currentThread())
            return false;

        boolean interrupted = false;

        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    private void closeAfterFailure(Throwable failure) {
        try {
            close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static Database prepareDatabase(Properties properties) throws IOException {
        String dataDirectory = property(properties, "bitcask.data-dir", "./data/bitcask");
        return new Database(dataDirectory);
    }

    // I think that I need to make it more general ?
    // Maybe I should Implement an interface or something or use some kind of factory pattern
    private static WeatherConsumer prepareKafka(Properties properties, WeatherLoggingService loggingService) {
        WeatherConsumer consumer = null;
        if (KafkaConfig.isKafkaEnabled(properties)) {
            LOG.log(System.Logger.Level.INFO, "Kafka ingestion is enabled");

            String topic = KafkaConfig.getKafkaTopic(properties);
            Properties kafkaProperties = KafkaConfig.consumerProperties(properties);

            consumer = new WeatherConsumer(kafkaProperties, topic, loggingService);

        } else
            LOG.log(System.Logger.Level.INFO, "Kafka ingestion is disabled");

        return consumer;
    }

    private static CommandDispatcher prepareCmdDispatcher(KeyValueReader store) {
        List<CommandHandler> handlers = List.of(new GetCommandHandler(store), new GetAllCommandHandler(store));
        return new CommandDispatcher(handlers);
    }

    private static TcpServer prepareTcpServer(Properties properties, CommandDispatcher dispatcher) throws IOException {
        int port = integerProperty(
                properties,
                "tcp.port",
                6380);

        int backlog = integerProperty(
                properties,
                "tcp.backlog",
                128);

        int maxClients = integerProperty(
                properties,
                "tcp.max-concurrent-clients",
                128);

        long shutdownSeconds = longProperty(
                properties,
                "tcp.graceful-shutdown-seconds",
                5L);

        if (shutdownSeconds < 0) {
            throw new IllegalArgumentException(
                    "tcp.graceful-shutdown-seconds must not be negative");
        }

        return TcpServer.newTcpServer(
                port,
                backlog,
                maxClients,
                Duration.ofSeconds(shutdownSeconds),
                new RespHandler(dispatcher)
        );
    }

    public int port() {
        return tcpServer.getPort();
    }

    private static String property(Properties properties, String name, String fallback) {
        String value = properties.getProperty(name, fallback).trim();

        if (value.isEmpty())
            throw new IllegalArgumentException(name + " must not be empty");

        return value;
    }

    private static int integerProperty(Properties properties, String name, int fallback) {
        try {
            return Integer.parseInt(property(properties, name, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer", e);
        }
    }

    private static long longProperty(Properties properties, String name, long fallback) {
        try {
            return Long.parseLong(property(properties, name, Long.toString(fallback)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a long integer", e);
        }
    }
}
