package com.simulator.mdes.exception;

/** Thrown when a token cryptogram fails HMAC validation or ATC replay check. */
public class InvalidCryptogramException extends RuntimeException {
    public InvalidCryptogramException(String message) { super(message); }
}
