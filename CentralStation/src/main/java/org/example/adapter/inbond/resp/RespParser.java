package org.example.adapter.inbond.resp;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RespParser {
    private final RespLimits respLimits;
    public RespParser() {
        respLimits = RespLimits.DEFAULT;
    }
    public RespParser(RespLimits respLimits) {
        this.respLimits = Objects.requireNonNull(respLimits, "respLimits");
    }

    public RespValue parse(InputStream input) throws IOException {
        Objects.requireNonNull(input);
        int prefix = input.read();

        if(prefix == -1)
            return null;

        Budget budget = new Budget(respLimits.maxValueBytes());
        budget.consume(1);

        return readValue(input, budget, prefix, 0);
    }

    private RespValue readValue(InputStream input, Budget budget, int prefix, int depth) throws IOException {
        if (depth > respLimits.maxArrDepth())
            throw new RespProtocolException("Maximum nesting exceeded");

        return switch (prefix) {
            case '+' -> new RespValue.SimpleString(new String(readLine(input, budget), StandardCharsets.UTF_8));
            case '-' -> new RespValue.ErrorValue(new String(readLine(input, budget), StandardCharsets.UTF_8));
            case ':' -> new RespValue.IntegerValue(readNumber(input, budget));
            case '$' -> readBulk(input, budget);
            case '*' -> readArr(input, budget, depth);
            default -> throw new RespProtocolException("Unsupported RESP prefix: " + prefix);
        };
    }
    private RespValue readArr(InputStream input, Budget budget, int depth) throws IOException {
        long arrSize = readNumber(input, budget);

        if (arrSize == -1)
            return RespValue.NullArray.INSTANCE;
        if (arrSize < 0)
            throw new RespProtocolException("Expected +ve size of array");
        if (arrSize > respLimits.maxArrElements())
            throw new RespProtocolException("Exessive array length: " + arrSize);

        List<RespValue> elements = new ArrayList<>();

        for (int i=0 ; i<arrSize ; ++i)
            elements.add(readValue(input, budget, readByte(input, budget), depth + 1));

        return new RespValue.ArrayValue(elements);
    }
    private RespValue readBulk(InputStream input, Budget budget) throws IOException {
        long length = readNumber(input, budget);

        if (length == -1)
            return RespValue.NullBulkString.INSTANCE;
        if (length < 0)
            throw new RespProtocolException("Expected +ve length size");
        if(length > respLimits.maxBulkBytes())
            throw new RespProtocolException("Bulk length is too long");

        budget.consume(length);
        byte[] payload = input.readNBytes((int) length);

        if (payload.length != length)
            throw new EOFException("Incomplete bulk-string payload");

        requireCRLF(input, budget);

        return new RespValue.BulkString(payload);
    }
    private void requireCRLF(InputStream input, Budget budget) throws IOException {
        if (readByte(input, budget) != '\r')
            throw new RespProtocolException("Expected CR after payload");

        if (readByte(input, budget) != '\n')
            throw new RespProtocolException("Expected LF after CR");
    }
    private byte[] readLine(InputStream input, Budget budget) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        while(true) {
            int curByte = readByte(input, budget);
            if ((char)curByte == '\r') {
                if (readByte(input, budget) == '\n')
                    return data.toByteArray();

                throw new RespProtocolException("Expected LF after CR");
            }

            if((char)curByte == '\n') {
                throw new RespProtocolException("LF without preceding CR");
            }

            if(data.size() >= respLimits.maxLineBytes())
                throw new RespProtocolException("Line is too long");

            data.write(curByte);
        }
    }
    private long readNumber(InputStream input,  Budget budget) throws IOException {
        byte[] bytes = readLine(input, budget);

        if (bytes.length == 0)
            throw new RespProtocolException("Empty numeric line");

        int start = (bytes[0] == '-' || bytes[0] == '+') ? 1 : 0;
        if (start == bytes.length)
            throw new RespProtocolException("Numeric line has no digits");

        for (int i = start ; i < bytes.length ; ++i) {
            if (bytes[i] < '0' || bytes[i] > '9')
                throw new RespProtocolException("Invalid character in numeric line");
        }
        try {
            return Long.parseLong(new String(bytes, StandardCharsets.US_ASCII));
        } catch (NumberFormatException e) {
            throw new RespProtocolException("Numeric header out of range");
        }
    }
    private int readByte(InputStream input, Budget budget) throws IOException {
        budget.consume(1);
        int curByte = input.read();

        if (curByte == -1)
            throw new EOFException("Connection ended during RESP value");

        return curByte;
    }
    private static final class Budget {
        private long remaining;
        private Budget(long remaining) {
            this.remaining = remaining;
        }
        private void consume(long bytes) throws RespProtocolException {
            if(bytes > remaining)
                throw new RespProtocolException("Maximum RESP value size exceeded");
            remaining -= bytes;
        }
    }
}