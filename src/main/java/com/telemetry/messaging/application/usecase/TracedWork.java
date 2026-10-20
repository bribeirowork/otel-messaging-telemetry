package com.telemetry.messaging.application.usecase;

/**
 * Work executed inside a span (sending or processing the message).
 */
@FunctionalInterface
public interface TracedWork<T> {

    T execute() throws Throwable;
}
