package org.example.application.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommandModelTest {
    @Test
    void commandCopiesArgumentsToPreventExternalMutation() {
        List<String> source = new ArrayList<>(List.of("station-42"));
        Command command = new Command("GET", source);
        source.add("unexpected");

        assertEquals(List.of("station-42"), command.arguments());
        assertThrows(UnsupportedOperationException.class,
                () -> command.arguments().add("another"));
    }

    @Test
    void commandRejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> new Command("", List.of()));
    }

    @Test
    void commandResultValuesAreImmutableSnapshots() {
        var source = new java.util.HashMap<>(Map.of("a", "one"));
        CommandResult.Values result = new CommandResult.Values(source);
        source.put("b", "two");

        assertEquals(Map.of("a", "one"), result.values());
        assertThrows(UnsupportedOperationException.class,
                () -> result.values().put("c", "three"));
    }

    @Test
    void valueResultRequiresOptional() {
        assertThrows(NullPointerException.class, () -> new CommandResult.Value(null));
        assertEquals(Optional.empty(), new CommandResult.Value(Optional.empty()).value());
    }
}