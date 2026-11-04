package org.example.adapter.inbound.resp;

import org.example.adapter.inbond.resp.RespHandler;
import org.example.adapter.inbond.resp.RespParser;
import org.example.adapter.inbond.resp.RespValue;
import org.example.adapter.inbond.tcp.TcpServer;
import org.example.application.command.CommandDispatcher;
import org.example.application.command.CommandHandler;
import org.example.application.command.handler.GetAllCommandHandler;
import org.example.application.command.handler.GetCommandHandler;
import org.example.application.port.out.KeyValueReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


class RespHandlerIntegrationTest {
    @Test
    void servesGetMissingGetAllAndMultipleRequestsOnOneConnection() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.get("42")).thenReturn(Optional.of("{\"station_id\":42}"));
        when(reader.get("missing")).thenReturn(Optional.empty());
        when(reader.getAll()).thenReturn(Map.of(
                "42", "{\"station_id\":42}",
                "57", "{\"station_id\":57}"));

        try (RunningServer running = startServer(reader)) {
            byte[] requests = concat(
                    encodeCommand("GET", "42"),
                    encodeCommand("GET", "missing"),
                    encodeCommand("GETALL"));
            byte[] responseBytes = sendAndReadAll(running.port(), requests);

            ByteArrayInputStream response = new ByteArrayInputStream(responseBytes);
            RespParser parser = new RespParser();

            assertEquals("{\"station_id\":42}", text(parser.parse(response)));
            assertSame(RespValue.NullBulkString.INSTANCE, parser.parse(response));

            RespValue.ArrayValue pairs = assertInstanceOf(
                    RespValue.ArrayValue.class, parser.parse(response));
            Map<String, String> actual = new HashMap<>();
            for (RespValue element : pairs.elements()) {
                RespValue.ArrayValue pair = assertInstanceOf(RespValue.ArrayValue.class, element);
                assertEquals(2, pair.elements().size());
                actual.put(text(pair.elements().get(0)), text(pair.elements().get(1)));
            }
            assertEquals(Map.of(
                    "42", "{\"station_id\":42}",
                    "57", "{\"station_id\":57}"), actual);
            assertNull(parser.parse(response));
        }

        verify(reader).get("42");
        verify(reader).get("missing");
        verify(reader).getAll();
        verifyNoMoreInteractions(reader);
    }

    @Test
    void reportsUnknownCommandAndContinuesWithNextRequest() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.get("42")).thenReturn(Optional.of("json"));

        try (RunningServer running = startServer(reader)) {
            byte[] responseBytes = sendAndReadAll(running.port(), concat(
                    encodeCommand("NOPE"),
                    encodeCommand("GET", "42")));

            ByteArrayInputStream response = new ByteArrayInputStream(responseBytes);
            RespParser parser = new RespParser();
            RespValue.ErrorValue error = assertInstanceOf(
                    RespValue.ErrorValue.class, parser.parse(response));
            assertTrue(error.value().contains("Unknown command"));
            assertEquals("json", text(parser.parse(response)));
            assertNull(parser.parse(response));
        }
    }

    @Test
    void malformedCommandArrayGetsErrorButConnectionCanContinue() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.get("42")).thenReturn(Optional.of("json"));

        byte[] malformedCommand = "*1\r\n+GET\r\n".getBytes(StandardCharsets.US_ASCII);
        try (RunningServer running = startServer(reader)) {
            byte[] responseBytes = sendAndReadAll(running.port(), concat(
                    malformedCommand,
                    encodeCommand("GET", "42")));

            ByteArrayInputStream response = new ByteArrayInputStream(responseBytes);
            RespParser parser = new RespParser();
            RespValue.ErrorValue error = assertInstanceOf(
                    RespValue.ErrorValue.class, parser.parse(response));
            assertTrue(error.value().contains("bulk strings"));
            assertEquals("json", text(parser.parse(response)));
        }
    }

    @Test
    void malformedRespFrameGetsProtocolErrorAndConnectionCloses() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);

        try (RunningServer running = startServer(reader)) {
            byte[] responseBytes = sendAndReadAll(running.port(),
                    "?not-resp\r\n".getBytes(StandardCharsets.US_ASCII));

            RespValue.ErrorValue error = assertInstanceOf(RespValue.ErrorValue.class,
                    new RespParser().parse(new ByteArrayInputStream(responseBytes)));
            assertTrue(error.value().contains("Protocol error"));
        }
        verifyNoInteractions(reader);
    }

    @Test
    void truncatedRespFrameGetsErrorAndConnectionCloses() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);

        try (RunningServer running = startServer(reader)) {
            byte[] responseBytes = sendAndReadAll(running.port(),
                    "$5\r\nab".getBytes(StandardCharsets.US_ASCII));

            RespValue.ErrorValue error = assertInstanceOf(RespValue.ErrorValue.class,
                    new RespParser().parse(new ByteArrayInputStream(responseBytes)));
            assertTrue(error.value().contains("incomplete RESP request"));
        }
        verifyNoInteractions(reader);
    }

    @Test
    void storageFailureReturnsGenericErrorInsteadOfLeakingDetails() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.get("42")).thenThrow(new IOException("/secret/path/active.data"));

        try (RunningServer running = startServer(reader)) {
            byte[] responseBytes = sendAndReadAll(running.port(), encodeCommand("GET", "42"));
            RespValue.ErrorValue error = assertInstanceOf(RespValue.ErrorValue.class,
                    new RespParser().parse(new ByteArrayInputStream(responseBytes)));
            assertTrue(error.value().contains("Internal server error"));
            assertFalse(error.value().contains("secret/path"));
        }
    }

    private static RunningServer startServer(KeyValueReader reader) throws IOException {
        List<CommandHandler> handlers = List.of(
                new GetCommandHandler(reader),
                new GetAllCommandHandler(reader));
        RespHandler handler = new RespHandler(new CommandDispatcher(handlers));
        TcpServer server = TcpServer.newTcpServer(
                0, 32, 8, Duration.ofSeconds(2), handler);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            try {
                server.serve();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return new RunningServer(server, executor, future);
    }

    private static byte[] sendAndReadAll(int port, byte[] request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(request);
            socket.getOutputStream().flush();
            socket.shutdownOutput();
            return socket.getInputStream().readAllBytes();
        }
    }

    private static byte[] encodeCommand(String... arguments) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeAscii(output, "*" + arguments.length + "\r\n");
        for (String argument : arguments) {
            byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
            writeAscii(output, "$" + bytes.length + "\r\n");
            output.write(bytes);
            writeAscii(output, "\r\n");
        }
        return output.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] concat(byte[]... arrays) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            output.write(array);
        }
        return output.toByteArray();
    }

    private static String text(RespValue value) {
        RespValue.BulkString bulk = assertInstanceOf(RespValue.BulkString.class, value);
        return new String(bulk.bytes(), StandardCharsets.UTF_8);
    }

    private static final class RunningServer implements AutoCloseable {
        private final TcpServer server;
        private final ExecutorService executor;
        private final Future<?> serving;

        private RunningServer(TcpServer server, ExecutorService executor, Future<?> serving) {
            this.server = server;
            this.executor = executor;
            this.serving = serving;
        }

        private int port() {
            return server.getPort();
        }

        @Override
        public void close() throws Exception {
            server.close();
            executor.shutdown();
            try {
                serving.get(3, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}