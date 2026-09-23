package dev.relism.jdae.api;

/** The field an annotation sits on, or {@code null} for any other target. */
public record FieldInfo(String name, String descriptor, int access) {}
