package dev.relism.jdae.api;

import java.util.function.Consumer;

/**
 * Fluent builder to construct an AnnotationDescriptor.
 */
public interface AnnotationBuilder {
    // Backward-compatible primitive member setter
    AnnotationBuilder value(String name, Object val);

    // Preferred member setter
    AnnotationBuilder member(String name, Object val);

    AnnotationBuilder nested(String name, String annotationClassName, Consumer<AnnotationBuilder> consumer);
    AnnotationBuilder nested(String name, Class<?> annotationClass, Consumer<AnnotationBuilder> consumer);

    AnnotationBuilder nestedArray(String name, AnnotationBuilder... nestedBuilders);

    AnnotationDescriptor build();

    // Standalone builders for composing nested annotations
    static AnnotationBuilder of(String annotationClassName) {
        return new SimpleAnnotationBuilder(annotationClassName);
    }

    static AnnotationBuilder of(Class<?> annotationClass) {
        return new SimpleAnnotationBuilder(annotationClass.getName());
    }
}