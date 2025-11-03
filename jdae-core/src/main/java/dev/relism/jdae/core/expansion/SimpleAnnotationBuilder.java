package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.AnnotationBuilder;
import dev.relism.jdae.api.AnnotationDescriptor;

import java.util.function.Consumer;

/**
 * Lightweight builder used for nested annotation composition inside ExpansionContext.
 */
class SimpleAnnotationBuilder implements AnnotationBuilder {
    private final AnnotationDescriptor.Builder builder;

    SimpleAnnotationBuilder(String annotationClassName) {
        this.builder = AnnotationDescriptor.builder(annotationClassName);
    }

    @Override
    public AnnotationBuilder value(String name, Object val) {
        builder.value(name, val);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Object val) {
        return value(name, val);
    }

    @Override
    public AnnotationBuilder nested(String name, String annotationClassName, Consumer<AnnotationBuilder> consumer) {
        SimpleAnnotationBuilder nested = new SimpleAnnotationBuilder(annotationClassName);
        consumer.accept(nested);
        builder.value(name, nested.build());
        return this;
    }

    @Override
    public AnnotationBuilder nested(String name, Class<?> annotationClass, Consumer<AnnotationBuilder> consumer) {
        return nested(name, annotationClass.getName(), consumer);
    }

    @Override
    public AnnotationBuilder nestedArray(String name, AnnotationBuilder... nestedBuilders) {
        AnnotationDescriptor[] arr = new AnnotationDescriptor[nestedBuilders.length];
        for (int i = 0; i < nestedBuilders.length; i++) {
            arr[i] = nestedBuilders[i].build();
        }
        builder.value(name, arr);
        return this;
    }

    @Override
    public AnnotationDescriptor build() {
        return builder.build();
    }
}