// language: java
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
        java.util.Map<String, java.util.List<ExpanderCandidate>> byOwner = new java.util.LinkedHashMap<>();
        for (ExpanderCandidate c : candidates) {
            byOwner.computeIfAbsent(c.getOwnerId(), k -> new java.util.ArrayList<>()).add(c);
        }

        for (java.util.Map.Entry<String, java.util.List<ExpanderCandidate>> entry : byOwner.entrySet()) {
            String ownerId = entry.getKey();
            java.util.List<ExpanderCandidate> group = entry.getValue();
            ExpansionMetadata meta = new ExpansionMetadata();

            java.util.Set<String> processedAnnotationTypes = new java.util.LinkedHashSet<>();

            for (ExpanderCandidate c : group) {
                if (!registry.hasExpander(c.getAnnotationClassName())) {
                    continue;
                }

                JDAEExpander<?> exp = registry.get(c.getAnnotationClassName());
                if (exp == null) continue;

                ExpansionContextImpl ctx = new ExpansionContextImpl(
                        meta,
                        c.getTargetKind(),
                        c.getClassInfo(),
                        c.getMethodInfo(),
                        c.getFieldInfo()
                );

                Annotation annProxy = annotationFactory.create(c.getAnnotationClassName(), c.getRawAnnotation());
                @SuppressWarnings("rawtypes")
                JDAEExpander raw = exp;
                raw.expand(ctx, annProxy);

                processedAnnotationTypes.add(c.getAnnotationClassName());
            }

            java.util.List<AnnotationDescriptor> inject = meta.getGeneratedAnnotations();
            java.util.List<String> removalTypes = processedAnnotationTypes.stream()
                    .filter(t -> removeOriginal && registry.shouldRemoveOriginal(t))
                    .toList();

            if (inject.isEmpty() && removalTypes.isEmpty()) {
                continue;
            }

            current = bytecodeExpander.apply(current, ownerId, removalTypes, true /* removal already filtered by policy */, inject);
        }
        return current;
    }
}