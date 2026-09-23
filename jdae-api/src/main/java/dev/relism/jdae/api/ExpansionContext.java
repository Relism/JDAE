package dev.relism.jdae.api;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;

/**
 * What an expander writes into, and what it can ask about the target it is expanding.
 *
 * <p>{@code addAnnotation} replaces whatever annotation of that type the target carries;
 * {@code addOrModifyAnnotation} starts from it instead, overlaying the members it sets
 * (arrays are appended to, single values replaced). Two additions of one
 * {@link java.lang.annotation.Repeatable} type are written into its container.
 */
public interface ExpansionContext {

    AnnotationBuilder addAnnotation(String annotationClassName);

    AnnotationBuilder addAnnotation(Class<?> annotationClass);

    ExpansionContext addAnnotation(String annotationClassName, Consumer<AnnotationBuilder> annotation);

    ExpansionContext addAnnotation(Class<?> annotationClass, Consumer<AnnotationBuilder> annotation);

    AnnotationBuilder addOrModifyAnnotation(String annotationClassName);

    AnnotationBuilder addOrModifyAnnotation(Class<?> annotationClass);

    ExpansionContext addOrModifyAnnotation(String annotationClassName, Consumer<AnnotationBuilder> annotation);

    ExpansionContext addOrModifyAnnotation(Class<?> annotationClass, Consumer<AnnotationBuilder> annotation);

    TargetKind getTargetKind();

    ClassInfo getClassInfo();

    /** Null unless {@link #getTargetKind()} is {@link TargetKind#METHOD}. */
    MethodInfo getMethodInfo();

    /** Null unless {@link #getTargetKind()} is {@link TargetKind#FIELD}. */
    FieldInfo getFieldInfo();

    /** What the target carries right now, before this expansion, or null. */
    <A extends Annotation> A annotation(Class<A> annotationClass);

    /** Refuses the expansion with a message naming what is wrong. Fails the build. */
    default void fail(String message) {
        throw new ExpansionException(message);
    }
}
