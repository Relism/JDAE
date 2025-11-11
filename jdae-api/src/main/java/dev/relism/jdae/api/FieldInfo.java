package dev.relism.jdae.api;

public final class FieldInfo {
    private final String name;
    private final String descriptor;
    private final int access;

    public FieldInfo(String name, String descriptor, int access) {
        this.name = name;
        this.descriptor = descriptor;
        this.access = access;
    }

    public String getName() { return name; }
    public String getDescriptor() { return descriptor; }
    public int getAccess() { return access; }
}