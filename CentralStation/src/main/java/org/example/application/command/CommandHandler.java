package org.example.application.command;

import java.io.IOException;
import java.util.List;

public interface CommandHandler {
    String name();

    ArgumentCount argumentCount();

    CommandResult execute(List<String> arguments) throws IOException;
}
