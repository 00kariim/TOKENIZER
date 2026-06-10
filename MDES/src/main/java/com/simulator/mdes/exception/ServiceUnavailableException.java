package com.simulator.mdes.exception;

/** Thrown when a downstream service (Core Banking) is temporarily unavailable. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) { super(message); }
}
