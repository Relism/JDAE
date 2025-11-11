package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.*;

import java.util.function.Consumer;

public class ExpansionContextImpl implements ExpansionContext {
    private final ExpansionMetadata metadata;
    private final TargetKind targetKind;
    private final ClassInfo classInfo;
    private final MethodInfo methodInfo;
    private final FieldInfo fieldInfo;

    public ExpansionContextImpl(ExpansionMetadata metadata,
                                TargetKind targetKind,
                                ClassInfo classInfo,
                                MethodInfo methodInfo,
                                FieldInfo fieldInfo) {
        this.metadata = metadata;
        this.targetKind = targetKind;
        this.classInfo = classInfo;
        this.methodInfo = methodInfo;
        this.fieldInfo = fieldInfo;
    }

    @Override
    public AnnotationBuilder addAnnotation(String annotationClassName) {
        return new AnnotationBuilderImpl(annotationClassName, metadata);
    }

    @Override
    public AnnotationBuilder addAnnotation(Class<?> annotationClass) {
        return new AnnotationBuilderImpl(annotationClass.getName(), metadata);
    }

    @Override
    public ExpansionContext addAnnotation(String annotationClassName, Consumer<AnnotationBuilder> consumer) {
        AnnotationBuilder builder = addAnnotation(annotationClassName);
        consumer.accept(builder);
        builder.build();
        return this;
    }

    @Override
    public ExpansionContext addAnnotation(Class<?> annotationClass, Consumer<AnnotationBuilder> consumer) {
        AnnotationBuilder builder = addAnnotation(annotationClass);
        consumer.accept(builder);
        builder.build();
        return this;
    }

    // Add-or-modify API
    @Override
    public AnnotationBuilder addOrModifyAnnotation(String annotationClassName) {
        return new MergingAnnotationBuilderImpl(annotationClassName, metadata);
    }

    @Override
    public AnnotationBuilder addOrModifyAnnotation(Class<?> annotationClass) {
        return addOrModifyAnnotation(annotationClass.getName());
    }

    @Override
    public ExpansionContext addOrModifyAnnotation(String annotationClassName, Consumer<AnnotationBuilder> consumer) {
        AnnotationBuilder builder = addOrModifyAnnotation(annotationClassName);
        consumer.accept(builder);
        builder.build();
        return this;
    }

    @Override
    public ExpansionContext addOrModifyAnnotation(Class<?> annotationClass, Consumer<AnnotationBuilder> consumer) {
        return addOrModifyAnnotation(annotationClass.getName(), consumer);
    }

    @Override
    public TargetKind getTargetKind() { return targetKind; }

    @Override
    public ClassInfo getClassInfo() { return classInfo; }

    @Override
    public MethodInfo getMethodInfo() { return methodInfo; }

    @Override
    public FieldInfo getFieldInfo() { return fieldInfo; }

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
        public AnnotationBuilder member(String name, Object val) {
            return value(name, val);
        }

        @Override
        public AnnotationBuilder nested(String name, String annotationClassName, Consumer<AnnotationBuilder> consumer) {
            // Build a nested annotation as an AnnotationDescriptor; ASM accepts AnnotationNode as value,
            // but we keep it as a descriptor and convert later in rewriter.
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
            AnnotationDescriptor d = builder.build();
            meta.add(d);
            return d;
        }
    }

    static class MergingAnnotationBuilderImpl implements AnnotationBuilder {
        private final AnnotationDescriptor.Builder builder;
        private final ExpansionMetadata meta;

        MergingAnnotationBuilderImpl(String annotationClassName, ExpansionMetadata meta) {
            this.builder = AnnotationDescriptor.builder(annotationClassName);
            this.meta = meta;
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
            AnnotationDescriptor d = builder.build();
            meta.merge(d);
            return d;
        }
    }
}