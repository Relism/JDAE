package dev.relism.jdae.core.bytecode;

import org.objectweb.asm.tree.AnnotationNode;

/**
 * Represents a location in bytecode (class or member) with an annotation candidate
 * that may be expanded.
 */
public final class ExpanderCandidate {
    private final String ownerId; // class or member identifier
    private final String annotationClassName;
    private final AnnotationNode rawAnnotation;

    public ExpanderCandidate(String ownerId, String annotationClassName, AnnotationNode rawAnnotation) {
        this.ownerId = ownerId;
        this.annotationClassName = annotationClassName;
        this.rawAnnotation = rawAnnotation;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getAnnotationClassName() {
        return annotationClassName;
    }

    public AnnotationNode getRawAnnotation() {
        return rawAnnotation;
    }
}