package dev.relism.jdae.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata collected during an expansion for a single class/member.
 */
public final class ExpansionMetadata {
    private final List<AnnotationDescriptor> generatedAnnotations = new ArrayList<>();

    public void add(AnnotationDescriptor descriptor) {
        generatedAnnotations.add(descriptor);
    }

    public List<AnnotationDescriptor> getGeneratedAnnotations() {
        return Collections.unmodifiableList(generatedAnnotations);
    }
}