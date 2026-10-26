package org.example.application.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface CommandResult {

    record Value(Optional<String> value) implements CommandResult{
        public Value {
            Objects.requireNonNull(value);
        }
    }

    record Values(List<String> values) implements CommandResult{
        public Values{
            Objects.requireNonNull(values);
            values = List.copyOf(values);
        }
    }
}
