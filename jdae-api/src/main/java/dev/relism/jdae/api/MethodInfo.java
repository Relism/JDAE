package dev.relism.jdae.api;

import java.util.List;

/** The method an annotation sits on, or {@code null} for any other target. */
public record MethodInfo(String name, String descriptor, int access, List<String> parameterTypes, String returnType) {}
