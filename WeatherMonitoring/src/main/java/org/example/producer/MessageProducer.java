package org.example.producer;

import java.util.concurrent.ExecutionException;

public interface MessageProducer<T> extends AutoCloseable{
    void send(T message) throws ExecutionException, InterruptedException;
}
