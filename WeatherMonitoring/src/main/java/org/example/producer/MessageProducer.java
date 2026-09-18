package org.example.producer;

import java.util.concurrent.ExecutionException;

public interface MessageProducer<T> {
    void send(T message) throws ExecutionException, InterruptedException;
    void close();
}
