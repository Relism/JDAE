package dev.relism.jdae.api;

import java.util.function.Consumer;

/**
 * Rich context passed to expanders to build annotations and check against
 * application point metadata (class/method/field).
 */
public interface ExpansionContext {
    // builder API
    AnnotationBuilder addAnnotation(String annotationClassName);
    AnnotationBuilder addAnnotation(Class<?> annotationClass);
    ExpansionContext addAnnotation(String annotationClassName, Consumer<AnnotationBuilder> consumer);
    ExpansionContext addAnnotation(Class<?> annotationClass, Consumer<AnnotationBuilder> consumer);

    // add-or-modify API: merge changes into an existing annotation of same type if present, otherwise create it
    AnnotationBuilder addOrModifyAnnotation(String annotationClassName);
    AnnotationBuilder addOrModifyAnnotation(Class<?> annotationClass);
    ExpansionContext addOrModifyAnnotation(String annotationClassName, Consumer<AnnotationBuilder> consumer);
    ExpansionContext addOrModifyAnnotation(Class<?> annotationClass, Consumer<AnnotationBuilder> consumer);

    // just target metadata
    TargetKind getTargetKind();
    ClassInfo getClassInfo();
    MethodInfo getMethodInfo();
    FieldInfo getFieldInfo();

    // util s
    default void fail(String message) { throw new ExpansionException(message); }
}