package dev.relism.jdae.api;

import java.util.function.Consumer;

/**
 * Fluent context passed to expanders for building annotations.
 */
public interface ExpansionContext {
    AnnotationBuilder addAnnotation(String annotationClassName);
    ExpansionContext with(Consumer<ExpansionContext> consumer);
}