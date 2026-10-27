package org.example.application.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public sealed interface CommandResult {

    record Value(Optional<String> value) implements CommandResult{
        public Value {
            Objects.requireNonNull(value);
        }
    }

    record Values(Map<String, String> values) implements CommandResult{
        public Values{
            Objects.requireNonNull(values);
            values = Map.copyOf(values);
        }
    }
}
