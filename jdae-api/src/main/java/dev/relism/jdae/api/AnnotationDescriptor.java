package dev.relism.jdae.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A runtime description of an annotation to be injected into bytecode.
 */
public final class AnnotationDescriptor {
    private final String annotationClassName;
    private final Map<String, Object> values;

    public AnnotationDescriptor(String annotationClassName, Map<String, Object> values) {
        this.annotationClassName = annotationClassName;
        this.values = values != null ? new LinkedHashMap<>(values) : new LinkedHashMap<>();
    }

    public String getAnnotationClassName() {
        return annotationClassName;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public static Builder builder(String annotationClassName) {
        return new Builder(annotationClassName);
    }

    public static final class Builder {
        private final String annotationClassName;
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder(String annotationClassName) {
            this.annotationClassName = annotationClassName;
        }

        public Builder value(String name, Object val) {
            values.put(name, val);
            return this;
        }

        public AnnotationDescriptor build() {
            return new AnnotationDescriptor(annotationClassName, values);
        }
    }
}