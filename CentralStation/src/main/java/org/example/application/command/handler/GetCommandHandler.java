package org.example.application.command.handler;

import org.example.application.command.ArgumentCount;
import org.example.application.command.CommandHandler;
import org.example.application.command.CommandResult;
import org.example.application.port.out.KeyValueReader;

import java.io.IOException;
import java.util.List;

public class GetCommandHandler implements CommandHandler {
    private final KeyValueReader reader;

    public GetCommandHandler(KeyValueReader reader) {
        this.reader = reader;
    }

    @Override
    public String name() {
        return "GET";
    }

    @Override
    public ArgumentCount argumentCount() {
        return ArgumentCount.exactly(1);
    }

    @Override
    public CommandResult execute(List<String> arguments) throws IOException {
        return new CommandResult.Value(reader.get(arguments.getFirst()));
    }
}
