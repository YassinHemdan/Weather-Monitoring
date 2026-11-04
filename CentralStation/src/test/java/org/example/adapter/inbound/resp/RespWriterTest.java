package org.example.adapter.inbound.resp;

import org.example.adapter.inbond.resp.RespParser;
import org.example.adapter.inbond.resp.RespValue;
import org.example.adapter.inbond.resp.RespWriter;
import org.example.application.command.CommandResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RespWriterTest {
    private final RespWriter writer = new RespWriter();

    @Test
    void writesSimpleStringIntegerAndErrors() throws Exception {
        assertEquals("+OK\r\n", write(new RespValue.SimpleString("OK")));
        assertEquals(":-12\r\n", write(new RespValue.IntegerValue(-12)));
        assertEquals("-ERR bad request\r\n", write(new RespValue.ErrorValue("ERR bad request")));
    }

    @Test
    void writesBulkStringUsingUtf8ByteLength() throws Exception {
        assertEquals("$2\r\né\r\n", write(new RespValue.BulkString("é".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void writesNullValuesAndEmptyArray() throws Exception {
        assertEquals("$-1\r\n", write(RespValue.NullBulkString.INSTANCE));
        assertEquals("*-1\r\n", write(RespValue.NullArray.INSTANCE));
        assertEquals("*0\r\n", write(new RespValue.ArrayValue(List.of())));
    }

    @Test
    void writesNestedArray() throws Exception {
        RespValue nested = new RespValue.ArrayValue(List.of(
                new RespValue.BulkString("key".getBytes(StandardCharsets.UTF_8)),
                new RespValue.BulkString("value".getBytes(StandardCharsets.UTF_8))));
        assertEquals("*1\r\n*2\r\n$3\r\nkey\r\n$5\r\nvalue\r\n",
                write(new RespValue.ArrayValue(List.of(nested))));
    }

    @Test
    void encodesPresentAndMissingGetResults() throws Exception {
        assertEquals("$5\r\nhello\r\n", writeResult(new CommandResult.Value(Optional.of("hello"))));
        assertEquals("$-1\r\n", writeResult(new CommandResult.Value(Optional.empty())));
    }

    @Test
    void encodesGetAllAsPairsAndPreservesEveryEntry() throws Exception {
        Map<String, String> values = Map.of("42", "json-42", "57", "json-57");
        byte[] encoded = writeResultBytes(new CommandResult.Values(values));
        RespValue.ArrayValue response = assertInstanceOf(RespValue.ArrayValue.class,
                new RespParser().parse(new ByteArrayInputStream(encoded)));

        Map<String, String> actual = new HashMap<>();
        for (RespValue item : response.elements()) {
            RespValue.ArrayValue pair = assertInstanceOf(RespValue.ArrayValue.class, item);
            assertEquals(2, pair.elements().size());
            actual.put(text(pair.elements().get(0)), text(pair.elements().get(1)));
        }
        assertEquals(values, actual);
    }

    @Test
    void writeErrorAddsErrPrefixAndSanitizesLineBreaks() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.writeError("bad\r\nrequest", output);
        assertEquals("-ERR bad  request\r\n", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsCrLfInsideSimpleStringOrRawError() {
        assertThrows(IllegalArgumentException.class,
                () -> write(new RespValue.SimpleString("first\nsecond")));
        assertThrows(IllegalArgumentException.class,
                () -> write(new RespValue.ErrorValue("ERR\rINJECTED")));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> writer.write(null, new ByteArrayOutputStream()));
        assertThrows(NullPointerException.class,
                () -> writer.write(new RespValue.SimpleString("OK"), null));
        assertThrows(NullPointerException.class,
                () -> writer.writeResult(null, new ByteArrayOutputStream()));
    }

    private String write(RespValue value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(value, output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private String writeResult(CommandResult result) throws Exception {
        return new String(writeResultBytes(result), StandardCharsets.UTF_8);
    }

    private byte[] writeResultBytes(CommandResult result) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.writeResult(result, output);
        return output.toByteArray();
    }

    private static String text(RespValue value) {
        RespValue.BulkString bulk = assertInstanceOf(RespValue.BulkString.class, value);
        return new String(bulk.bytes(), StandardCharsets.UTF_8);
    }
}