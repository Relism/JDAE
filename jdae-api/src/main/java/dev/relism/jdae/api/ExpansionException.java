package dev.relism.jdae.api;

/** Anything that makes an expansion wrong. Always fails the build. */
public class ExpansionException extends RuntimeException {
    public ExpansionException(String message) { super(message); }
    public ExpansionException(String message, Throwable cause) { super(message, cause); }
}
