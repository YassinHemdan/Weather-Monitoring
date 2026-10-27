package org.example.adapter.inbound.resp;

import org.example.adapter.inbond.resp.RespLimits;
import org.example.adapter.inbond.resp.RespParser;
import org.example.adapter.inbond.resp.RespProtocolException;
import org.example.adapter.inbond.resp.RespValue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespParserTest {
    private final RespParser parser = new RespParser();

    @Test
    void parsesSimpleStringErrorAndInteger() throws Exception {
        assertEquals(new RespValue.SimpleString("OK"), parse("+OK\r\n"));
        assertEquals(new RespValue.ErrorValue("ERR bad request"), parse("-ERR bad request\r\n"));
        assertEquals(new RespValue.IntegerValue(-42), parse(":-42\r\n"));
    }

    @Test
    void parsesUtf8BulkStringUsingByteLength() throws Exception {
        RespValue.BulkString value = assertInstanceOf(
                RespValue.BulkString.class, parse("$2\r\né\r\n"));
        assertEquals("é", new String(value.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void bulkStringCanContainCrLfBytes() throws Exception {
        byte[] input = "$4\r\na\r\nb\r\n".getBytes(StandardCharsets.US_ASCII);
        RespValue.BulkString value = assertInstanceOf(
                RespValue.BulkString.class, parser.parse(new ByteArrayInputStream(input)));
        assertArrayEquals(new byte[]{'a', '\r', '\n', 'b'}, value.bytes());
    }

    @Test
    void parsesNullBulkStringAndNullArray() throws Exception {
        assertSame(RespValue.NullBulkString.INSTANCE, parse("$-1\r\n"));
        assertSame(RespValue.NullArray.INSTANCE, parse("*-1\r\n"));
    }

    @Test
    void parsesNestedAndEmptyArrays() throws Exception {
        RespValue.ArrayValue array = assertInstanceOf(RespValue.ArrayValue.class,
                parse("*2\r\n$3\r\nGET\r\n*1\r\n:7\r\n"));
        assertEquals(2, array.elements().size());
        assertEquals("GET", text(array.elements().getFirst()));
        RespValue.ArrayValue nested = assertInstanceOf(
                RespValue.ArrayValue.class, array.elements().get(1));
        assertEquals(List.of(new RespValue.IntegerValue(7)), nested.elements());

        RespValue.ArrayValue empty = assertInstanceOf(RespValue.ArrayValue.class, parse("*0\r\n"));
        assertTrue(empty.elements().isEmpty());
    }

    @Test
    void returnsNullOnCleanEofBetweenValues() throws Exception {
        assertNull(parser.parse(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void rejectsUnsupportedPrefix() {
        assertThrows(RespProtocolException.class, () -> parse("?hello\r\n"));
    }

    @Test
    void rejectsMalformedNumericHeader() {
        assertThrows(RespProtocolException.class, () -> parse(":12x\r\n"));
        assertThrows(RespProtocolException.class, () -> parse(":\r\n"));
    }

    @Test
    void rejectsLineEndingWithoutCr() {
        assertThrows(RespProtocolException.class, () -> parse("+hello\n"));
    }

    @Test
    void reportsTruncatedBulkString() {
        assertThrows(EOFException.class, () -> parse("$4\r\nxy"));
    }

    @Test
    void rejectsOversizedBulkString() {
        RespParser limited = new RespParser(new RespLimits(32, 4, 3, 4, 128));
        assertThrows(RespProtocolException.class,
                () -> limited.parse(bytes("$4\r\nfour\r\n")));
    }

    @Test
    void rejectsTooManyArrayElements() {
        RespParser limited = new RespParser(new RespLimits(32, 1, 10, 4, 128));
        assertThrows(RespProtocolException.class,
                () -> limited.parse(bytes("*2\r\n")));
    }

    @Test
    void rejectsValueThatExceedsAggregateByteBudget() {
        RespParser limited = new RespParser(new RespLimits(32, 4, 10, 4, 4));
        assertThrows(RespProtocolException.class,
                () -> limited.parse(bytes("+abc\r\n")));
    }

    @Test
    void rejectsInvalidLimitsAndNullLimitConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new RespLimits(0, 1, 1, 1, 1));
        assertThrows(NullPointerException.class, () -> new RespParser(null));
    }

    private RespValue parse(String input) throws IOException {
        return parser.parse(bytes(input));
    }

    private static ByteArrayInputStream bytes(String input) {
        return new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(RespValue value) {
        RespValue.BulkString bulk = assertInstanceOf(RespValue.BulkString.class, value);
        return new String(bulk.bytes(), StandardCharsets.UTF_8);
    }
}