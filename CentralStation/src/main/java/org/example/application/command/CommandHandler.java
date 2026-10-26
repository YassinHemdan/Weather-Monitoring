package org.example.application.command;

import java.util.List;

public interface CommandHandler {
    String name();

    ArgumentCount argumentCount();

    CommandResult execute(List<String> arguments) throws CommandException;
}
