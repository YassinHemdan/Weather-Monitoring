package org.example.adapter.inbond.resp;

import org.example.application.command.CommandResult;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RespWriter {
    private static final byte[] CRLF = {'\r', '\n'};


    public void writeResult(CommandResult result, OutputStream output) throws IOException {
        Objects.requireNonNull(result, "result");
        write(toRespValue(result), output);
    }
    public void write(RespValue respValue, OutputStream output) throws IOException {
        Objects.requireNonNull(respValue, "resp-value");
        Objects.requireNonNull(output, "output");

        writeRespValue(respValue, output);
        output.flush();
    }

    public void writeError(String message, OutputStream output) throws IOException {
        Objects.requireNonNull(message);
        message = message.replace('\n', ' ').replace('\r', ' ');
        String err = message.isBlank() ? "ERR" : "ERR " + message;

        write(new RespValue.ErrorValue(err), output);
    }
    private void writeRespValue(RespValue respValue, OutputStream output) throws IOException {
        if (respValue instanceof RespValue.SimpleString simpleString) {
            writeLineValue('+', simpleString.value(), output);
            return;
        }

        if (respValue instanceof RespValue.ErrorValue errorValue) {
            writeLineValue('-', errorValue.value(), output);
            return;
        }

        if (respValue instanceof RespValue.IntegerValue integerValue) {
            output.write(':');
            writeAscii(Long.toString(integerValue.value()), output);
            output.write(CRLF);

            return;
        }

        if (respValue instanceof RespValue.BulkString bulkString) {
            // $<size>\r\n<bulkString>\r\n

            byte[] bytes = bulkString.bytes();

            output.write('$');
            writeAscii(Integer.toString(bytes.length), output);
            output.write(CRLF);
            output.write(bytes);
            output.write(CRLF);

            return;
        }

        if (respValue instanceof RespValue.ArrayValue arrayValue) {
            // *<arrSize>\r\n<RespValue-1><RespValue-2> ... <RespValue-n>

            List<RespValue> elements = arrayValue.elements();

            output.write('*');
            writeAscii(Integer.toString(elements.size()), output);
            output.write(CRLF);

            for (RespValue el : elements) {
                writeRespValue(el, output);
            }

            return;
        }

        if (respValue == RespValue.NullBulkString.INSTANCE) {
            writeAscii("$-1\r\n", output);
            return;
        }

        if (respValue == RespValue.NullArray.INSTANCE) {
            writeAscii("*-1\r\n", output);
            return;
        }

        throw new IllegalArgumentException(
                "Unsupported RESP value type: " + respValue.getClass().getName());
    }

    private void writeLineValue(int prefix, String value, OutputStream output) throws IOException {
        // check if the string contains CR or LF
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0){
            throw new IllegalArgumentException(
                    "RESP simple strings should not contain CR or LF");
        }

        output.write(prefix);
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write(CRLF);
    }

    private void writeAscii(String value, OutputStream output) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }
    private RespValue toRespValue(CommandResult commandResult) {
        // if Value -> GET -> SimpleString
        // if Values -> GETALL -> Array of bulk strings
        if (commandResult instanceof CommandResult.Value res) {
            return res.value()
                    .<RespValue>map(RespWriter::bulkString)
                    .orElse(RespValue.NullBulkString.INSTANCE);
        }

        if (commandResult instanceof CommandResult.Values res) {
            // We will return a 2d array
            // where each element is an array that represents two bulk strings
            // the first one for the key, second one for the value
            /*
            let say that we have 3 entries in our database
             --> *3\r\n        -> three elements, each one is an array of two elements
                    *2\r\n     -> two elements, first one is the key, second is the value
                        $4\r\nKey1\r\n
                        $6\r\nValue1\r\n
                    *2\r\n
                        $4\r\nKey2\r\n
                        $6\r\nValue2\r\n
                    *2\r\n
                        $4\r\nKey3\r\n
                        $6\r\nValue4\r\n
            * */
            List<RespValue> elements = new ArrayList<>();
            res.values().forEach((key, value) -> {
                elements.add(new RespValue.ArrayValue(
                        List.of(
                                bulkString(key),
                                bulkString(value)
                        )
                ));
            });

            return new RespValue.ArrayValue(elements);
        }

        throw new IllegalArgumentException(
                "Unsupported command result type: " + commandResult.getClass().getName());
    }

    private static RespValue.BulkString bulkString(String value) {
        return new RespValue.BulkString(value.getBytes(StandardCharsets.UTF_8));
    }
}