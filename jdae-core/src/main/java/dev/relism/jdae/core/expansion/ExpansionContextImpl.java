package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.AnnotationBuilder;
import dev.relism.jdae.api.AnnotationDescriptor;
import dev.relism.jdae.api.ExpansionContext;
import dev.relism.jdae.api.ExpansionMetadata;

import java.util.function.Consumer;

public class ExpansionContextImpl implements ExpansionContext {
    private final ExpansionMetadata metadata;

    public ExpansionContextImpl(ExpansionMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public AnnotationBuilder addAnnotation(String annotationClassName) {
        return new AnnotationBuilderImpl(annotationClassName, metadata);
    }

    @Override
    public ExpansionContext with(Consumer<ExpansionContext> consumer) {
        consumer.accept(this);
        return this;
    }

    static class AnnotationBuilderImpl implements AnnotationBuilder {
        private final AnnotationDescriptor.Builder builder;
        private final ExpansionMetadata meta;

        AnnotationBuilderImpl(String annotationClassName, ExpansionMetadata meta) {
            this.builder = AnnotationDescriptor.builder(annotationClassName);
            this.meta = meta;
        }

        @Override
        public AnnotationBuilder value(String name, Object val) {
            builder.value(name, val);
            return this;
        }

        @Override
        public AnnotationDescriptor build() {
            AnnotationDescriptor d = builder.build();
            meta.add(d);
            return d;
        }
    }
}