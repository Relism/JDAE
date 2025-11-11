package dev.relism.jdae.core.bytecode;

import dev.relism.jdae.api.AnnotationDescriptor;

import java.util.List;

/**
 * Central class that applies expansion metadata to original class bytes.
 */
public class BytecodeExpander {

    private final AnnotationRewriter rewriter = new AnnotationRewriter();

    public byte[] apply(byte[] classBytes, String ownerId, String sourceAnnotationClassName,
                        boolean removeOriginal, List<AnnotationDescriptor> inject) {
        return rewriter.rewrite(classBytes, ownerId, sourceAnnotationClassName, removeOriginal, inject);
    }

    public byte[] apply(byte[] classBytes, String ownerId, java.util.List<String> sourceAnnotationClassNames,
                        boolean removeOriginal, List<AnnotationDescriptor> inject) {
        return rewriter.rewrite(classBytes, ownerId, sourceAnnotationClassNames, removeOriginal, inject);
    }
}