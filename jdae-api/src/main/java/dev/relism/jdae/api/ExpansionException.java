package dev.relism.jdae.api;

/**
 * Domain exception for errors occurring during expansion processes.
 */
public class ExpansionException extends RuntimeException {
    public ExpansionException(String message) { super(message); }
    public ExpansionException(String message, Throwable cause) { super(message, cause); }
}