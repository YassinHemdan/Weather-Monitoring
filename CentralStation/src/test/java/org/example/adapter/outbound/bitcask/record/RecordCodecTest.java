package org.example.adapter.outbound.bitcask.record;

import org.example.adapter.outbound.bitcask.Entry;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RecordCodecTest {

    private final RecordCodec codec = new RecordCodec();

    @Test
    void encodeShouldPreserveRecordInformation() throws Exception {
        Entry entry = new Entry(123L, "météo", "☀️ Sunny");

        EncodedRecord encoded = codec.encode(entry);
        byte[] data = encoded.data();

        ByteBuffer buffer = ByteBuffer.wrap(data);

        long timestamp = buffer.getLong();
        int keyLength = buffer.getInt();
        int valueLength = buffer.getInt();

        byte[] keyBytes = new byte[keyLength];
        byte[] valueBytes = new byte[valueLength];

        buffer.get(keyBytes);
        buffer.get(valueBytes);

        assertEquals(123L, timestamp);

        assertEquals(
                "météo",
                new String(keyBytes, StandardCharsets.UTF_8)
        );

        assertEquals(
                "☀️ Sunny",
                new String(valueBytes, StandardCharsets.UTF_8)
        );

        assertEquals(
                codec.headerSize() + keyLength,
                encoded.valueOffset()
        );

        assertEquals(valueLength, encoded.valueLength());

        assertEquals(data.length, codec.decodeHeader(
                Arrays.copyOf(data, codec.headerSize())
        ));
    }

    @Test
    void decodeMetadataShouldExtractCorrectInformation() throws Exception {
        Entry entry = new Entry(456L, "station-1", "25.5");

        EncodedRecord encoded = codec.encode(entry);

        RecordMetadata metadata = codec.decodeMetadata(encoded.data());

        assertEquals("station-1", metadata.key());
        assertEquals(456L, metadata.timestamp());
        assertEquals(encoded.valueOffset(), metadata.valueOffset());
        assertEquals(encoded.valueLength(), metadata.valueLength());
        assertEquals(encoded.data().length, metadata.recordLength());
    }

    @Test
    void decodeValueShouldDecodeUtf8Correctly() throws Exception {
        String expected = "Temperature: 25.5°C";

        String actual = codec.decodeValue(
                expected.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(expected, actual);
    }

    @Test
    void decodeHeaderShouldRejectIncorrectHeaderSize() {
        byte[] invalidHeader = new byte[codec.headerSize() - 1];

        assertThrows(
                CorruptRecordException.class,
                () -> codec.decodeHeader(invalidHeader)
        );
    }

    @Test
    void decodeHeaderShouldRejectNegativeKeyLength() {
        byte[] header = ByteBuffer.allocate(codec.headerSize())
                .putLong(1L)
                .putInt(-1)
                .putInt(0)
                .array();

        assertThrows(
                CorruptRecordException.class,
                () -> codec.decodeHeader(header)
        );
    }

    @Test
    void decodeHeaderShouldRejectNegativeValueLength() {
        byte[] header = ByteBuffer.allocate(codec.headerSize())
                .putLong(1L)
                .putInt(0)
                .putInt(-1)
                .array();

        assertThrows(
                CorruptRecordException.class,
                () -> codec.decodeHeader(header)
        );
    }

    @Test
    void decodeHeaderShouldRejectRecordLengthOverflow() {
        byte[] header = ByteBuffer.allocate(codec.headerSize())
                .putLong(1L)
                .putInt(Integer.MAX_VALUE)
                .putInt(1)
                .array();

        assertThrows(
                CorruptRecordException.class,
                () -> codec.decodeHeader(header)
        );
    }

    @Test
    void decodeMetadataShouldRejectIncompleteRecord() {
        byte[] record = codec.encode(
                new Entry(1L, "key", "value")
        ).data();

        byte[] incomplete = Arrays.copyOf(
                record,
                record.length - 1
        );

        assertThrows(
                CorruptRecordException.class,
                () -> codec.decodeMetadata(incomplete)
        );
    }

    @Test
    void decodeMetadataShouldRejectRecordWithExtraBytes() {
        byte[] record = codec.encode(
                new Entry(1L, "key", "value")
        ).data();

        byte[] invalidRecord = Arrays.copyOf(
                record,
                record.length + 1
        );

        assertThrows(
                CorruptRecordException.class,
                () -> codec.decodeMetadata(invalidRecord)
        );
    }

    @Test
    void decodeMetadataShouldRejectInvalidUtf8Key() {
        byte[] record = rawRecord(
                new byte[]{(byte) 0xC3},
                new byte[0]
        );

        assertThrows(
                CorruptRecordException.class,
                () -> codec.decodeMetadata(record)
        );
    }

    @Test
    void decodeMetadataShouldRejectInvalidUtf8Value() {
        byte[] record = rawRecord(
                "key".getBytes(StandardCharsets.UTF_8),
                new byte[]{(byte) 0xC3}
        );

        assertThrows(
                CorruptRecordException.class,
                () -> codec.decodeMetadata(record)
        );
    }

    @Test
    void decodeValueShouldRejectInvalidUtf8() {
        byte[] invalidUtf8 = {(byte) 0xC3};

        assertThrows(
                CorruptRecordException.class,
                () -> codec.decodeValue(invalidUtf8)
        );
    }

    private byte[] rawRecord(byte[] key, byte[] value) {
        return ByteBuffer.allocate(
                        codec.headerSize() + key.length + value.length
                )
                .putLong(1L)
                .putInt(key.length)
                .putInt(value.length)
                .put(key)
                .put(value)
                .array();
    }
}