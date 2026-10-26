package org.example.application.command;

import java.util.Objects;

public class CommandException extends Exception {

    public enum Code {
        UNKNOWN_COMMAND,
        WRONG_ARGUMENT_COUNT,
        INVALID_ARGUMENT
    }

    private final Code code;

    public CommandException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code);
    }

    public Code code() {
        return code;
    }
}