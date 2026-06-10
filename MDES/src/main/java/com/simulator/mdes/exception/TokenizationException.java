package com.simulator.mdes.exception;

/** Thrown when a token provisioning request cannot be completed. */
public class TokenizationException extends RuntimeException {
    public TokenizationException(String message) { super(message); }
}
