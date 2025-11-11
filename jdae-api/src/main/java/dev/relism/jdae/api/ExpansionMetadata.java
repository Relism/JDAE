package dev.relism.jdae.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Metadata collected during an expansion for a single class/member.
 */
public final class ExpansionMetadata {
    private final List<AnnotationDescriptor> generatedAnnotations = new ArrayList<>();
    private final Map<String, Map<String, Object>> mergedByType = new LinkedHashMap<>();

    public void add(AnnotationDescriptor descriptor) {
        generatedAnnotations.add(descriptor);
    }

    public void merge(AnnotationDescriptor descriptor) {
        String type = descriptor.getAnnotationClassName();
        Map<String, Object> existing = mergedByType.computeIfAbsent(type, k -> new LinkedHashMap<>());
        for (Map.Entry<String, Object> e : descriptor.getValues().entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            Object prev = existing.get(key);
            if (prev == null) {
                existing.put(key, val);
            } else {
                existing.put(key, mergeValues(prev, val));
            }
        }
    }

    private Object mergeValues(Object prev, Object next) {
        if (prev == null) return next;
        if (next == null) return prev;
        // Concatenate arrays
        if (prev.getClass().isArray() && next.getClass().isArray()) {
            int len1 = java.lang.reflect.Array.getLength(prev);
            int len2 = java.lang.reflect.Array.getLength(next);
            Object[] out = new Object[len1 + len2];
            for (int i = 0; i < len1; i++) out[i] = java.lang.reflect.Array.get(prev, i);
            for (int j = 0; j < len2; j++) out[len1 + j] = java.lang.reflect.Array.get(next, j);
            return out;
        }
        // Append lists
        if (prev instanceof List<?> l1 && next instanceof List<?> l2) {
            List<Object> out = new ArrayList<>(l1.size() + l2.size());
            out.addAll(l1);
            out.addAll(l2);
            return out;
        }
        // Last writer wins for scalars and nested descriptors
        return next;
    }

    public List<AnnotationDescriptor> getGeneratedAnnotations() {
        List<AnnotationDescriptor> out = new ArrayList<>(mergedByType.size() + generatedAnnotations.size());
        Set<String> mergedTypes = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Object>> e : mergedByType.entrySet()) {
            out.add(new AnnotationDescriptor(e.getKey(), e.getValue()));
            mergedTypes.add(e.getKey());
        }
        for (AnnotationDescriptor d : generatedAnnotations) {
            if (!mergedTypes.contains(d.getAnnotationClassName())) {
                out.add(d);
            }
        }
        return Collections.unmodifiableList(out);
    }
}