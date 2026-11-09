package org.example.adapter.outbound.bitcask.record;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordValueObjectsTest {

    @Test
    void encodedRecordShouldDefensivelyCopyInputArray() {
        byte[] original = {1, 2, 3};

        EncodedRecord encoded = new EncodedRecord(
                original,
                1,
                2
        );

        original[0] = 99;

        assertArrayEquals(
                new byte[]{1, 2, 3},
                encoded.data()
        );
    }

    @Test
    void encodedRecordShouldReturnDefensiveCopy() {
        EncodedRecord encoded = new EncodedRecord(
                new byte[]{1, 2, 3},
                1,
                2
        );

        byte[] returned = encoded.data();
        returned[0] = 99;

        assertArrayEquals(
                new byte[]{1, 2, 3},
                encoded.data()
        );
    }

    @Test
    void encodedRecordShouldRejectNegativeValueOffset() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EncodedRecord(new byte[10], -1, 2)
        );
    }

    @Test
    void encodedRecordShouldRejectNegativeValueLength() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EncodedRecord(new byte[10], 0, -1)
        );
    }

    @Test
    void encodedRecordShouldRejectValueOutsideRecord() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EncodedRecord(new byte[5], 4, 2)
        );
    }

    @Test
    void recordMetadataShouldRejectNegativeOffsets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RecordMetadata("key", 1, -1, 2, 10)
        );
    }

    @Test
    void recordMetadataShouldRejectNegativeLengths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RecordMetadata("key", 1, 1, -2, 10)
        );
    }

    @Test
    void recordMetadataShouldRejectValueOutsideRecord() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RecordMetadata("key", 1, 9, 2, 10)
        );
    }
}