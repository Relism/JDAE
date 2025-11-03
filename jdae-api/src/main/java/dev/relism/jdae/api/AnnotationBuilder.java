package dev.relism.jdae.api;

/**
 * Fluent builder to construct an AnnotationDescriptor.
 */
public interface AnnotationBuilder {
    AnnotationBuilder value(String name, Object val);
    AnnotationDescriptor build();
}