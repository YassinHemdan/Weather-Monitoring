package org.example.application.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CommandDispatcherTest {
    @Test
    void dispatchesToRegisteredHandlerAndNormalizesCommandName() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<List<String>> receivedArguments = new AtomicReference<>();
        CommandResult expected = new CommandResult.Value(Optional.of("weather-json"));

        CommandHandler get = new TestHandler("GET", ArgumentCount.exactly(1), arguments -> {
            calls.incrementAndGet();
            receivedArguments.set(arguments);
            return expected;
        });
        CommandDispatcher dispatcher = new CommandDispatcher(List.of(get));

        CommandResult actual = dispatcher.dispatch(new Command("gEt", List.of("station-42")));

        assertSame(expected, actual);
        assertEquals(1, calls.get());
        assertEquals(List.of("station-42"), receivedArguments.get());
    }

    @Test
    void unknownCommandProducesTypedException() {
        CommandDispatcher dispatcher = new CommandDispatcher(List.of(
                new TestHandler("GET", ArgumentCount.exactly(1), ignored ->
                        new CommandResult.Value(Optional.empty()))));

        CommandException exception = assertThrows(CommandException.class,
                () -> dispatcher.dispatch(new Command("NOPE", List.of())));

        assertEquals(CommandException.Code.UNKNOWN_COMMAND, exception.code());
    }

    @Test
    void wrongArgumentCountDoesNotInvokeHandler() {
        AtomicInteger calls = new AtomicInteger();
        CommandDispatcher dispatcher = new CommandDispatcher(List.of(
                new TestHandler("GET", ArgumentCount.exactly(1), ignored -> {
                    calls.incrementAndGet();
                    return new CommandResult.Value(Optional.empty());
                })));

        CommandException exception = assertThrows(CommandException.class,
                () -> dispatcher.dispatch(new Command("GET", List.of())));

        assertEquals(CommandException.Code.WRONG_ARGUMENT_COUNT, exception.code());
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsDuplicateCommandNamesCaseInsensitively() {
        CommandHandler upper = new TestHandler("GET", ArgumentCount.exactly(1),
                ignored -> new CommandResult.Value(Optional.empty()));
        CommandHandler lower = new TestHandler("get", ArgumentCount.exactly(1),
                ignored -> new CommandResult.Value(Optional.empty()));

        assertThrows(IllegalArgumentException.class,
                () -> new CommandDispatcher(List.of(upper, lower)));
    }

    @Test
    void rejectsHandlerWithEmptyName() {
        CommandHandler invalid = new TestHandler("", ArgumentCount.exactly(0),
                ignored -> new CommandResult.Value(Optional.empty()));

        assertThrows(IllegalArgumentException.class,
                () -> new CommandDispatcher(List.of(invalid)));
    }

    @Test
    void rejectsNullHandlerCollection() {
        assertThrows(NullPointerException.class, () -> new CommandDispatcher(null));
    }

    @FunctionalInterface
    private interface Action {
        CommandResult execute(List<String> arguments) throws IOException;
    }

    private record TestHandler(
            String name,
            ArgumentCount argumentCount,
            Action action) implements CommandHandler {
        @Override
        public CommandResult execute(List<String> arguments) throws IOException {
            return action.execute(arguments);
        }
    }
}