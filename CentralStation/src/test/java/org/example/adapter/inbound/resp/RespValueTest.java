package org.example.adapter.inbound.resp;

import org.example.adapter.inbond.resp.RespValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespValueTest {
    @Test
    void bulkStringCopiesInputAndOutputBytes() {
        byte[] original = new byte[]{1, 2, 3};
        RespValue.BulkString bulk = new RespValue.BulkString(original);
        original[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, bulk.bytes());

        byte[] returned = bulk.bytes();
        returned[1] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, bulk.bytes());
    }

    @Test
    void arrayValueCopiesItsElements() {
        List<RespValue> source = new ArrayList<>();
        source.add(new RespValue.IntegerValue(1));
        RespValue.ArrayValue array = new RespValue.ArrayValue(source);
        source.add(new RespValue.IntegerValue(2));

        assertEquals(1, array.elements().size());
        assertThrows(UnsupportedOperationException.class,
                () -> array.elements().add(new RespValue.IntegerValue(3)));
    }
}