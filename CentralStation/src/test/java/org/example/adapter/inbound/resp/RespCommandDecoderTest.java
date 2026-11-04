package org.example.adapter.inbound.resp;

import org.example.adapter.inbond.resp.RespCommandDecoder;
import org.example.adapter.inbond.resp.RespValue;
import org.example.application.command.Command;
import org.example.application.command.InvalidCommandException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespCommandDecoderTest {
    private final RespCommandDecoder decoder = new RespCommandDecoder();

    @Test
    void decodesCommandAndArgumentsFromBulkStringArray() throws Exception {
        Command command = decoder.decode(new RespValue.ArrayValue(List.of(
                bulk("GET"), bulk("station-42"))));

        assertEquals("GET", command.name());
        assertEquals(List.of("station-42"), command.arguments());
    }

    @Test
    void preservesUtf8Text() throws Exception {
        Command command = decoder.decode(new RespValue.ArrayValue(List.of(
                bulk("GET"), bulk("مصر"))));

        assertEquals(List.of("مصر"), command.arguments());
    }

    @Test
    void rejectsNonArrayRequest() {
        assertThrows(InvalidCommandException.class,
                () -> decoder.decode(new RespValue.SimpleString("GET")));
    }

    @Test
    void rejectsEmptyArray() {
        assertThrows(InvalidCommandException.class,
                () -> decoder.decode(new RespValue.ArrayValue(List.of())));
    }

    @Test
    void rejectsNullArray() {
        assertThrows(InvalidCommandException.class,
                () -> decoder.decode(RespValue.NullArray.INSTANCE));
    }

    @Test
    void rejectsNonBulkArrayElements() {
        assertThrows(InvalidCommandException.class,
                () -> decoder.decode(new RespValue.ArrayValue(List.of(
                        new RespValue.SimpleString("GET")))));
    }

    @Test
    void rejectsNullBulkStringElements() {
        assertThrows(InvalidCommandException.class,
                () -> decoder.decode(new RespValue.ArrayValue(List.of(
                        RespValue.NullBulkString.INSTANCE))));
    }

    @Test
    void rejectsEmptyCommandName() {
        assertThrows(InvalidCommandException.class,
                () -> decoder.decode(new RespValue.ArrayValue(List.of(bulk("")))));
    }

    @Test
    void rejectsMalformedUtf8() {
        RespValue.BulkString invalidUtf8 = new RespValue.BulkString(new byte[]{(byte) 0xC3});
        assertThrows(InvalidCommandException.class,
                () -> decoder.decode(new RespValue.ArrayValue(List.of(invalidUtf8))));
    }

    private static RespValue.BulkString bulk(String value) {
        return new RespValue.BulkString(value.getBytes(StandardCharsets.UTF_8));
    }
}