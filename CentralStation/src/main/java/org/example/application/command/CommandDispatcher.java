package org.example.application.command;

import java.io.IOException;
import java.util.*;

public final class CommandDispatcher {
    private final Map<String, CommandHandler> handlers;

    public CommandDispatcher(Collection<? extends CommandHandler> commandHandlers) {
        Objects.requireNonNull(commandHandlers);

        Map<String, CommandHandler> registry = new HashMap<>();

        for (CommandHandler commandHandler : commandHandlers)
            validateRegistration(commandHandler, registry);

        this.handlers = Map.copyOf(registry);
    }

    public CommandResult dispatch(Command command) throws CommandException, IOException {
        Objects.requireNonNull(command);

        String name = normalize(command.name());

        CommandHandler handler = handlers.get(name);

        if (handler == null) {
            throw new CommandException(
                    CommandException.Code.UNKNOWN_COMMAND,
                    "Unknown command"
            );
        }

        ArgumentCount expected = handler.argumentCount();
        int actual = command.arguments().size();

        if (!expected.accepts(actual)) {
            throw new CommandException(
                    CommandException.Code.WRONG_ARGUMENT_COUNT,
                    name + " expects "
                            + expected.description()
                            + " argument(s), but received "
                            + actual
            );
        }

        return Objects.requireNonNull(
                handler.execute(command.arguments()),
                "Command handler returned null"
        );
    }
    private void validateRegistration(
            CommandHandler commandHandler,
            Map<String, CommandHandler> registry
    ) {
        Objects.requireNonNull(commandHandler);

        String name = normalize(Objects.requireNonNull(commandHandler.name()));

        if (name.isEmpty())
            throw new IllegalArgumentException("Handler name must not be empty");

        Objects.requireNonNull(commandHandler.argumentCount());

        CommandHandler oldHandler = registry.get(name);
        if (oldHandler != null)
            throw new IllegalArgumentException("Duplicate command registration: " + name);
    }
    private String normalize(String name) {
        return name.toUpperCase(Locale.ROOT);
    }
}
