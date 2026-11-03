package org.example.resp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CentralStationConnection implements AutoCloseable{
    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;

    private CentralStationConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
    }

    public static CentralStationConnection connect(
            String host,
            int port,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) throws IOException {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalArgumentException("Connection and read timeouts must be positive");
        }

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            socket.setTcpNoDelay(true);

            return new CentralStationConnection(socket);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }

            throw failure;
        }
    }

    public Optional<String> get(String key) throws IOException {
        Objects.requireNonNull(key, "key");
        RespWriter.writeCommand(output, "GET", key);
        RespValue response = RespParser.parse(input);

        if (response == RespValue.NullBulkString.INSTANCE) {
            return Optional.empty();
        }
        if (response instanceof RespValue.BulkString bulkString) {
            return Optional.of(decodeUtf8(bulkString.bytes(), "GET value"));
        }

        throw unexpectedResponse("GET", response);
    }

    public Map<String, String> getAll() throws IOException {
        RespWriter.writeCommand(output, "GETALL");
        RespValue response = RespParser.parse(input);

        if (!(response instanceof RespValue.ArrayValue array)) {
            throw unexpectedResponse("GETALL", response);
        }

        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < array.elements().size(); i++) {
            RespValue element = array.elements().get(i);

            if (!(element instanceof RespValue.ArrayValue keyValuePair) || keyValuePair.elements().size() != 2) {
                throw new RespProtocolException(
                        "GETALL response element " + i + " must be an array containing exactly two bulk strings");
            }

            if (!(keyValuePair.elements().get(0) instanceof RespValue.BulkString key)
                    || !(keyValuePair.elements().get(1) instanceof RespValue.BulkString value)) {
                throw new RespProtocolException(
                        "GETALL response element " + i + " must contain non-null bulk strings");
            }

            String decodedKey = decodeUtf8(key.bytes(), "GETALL key");
            String decodedValue = decodeUtf8(value.bytes(), "GETALL value");

            // We should already have a unique array of <key, value>
            if (values.putIfAbsent(decodedKey, decodedValue) != null) {
                throw new RespProtocolException("GETALL response contains duplicate key: " + decodedKey);
            }
        }

        return Collections.unmodifiableMap(values);
    }

    private IOException unexpectedResponse(String command, RespValue response) {
        if (response instanceof RespValue.ErrorValue error) {
            return new IOException("CentralStation rejected " + command + ": " + error.value());
        }
        String type = response == RespValue.NullArray.INSTANCE ? "null array"
                : response == RespValue.NullBulkString.INSTANCE ? "null bulk string"
                : response.getClass().getSimpleName();
        return new RespProtocolException("Unexpected " + type + " response to " + command);
    }

    private static String decodeUtf8(byte[] bytes, String field) throws RespProtocolException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new RespProtocolException(field + " is not valid UTF-8", exception);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
