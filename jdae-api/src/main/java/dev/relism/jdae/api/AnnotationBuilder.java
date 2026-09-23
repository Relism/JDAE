package dev.relism.jdae.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds one annotation. Member values are what the annotation type declares: a {@code String},
 * a primitive wrapper, a {@link Class}, an {@link Enum} constant, a nested annotation, or an array
 * of those. Everything is checked against the annotation type before it is written.
 */
public final class AnnotationBuilder {

    private final String annotationClassName;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Consumer<AnnotationDescriptor> sink;

    private AnnotationBuilder(String annotationClassName, Consumer<AnnotationDescriptor> sink) {
        this.annotationClassName = annotationClassName;
        this.sink = sink;
    }

    /** A builder that stands on its own, for {@link #nestedArray} elements. */
    public static AnnotationBuilder of(String annotationClassName) {
        return new AnnotationBuilder(annotationClassName, null);
    }

    public static AnnotationBuilder of(Class<?> annotationClass) {
        return of(annotationClass.getName());
    }

    /** Used by {@link ExpansionContext} implementations to collect what the expander built. */
    public static AnnotationBuilder collecting(String annotationClassName, Consumer<AnnotationDescriptor> sink) {
        return new AnnotationBuilder(annotationClassName, sink);
    }

    public AnnotationBuilder member(String name, Object value) {
        values.put(name, value);
        return this;
    }

    public AnnotationBuilder nested(String name, String annotationClassName, Consumer<AnnotationBuilder> nested) {
        AnnotationBuilder builder = of(annotationClassName);
        nested.accept(builder);
        return member(name, builder.build());
    }

    public AnnotationBuilder nested(String name, Class<?> annotationClass, Consumer<AnnotationBuilder> nested) {
        return nested(name, annotationClass.getName(), nested);
    }

    public AnnotationBuilder nestedArray(String name, AnnotationBuilder... elements) {
        AnnotationDescriptor[] out = new AnnotationDescriptor[elements.length];
        for (int i = 0; i < elements.length; i++) out[i] = elements[i].build();
        return member(name, out);
    }

    /** Finishes this annotation, handing it to the context when there is one. */
    public AnnotationDescriptor build() {
        AnnotationDescriptor descriptor = new AnnotationDescriptor(annotationClassName, values);
        if (sink != null) sink.accept(descriptor);
        return descriptor;
    }
}
