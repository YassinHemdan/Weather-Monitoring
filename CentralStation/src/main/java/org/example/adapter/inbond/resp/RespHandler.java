package org.example.adapter.inbond.resp;

import org.example.adapter.inbond.tcp.TcpServer;
import org.example.application.command.Command;
import org.example.application.command.CommandDispatcher;
import org.example.application.command.CommandException;
import org.example.application.command.CommandResult;
import org.example.application.command.InvalidCommandException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;

public class RespHandler implements TcpServer.ConnectionHandler {
    private static final System.Logger LOG =
            System.getLogger(RespHandler.class.getName());

    private final RespParser parser;
    private final RespWriter writer;
    private final RespCommandDecoder decoder;
    private final CommandDispatcher dispatcher;

    public RespHandler(CommandDispatcher dispatcher) {
        this(new RespParser(), new RespCommandDecoder(), dispatcher, new RespWriter());
    }

    public RespHandler(
            RespParser parser,
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
        Objects.requireNonNull(socket, "socket");

        InputStream input = new BufferedInputStream(socket.getInputStream());
        OutputStream output = new BufferedOutputStream(socket.getOutputStream());

        while (!socket.isClosed()) {
            RespValue request;
            try {
                request = parser.parse(input);
            } catch (EOFException e) {
                writer.writeError("Protocol error: incomplete RESP request", output);
                return;
            } catch (RespProtocolException e) {
                writer.writeError("Protocol error: " + e.getMessage(), output);
                return;
            }

            if (request == null) {
                return;
            }

            Command command;
            try {
                command = decoder.decode(request);
            } catch (InvalidCommandException e) {
                writer.writeError(e.getMessage(), output);
                continue;
            }

            CommandResult result;
            try {
                result = dispatcher.dispatch(command);
            } catch (CommandException e) {
                writer.writeError(e.getMessage(), output);
                continue;
            } catch (IOException e) {
                LOG.log(System.Logger.Level.ERROR,
                        "Command execution failed because of an I/O error", e);
                writer.writeError("Internal server error", output);
                continue;
            }

            writer.writeResult(result, output);
        }
    }
}