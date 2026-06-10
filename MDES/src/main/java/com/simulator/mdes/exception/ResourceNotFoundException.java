package com.simulator.mdes.exception;

/** Thrown when a payment token is not found in the vault. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}
