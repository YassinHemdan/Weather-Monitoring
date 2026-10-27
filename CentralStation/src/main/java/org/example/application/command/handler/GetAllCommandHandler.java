package org.example.application.command.handler;

import org.example.application.command.ArgumentCount;
import org.example.application.command.CommandHandler;
import org.example.application.command.CommandResult;
import org.example.application.port.out.KeyValueReader;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class GetAllCommandHandler implements CommandHandler {
    private final KeyValueReader reader;
    public GetAllCommandHandler(KeyValueReader reader) {
        this.reader = reader;
    }
    @Override
    public String name() {
        return "GETALL";
    }

    @Override
    public ArgumentCount argumentCount() {
        return ArgumentCount.exactly(0);
    }

    @Override
    public CommandResult execute(List<String> arguments) throws IOException{
        return new CommandResult.Values(reader.getAll());
    }
}
