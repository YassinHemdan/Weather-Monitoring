package org.example.resp;

import java.io.IOException;

public class RespProtocolException extends IOException {
    public RespProtocolException(String message) {
        super(message);
    }

    public RespProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
