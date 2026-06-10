package com.simulator.mdes.exception;

/** Thrown when a duplicate token is detected for the same card + device pair. */
public class DuplicateTokenException extends RuntimeException {
    public DuplicateTokenException(String message) { super(message); }
}
