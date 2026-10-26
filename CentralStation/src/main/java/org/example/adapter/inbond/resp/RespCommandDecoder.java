package org.example.adapter.inbond.resp;

import org.example.application.command.Command;
import org.example.application.command.InvalidCommandException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public final class RespCommandDecoder {
    public Command decode(RespValue value) throws InvalidCommandException {
        if (!(value instanceof RespValue.ArrayValue array))
            throw new InvalidCommandException("Command must be a non-null array");

        if (array.elements().isEmpty())
            throw new InvalidCommandException("Command array must not be empty");

        ArrayList<String> parts = new ArrayList<>();
        for(RespValue element : array.elements()) {
            if (!(element instanceof RespValue.BulkString bulk))
                throw new InvalidCommandException("Command elements must be non-null bulk strings");

            parts.add(decodeUTF8(bulk.bytes()));
        }

        String name = parts.getFirst();
        if (name.isEmpty())
            throw new InvalidCommandException("Command name must not be empty");

        return new Command(name, parts.subList(1, parts.size()));
    }

    private String decodeUTF8(byte[] bytes) throws InvalidCommandException{
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e){
            throw new InvalidCommandException("This server requires UTF-8 command test");
        }
    }
}