package org.example.application.command.handler;

import org.example.application.command.CommandResult;
import org.example.application.port.out.KeyValueReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetCommandHandlerTest {
    @Test
    void returnsStoredValue() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.get("42")).thenReturn(Optional.of("{\"station_id\":42}"));

        CommandResult.Value result = (CommandResult.Value)
                new GetCommandHandler(reader).execute(List.of("42"));

        assertEquals(Optional.of("{\"station_id\":42}"), result.value());
        verify(reader).get("42");
        verifyNoMoreInteractions(reader);
    }

    @Test
    void representsMissingKeyAsEmptyOptional() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.get("missing")).thenReturn(Optional.empty());

        CommandResult.Value result = (CommandResult.Value)
                new GetCommandHandler(reader).execute(List.of("missing"));

        assertTrue(result.value().isEmpty());
    }

    @Test
    void propagatesStorageFailure() throws Exception {
        KeyValueReader reader = mock(KeyValueReader.class);
        when(reader.get("42")).thenThrow(new IOException("disk unavailable"));

        assertThrows(IOException.class,
                () -> new GetCommandHandler(reader).execute(List.of("42")));
    }
}