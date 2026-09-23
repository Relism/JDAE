package dev.relism.jdae.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One annotation to write, as a name and its members. Immutable. */
public final class AnnotationDescriptor {

    private final String annotationClassName;
    private final Map<String, Object> values;

    public AnnotationDescriptor(String annotationClassName, Map<String, Object> values) {
        this.annotationClassName = annotationClassName;
        this.values = Collections.unmodifiableMap(values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values));
    }

    public String annotationClassName() { return annotationClassName; }

    /** Members by name, in the order they were set. */
    public Map<String, Object> values() { return values; }

    @Override public String toString() { return "@" + annotationClassName + values; }
}
