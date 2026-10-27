package org.example.application.command.handler;

import org.example.application.command.CommandResult;
import org.example.application.port.out.KeyValueReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetAllCommandHandlerTest {
    @Test
    void returnsAllKeyValuePairs() throws Exception {
        Map<String, String> expected = Map.of(
                "42", "{\"station_id\":42}",
                "57", "{\"station_id\":57}");
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.getAll()).thenReturn(expected);

        CommandResult.Values result = (CommandResult.Values)
                new GetAllCommandHandler(reader).execute(List.of());

        assertEquals(expected, result.values());
        verify(reader).getAll();
    }

    @Test
    void returnsEmptyMapWhenStoreIsEmpty() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.getAll()).thenReturn(Map.of());

        CommandResult.Values result = (CommandResult.Values)
                new GetAllCommandHandler(reader).execute(List.of());

        assertTrue(result.values().isEmpty());
    }

    @Test
    void propagatesStorageFailure() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.getAll()).thenThrow(new IOException("disk unavailable"));

        assertThrows(IOException.class,
                () -> new GetAllCommandHandler(reader).execute(List.of()));
    }
}