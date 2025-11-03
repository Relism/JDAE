package dev.relism.jdae.api;

import java.util.function.Consumer;

/**
 * Fluent context passed to expanders for building annotations.
 */
public interface ExpansionContext {
    AnnotationBuilder addAnnotation(String annotationClassName);
    AnnotationBuilder addAnnotation(Class<?> annotationClass);

    // Convenience one-shot to add and configure an annotation inline
    ExpansionContext addAnnotation(String annotationClassName, Consumer<AnnotationBuilder> consumer);
    ExpansionContext addAnnotation(Class<?> annotationClass, Consumer<AnnotationBuilder> consumer);
}