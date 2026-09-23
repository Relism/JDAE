package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.AnnotationBuilder;
import dev.relism.jdae.api.AnnotationDescriptor;
import dev.relism.jdae.api.ClassInfo;
import dev.relism.jdae.api.ExpansionContext;
import dev.relism.jdae.api.FieldInfo;
import dev.relism.jdae.api.MethodInfo;
import dev.relism.jdae.api.TargetKind;
import dev.relism.jdae.core.bytecode.AnnotationNodes;
import dev.relism.jdae.core.bytecode.ClassScanner;
import dev.relism.jdae.core.bytecode.Target;
import org.objectweb.asm.tree.AnnotationNode;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;

/** The context one expander call writes into. Collects what it builds; writes nothing itself. */
final class ExpansionContextImpl implements ExpansionContext {

    /** One annotation an expander asked for, and whether it starts from what is already there. */
    record Produced(String annotationClassName, boolean merge, AnnotationDescriptor descriptor) {}

    private final Target target;
    private final AnnotationInstanceFactory instances;
    private final Consumer<Produced> sink;

    ExpansionContextImpl(Target target, AnnotationInstanceFactory instances, Consumer<Produced> sink) {
        this.target = target;
        this.instances = instances;
        this.sink = sink;
    }

    @Override public AnnotationBuilder addAnnotation(String annotationClassName) {
        return AnnotationBuilder.collecting(annotationClassName, d -> sink.accept(new Produced(annotationClassName, false, d)));
    }

    @Override public AnnotationBuilder addAnnotation(Class<?> annotationClass) {
        return addAnnotation(annotationClass.getName());
    }

    @Override public ExpansionContext addAnnotation(String annotationClassName, Consumer<AnnotationBuilder> annotation) {
        AnnotationBuilder builder = addAnnotation(annotationClassName);
        annotation.accept(builder);
        builder.build();
        return this;
    }

    @Override public ExpansionContext addAnnotation(Class<?> annotationClass, Consumer<AnnotationBuilder> annotation) {
        return addAnnotation(annotationClass.getName(), annotation);
    }

    @Override public AnnotationBuilder addOrModifyAnnotation(String annotationClassName) {
        return AnnotationBuilder.collecting(annotationClassName, d -> sink.accept(new Produced(annotationClassName, true, d)));
    }

    @Override public AnnotationBuilder addOrModifyAnnotation(Class<?> annotationClass) {
        return addOrModifyAnnotation(annotationClass.getName());
    }

    @Override public ExpansionContext addOrModifyAnnotation(String annotationClassName, Consumer<AnnotationBuilder> annotation) {
        AnnotationBuilder builder = addOrModifyAnnotation(annotationClassName);
        annotation.accept(builder);
        builder.build();
        return this;
    }

    @Override public ExpansionContext addOrModifyAnnotation(Class<?> annotationClass, Consumer<AnnotationBuilder> annotation) {
        return addOrModifyAnnotation(annotationClass.getName(), annotation);
    }

    @Override public TargetKind getTargetKind() { return target.kind(); }

    @Override public ClassInfo getClassInfo() { return target.classInfo(); }

    @Override public MethodInfo getMethodInfo() { return target.methodInfo(); }

    @Override public FieldInfo getFieldInfo() { return target.fieldInfo(); }

    @Override public <A extends Annotation> A annotation(Class<A> annotationClass) {
        for (AnnotationNode node : target.annotations()) {
            if (ClassScanner.nameOf(node).equals(annotationClass.getName())) {
                return instances.create(annotationClass, AnnotationNodes.toDescriptor(node));
            }
        }
        return null;
    }
}
