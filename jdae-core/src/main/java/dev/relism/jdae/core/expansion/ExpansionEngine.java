package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.AnnotationDescriptor;
import dev.relism.jdae.api.ExpansionMetadata;
import dev.relism.jdae.api.JDAEExpander;
import dev.relism.jdae.core.bytecode.BytecodeExpander;
import dev.relism.jdae.core.bytecode.ExpanderCandidate;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Orchestrates scanning, expansion, validation, and rewriting for a class's bytecode.
 */
public class ExpansionEngine {
    private final ExpanderRegistry registry;
    private final BytecodeExpander bytecodeExpander;
    private final AnnotationInstanceFactory annotationFactory;

    public ExpansionEngine(ExpanderRegistry registry) {
        this(registry, Thread.currentThread().getContextClassLoader());
    }

    public ExpansionEngine(ExpanderRegistry registry, ClassLoader projectClassLoader) {
        this.registry = registry;
        this.bytecodeExpander = new BytecodeExpander();
        this.annotationFactory = new AnnotationInstanceFactory(projectClassLoader);
    }

    public byte[] expand(byte[] classBytes, List<ExpanderCandidate> candidates, boolean removeOriginal) {
        byte[] current = classBytes;
        for (ExpanderCandidate c : candidates) {
            JDAEExpander<?> exp = registry.get(c.getAnnotationClassName());
            if (exp == null) continue;

            ExpansionMetadata meta = new ExpansionMetadata();
            ExpansionContextImpl ctx = new ExpansionContextImpl(meta);

            Annotation annProxy = annotationFactory.create(c.getAnnotationClassName(), c.getRawAnnotation());
            @SuppressWarnings("rawtypes")
            JDAEExpander raw = exp;
            raw.expand(ctx, annProxy);
            List<AnnotationDescriptor> inject = meta.getGeneratedAnnotations();
            current = bytecodeExpander.apply(current, c.getOwnerId(), c.getAnnotationClassName(), removeOriginal, inject);
        }
        return current;
    }
}