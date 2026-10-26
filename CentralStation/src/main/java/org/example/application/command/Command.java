package org.example.application.command;

import java.util.List;
import java.util.Objects;

public record Command(String name, List<String> arguments) {
    public Command {
        Objects.requireNonNull(name);
        if (name.isEmpty())
            throw new IllegalArgumentException("Command name must not be empty");

        arguments = List.copyOf(arguments);
    }
}
