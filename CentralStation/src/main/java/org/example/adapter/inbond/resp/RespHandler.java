package org.example.adapter.inbond.resp;

import org.example.adapter.inbond.tcp.TcpServer;
import org.example.application.command.*;

import java.io.*;
import java.net.Socket;
import java.util.Objects;

public class RespHandler implements TcpServer.ConnectionHandler {
    private final RespParser parser;
    private final RespWriter writer;
    private final RespCommandDecoder decoder;
    private final CommandDispatcher dispatcher;

    public RespHandler(CommandDispatcher dispatcher) {
        this(new RespParser(), new RespCommandDecoder(), dispatcher,
                new RespWriter());
    }

    public RespHandler(RespParser parser,
                       RespCommandDecoder decoder,
                       CommandDispatcher dispatcher,
                       RespWriter writer) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.writer = Objects.requireNonNull(writer, "writer");
    }


    @Override
    public void handle(Socket socket) throws IOException {
        Objects.requireNonNull(socket);

        InputStream inputStream = new BufferedInputStream(socket.getInputStream());
        OutputStream outputStream = new BufferedOutputStream(socket.getOutputStream());

        while(!socket.isClosed()) {
            RespValue request;
            try {
                request = parser.parse(inputStream);
            } catch (EOFException e) {
                writer.writeError("Protocol error: incomplete RESP request", outputStream);
                return;
            } catch (RespProtocolException e) {
                writer.writeError("Protocol error: " + e.getMessage(), outputStream);
                return;
            }

            if (request == null) {
                return;
            }

            try {
                Command command = decoder.decode(request);
                CommandResult result = dispatcher.dispatch(command);
                writer.writeResult(result, outputStream);
            } catch (InvalidCommandException | CommandException e) {
                writer.writeError(e.getMessage(), outputStream);
            }
        }
    }
}
