package org.example.resp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class RespWriter {
    private static final byte [] CRLF = {'\r', '\n'};

    public static void writeCommand(OutputStream output, String... args) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(args, "args");

        if (args.length == 0)
            throw new IllegalArgumentException("A RESP command must contain at least its name");

        writeAscii(output, "*" + args.length + "\r\n");
        for (String arg : args) {
            Objects.requireNonNull(arg, "command arg");

            writeAscii(output, "$" + arg.length() + "\r\n");
            output.write(arg.getBytes(StandardCharsets.UTF_8));
            output.write(CRLF);
        }
        output.flush();
    }
    private static void writeAscii(OutputStream output, String message) throws IOException {
        output.write(message.getBytes(StandardCharsets.US_ASCII));
    }
}