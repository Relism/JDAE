package dev.relism.jdae.api;

import java.util.List;

/**
 * Informazioni sulla classe target dell'espansione.
 */
public final class ClassInfo {
    private final String internalName;
    private final String binaryName;
    private final int access;
    private final String superName;
    private final List<String> interfaces;

    public ClassInfo(String internalName, String binaryName, int access, String superName, List<String> interfaces) {
        this.internalName = internalName;
        this.binaryName = binaryName;
        this.access = access;
        this.superName = superName;
        this.interfaces = interfaces;
    }

    public String getInternalName() { return internalName; }
    public String getBinaryName() { return binaryName; }
    public int getAccess() { return access; }
    public String getSuperName() { return superName; }
    public List<String> getInterfaces() { return interfaces; }
}