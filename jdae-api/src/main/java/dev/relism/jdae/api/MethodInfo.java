package dev.relism.jdae.api;

import java.util.List;

public final class MethodInfo {
    private final String name;
    private final String descriptor;
    private final int access;
    private final List<String> parameterTypes;
    private final String returnType;

    public MethodInfo(String name, String descriptor, int access,
                      List<String> parameterTypes, String returnType) {
        this.name = name;
        this.descriptor = descriptor;
        this.access = access;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    public String getName() { return name; }
    public String getDescriptor() { return descriptor; }
    public int getAccess() { return access; }
    public List<String> getParameterTypes() { return parameterTypes; }
    public String getReturnType() { return returnType; }
}