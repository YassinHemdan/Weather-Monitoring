package org.example.adapter.inbond.resp;

import java.util.List;
import java.util.Objects;

public sealed interface RespValue {

    record SimpleString(String value) implements RespValue {
        public SimpleString {
            Objects.requireNonNull(value);
        }
    }

    record ErrorValue(String value) implements RespValue {
        public ErrorValue {
            Objects.requireNonNull(value);
        }
    }

    record IntegerValue(long value) implements RespValue{}

    final class BulkString implements RespValue {
        private final byte[] value;
        public BulkString(byte[] value) {
            this.value = Objects.requireNonNull(value).clone();
        }
        public byte[] bytes() {
            return value.clone();
        }
    }

    record ArrayValue(List<RespValue> elements) implements RespValue {
        public ArrayValue{
            elements = List.copyOf(elements);
        }
    }

    enum NullBulkString implements RespValue {
        INSTANCE
    }

    enum NullArray implements RespValue {
        INSTANCE
    }
}
