package org.example.adapter.outbound.bitcask.record;

import org.example.adapter.outbound.bitcask.Entry;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class RecordCodec {
    private record ParsedRecord(
            String key,
            String value,
            long timestamp,
            int valueOffset,
            int valueLength,
            int recordLength
    ){}

    private static final int HEADER_SIZE = Long.BYTES + Integer.BYTES * 2;

    public EncodedRecord encode(Entry entry) {
        //     LONG      INT       INT
        // <timestamp><keySize><valueSize><key><value>
        Objects.requireNonNull(entry, "entry");

        byte[] keyBytes = entry.key().getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = entry.value().getBytes(StandardCharsets.UTF_8);

        long totalLength = (long) HEADER_SIZE + keyBytes.length + valueBytes.length;

        if (totalLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Serialized record is too large");
        }

        int recordLength = (int) totalLength;
        byte[] bytes = ByteBuffer.allocate(recordLength)
                .putLong(entry.timestamp())
                .putInt(keyBytes.length)
                .putInt(valueBytes.length)
                .put(keyBytes)
                .put(valueBytes)
                .array();

        int valueOffset = HEADER_SIZE + keyBytes.length;
        return new EncodedRecord(bytes, valueOffset, valueBytes.length);
    }

//    public Entry decode(byte[] recordBytes) throws CorruptRecordException {
//        ParsedRecord parsedRecord = parseRecord(recordBytes);
//        return new Entry(
//                parsedRecord.timestamp,
//                parsedRecord.key,
//                parsedRecord.value
//        );
//    }

    public int headerSize() {
        return HEADER_SIZE;
    }

    // It validates the headerLength and returns the expected recordLength
    // notice that we use the CorruptRecordException for only the retrieved
    public int decodeHeader(byte[] headerBytes) throws CorruptRecordException {
        Objects.requireNonNull(headerBytes);

        if (headerBytes.length != HEADER_SIZE) {
            throw new CorruptRecordException(
                    "Invalid record header size. Expected "
                    + HEADER_SIZE + " bytes but found "
                    + headerBytes.length
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);

        buffer.getLong(); // Ignore it. We only need the key and value to determine the record length.
        int keyLength = buffer.getInt();
        int valueLength = buffer.getInt();

        if (keyLength < 0 || valueLength < 0) {
            throw new CorruptRecordException("Negative key or value length");
        }

        long expectedRecordLength = (long) HEADER_SIZE + keyLength + valueLength;
        if (expectedRecordLength > Integer.MAX_VALUE) {
            throw new CorruptRecordException("Record length is too large");
        }

        return (int)expectedRecordLength;
    }

    public RecordMetadata decodeMetadata(byte[] recordBytes) throws CorruptRecordException {
        ParsedRecord parsed = parseRecord(recordBytes);
        return new RecordMetadata(
                parsed.key(),
                parsed.timestamp(),
                parsed.valueOffset(),
                parsed.valueLength(),
                parsed.recordLength()
        );
    }

    public String decodeValue(byte[] valueBytes) throws CorruptRecordException {
        Objects.requireNonNull(valueBytes, "valueBytes");
        return decodeUtf8(valueBytes, "value");
    }

    private ParsedRecord parseRecord(byte[] recordBytes) throws CorruptRecordException {
        Objects.requireNonNull(recordBytes, "recordBytes");

        if (recordBytes.length < HEADER_SIZE) {
            throw new CorruptRecordException("Record smaller than header size");
        }

        byte[] headerBytes = new byte[HEADER_SIZE];
        System.arraycopy(recordBytes, 0, headerBytes, 0, HEADER_SIZE);
        int expectedRecordLength = decodeHeader(headerBytes);

        if (expectedRecordLength != recordBytes.length) {
            throw new CorruptRecordException(
                    "Expected record with size "
                    + expectedRecordLength
                    + " but found "
                    + recordBytes.length
            );
        }


        ByteBuffer buffer = ByteBuffer.wrap(recordBytes);

        long timestamp = buffer.getLong();

        int keyLength = buffer.getInt();
        int valueLength = buffer.getInt();

        int valueOffset = HEADER_SIZE + keyLength;

        byte[] keyBytes = new byte[keyLength];
        byte[] valueBytes = new byte[valueLength];

        buffer.get(keyBytes);
        buffer.get(valueBytes);

        String key = decodeUtf8(keyBytes, "key");
        String value = decodeUtf8(valueBytes, "value");

        return new ParsedRecord(
                key,
                value,
                timestamp,
                valueOffset,
                valueLength,
                recordBytes.length
        );
    }

    private String decodeUtf8(byte[] bytes, String fieldName) throws CorruptRecordException {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new CorruptRecordException(
                    "Invalid UTF-8 encoding in record " + fieldName,
                    e
            );
        }
    }
}
