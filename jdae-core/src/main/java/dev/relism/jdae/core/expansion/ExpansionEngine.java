package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.AnnotationDescriptor;
import dev.relism.jdae.api.ExpansionMetadata;
import dev.relism.jdae.api.JDAEExpander;
import dev.relism.jdae.core.bytecode.BytecodeExpander;
import dev.relism.jdae.core.bytecode.ExpanderCandidate;

import java.util.List;

/**
 * Orchestrates scanning, expansion, validation, and rewriting for a class's bytecode.
 */
public class ExpansionEngine {
    private final ExpanderRegistry registry;
    private final BytecodeExpander bytecodeExpander;

    public ExpansionEngine(ExpanderRegistry registry) {
        this.registry = registry;
        this.bytecodeExpander = new BytecodeExpander();
    }

    public byte[] expand(byte[] classBytes, List<ExpanderCandidate> candidates, boolean removeOriginal) {
        byte[] current = classBytes;
        for (ExpanderCandidate c : candidates) {
            JDAEExpander<?> exp = registry.get(c.getAnnotationClassName());
            if (exp == null) continue;

            ExpansionMetadata meta = new ExpansionMetadata();
            ExpansionContextImpl ctx = new ExpansionContextImpl(meta);

            // For now, we cannot instantiate the source annotation reflectively from AnnotationNode; expansion will rely on raw presence.
            exp.expand(ctx, null);
            List<AnnotationDescriptor> inject = meta.getGeneratedAnnotations();
            current = bytecodeExpander.apply(current, c.getOwnerId(), c.getAnnotationClassName(), removeOriginal, inject);
        }
        return current;
    }
}