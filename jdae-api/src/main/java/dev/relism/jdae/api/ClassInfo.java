package dev.relism.jdae.api;

import java.util.List;

/** The class an expansion is happening in. */
public record ClassInfo(String internalName, String binaryName, int access, String superName, List<String> interfaces) {}
