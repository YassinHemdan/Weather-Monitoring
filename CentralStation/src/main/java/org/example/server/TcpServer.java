package org.example.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

public class TcpServer implements AutoCloseable{

    private static final System.Logger LOG =
            System.getLogger(TcpServer.class.getName());

    private static final Duration FORCED_SHUTDOWN_WAIT =
            Duration.ofSeconds(1);

    private enum State {
        NEW,
        RUNNING,
        STOPPING,
        TERMINATED
    }

    @FunctionalInterface
    public interface ConnectionHandler {
        void handle(Socket socket) throws IOException;
    }

    private final ServerSocket serverSocket;
    private final ExecutorService clientExecutor;
    private final Semaphore connectionSlots;
    private final Set<Socket> activeClients =
            ConcurrentHashMap.newKeySet();
    private final ConnectionHandler connectionHandler;
    private final Duration gracefulShutdownTimeout;

    private final Object lifecycleLock = new Object();
    private final CountDownLatch shutdownComplete = new CountDownLatch(1);

    private volatile State state = State.NEW;

    private volatile IOException shutdownFailure;

    private TcpServer(
            ServerSocket serverSocket,
            int maxConcurrentClients,
            Duration gracefulShutdownTimeout,
            ConnectionHandler connectionHandler
    ) {
        this.serverSocket = serverSocket;
        this.connectionSlots = new Semaphore(maxConcurrentClients);
        this.gracefulShutdownTimeout = gracefulShutdownTimeout;
        this.connectionHandler = connectionHandler;
        this.clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public static TcpServer newTcpServer(
            int port,
            int backlog,
            int maxConcurrentClients,
            Duration gracefulShutdownTimeout,
            ConnectionHandler connectionHandler
    ) throws IOException {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException(
                    "Port must be between 0 and 65535");
        }

        if (backlog < 1) {
            throw new IllegalArgumentException(
                    "backlog must be positive");
        }

        if (maxConcurrentClients < 1) {
            throw new IllegalArgumentException(
                    "maxConcurrentClients must be positive");
        }

        Objects.requireNonNull(
                gracefulShutdownTimeout,
                "gracefulShutdownTimeout");

        if (gracefulShutdownTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "gracefulShutdownTimeout must not be negative");
        }

        try {
            gracefulShutdownTimeout.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "gracefulShutdownTimeout is too large", e);
        }

        Objects.requireNonNull(
                connectionHandler, "connectionHandler");

        ServerSocket listener = new ServerSocket();

        try {
            listener.setReuseAddress(true);
            listener.bind(
                    new InetSocketAddress("127.0.0.1", port),
                    backlog);

            return new TcpServer(
                    listener,
                    maxConcurrentClients,
                    gracefulShutdownTimeout,
                    connectionHandler);

        } catch (IOException | RuntimeException | Error failure) {
            try {
                listener.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void serve() throws IOException {
        synchronized (lifecycleLock) {
            if (state != State.NEW) {
                throw new IllegalStateException(
                        "serve() can only be called once");
            }
            state = State.RUNNING;
        }

        LOG.log(System.Logger.Level.INFO,
                "Listening on 127.0.0.1:" + getPort());

        try {
            while (state == State.RUNNING) {
                Socket client;

                try {
                    client = serverSocket.accept();
                } catch (IOException e) {
                    if (state != State.RUNNING) {
                        break;
                    }
                    throw e;
                }

                admit(client);
            }
            LOG.log(System.Logger.Level.INFO, "Accept loop stopped.");

        } catch (IOException | RuntimeException | Error failure) {
            try {
                close();
            } catch (IOException closeFailure) {
                if (closeFailure != failure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
    }

    private void admit(Socket client) {
        synchronized (lifecycleLock) {
            if (state != State.RUNNING) {
                closeQuietly(client);
                return;
            }

            if (!connectionSlots.tryAcquire()) {
                LOG.log(System.Logger.Level.WARNING,
                        "Connection limit reached; rejecting client.");
                closeQuietly(client);
                return;
            }

            activeClients.add(client);

            try {
                clientExecutor.execute(() -> handleClient(client));

            } catch (RejectedExecutionException e) {
                releaseReservation(client);
                closeQuietly(client);
                LOG.log(System.Logger.Level.DEBUG,
                        "Client task rejected: " + e.getMessage());

            } catch (RuntimeException | Error failure) {
                releaseReservation(client);
                closeQuietly(client);
                throw failure;
            }
        }
    }

    private void handleClient(Socket client) {
        String remote = String.valueOf(client.getRemoteSocketAddress());

        try (Socket socket = client) {
            connectionHandler.handle(socket);

        } catch (IOException e) {
            if (state == State.RUNNING) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Connection ended with an I/O error: " + remote,
                        e);
            }

        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR,
                    "Client handler failed: " + remote, e);

        } finally {
            releaseReservation(client);
            LOG.log(System.Logger.Level.DEBUG,
                    "Disconnected: " + remote);
        }
    }

    private void releaseReservation(Socket client) {
        activeClients.remove(client);
        connectionSlots.release();
    }

    @Override
    public void close() throws IOException {
        boolean ownsShutdown = false;

        synchronized (lifecycleLock) {
            if (state == State.NEW || state == State.RUNNING) {
                state = State.STOPPING;
                ownsShutdown = true;
            }
        }

        if (!ownsShutdown) {
            awaitShutdownCompletion();
            if (shutdownFailure != null) {
                throw shutdownFailure;
            }
            return;
        }

        IOException closeFailure = null;
        boolean interrupted = false;

        try {
            try {
                serverSocket.close();
            } catch (IOException e) {
                closeFailure = e;
            }

            clientExecutor.shutdown();

            boolean terminated = false;
            try {
                terminated = clientExecutor.awaitTermination(
                        gracefulShutdownTimeout.toNanos(),
                        TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }

            if (!terminated) {
                for (Socket client : activeClients) {
                    closeQuietly(client);
                }

                clientExecutor.shutdownNow();

                try {
                    terminated = clientExecutor.awaitTermination(
                            FORCED_SHUTDOWN_WAIT.toNanos(),
                            TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }

            if (!terminated && !clientExecutor.isTerminated()) {
                LOG.log(System.Logger.Level.WARNING,
                        "Some client tasks have not terminated.");
            }

        } finally {
            shutdownFailure = closeFailure;

            synchronized (lifecycleLock) {
                state = State.TERMINATED;
            }

            shutdownComplete.countDown();

            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    private void awaitShutdownCompletion() {
        boolean interrupted = false;

        while (true) {
            try {
                shutdownComplete.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {

        }
    }
}
