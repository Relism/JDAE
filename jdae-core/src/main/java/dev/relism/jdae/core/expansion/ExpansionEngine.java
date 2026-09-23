package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.AnnotationDescriptor;
import dev.relism.jdae.api.ExpansionException;
import dev.relism.jdae.api.JDAEExpander;
import dev.relism.jdae.core.bytecode.AnnotationNodes;
import dev.relism.jdae.core.bytecode.AnnotationRewriter;
import dev.relism.jdae.core.bytecode.ClassScanner;
import dev.relism.jdae.core.bytecode.Target;
import org.objectweb.asm.tree.AnnotationNode;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.annotation.Repeatable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Expands one class file.
 *
 * <p>Per target and per annotation type: {@code addAnnotation} replaces what the target carries,
 * a second one makes the two a {@link Repeatable} pair written into the container,
 * {@code addOrModifyAnnotation} starts from what is there. Expanded annotations are then dropped
 * unless they asked to be kept, which is what makes running the build twice a no-op.
 */
public final class ExpansionEngine {

    private final ExpanderRegistry registry;
    private final AnnotationInstanceFactory instances;
    private final AnnotationValidator validator;
    private final AnnotationRewriter rewriter;
    private final ClassScanner scanner = new ClassScanner();
    private final ClassLoader loader;

    public ExpansionEngine(ExpanderRegistry registry, ClassLoader loader) {
        this.registry = registry;
        this.loader = loader;
        this.instances = new AnnotationInstanceFactory(loader);
        this.validator = new AnnotationValidator(loader);
        this.rewriter = new AnnotationRewriter(loader);
    }

    /** The expanded class, or the same array when this class has nothing to expand. */
    public byte[] expand(byte[] classBytes) {
        Map<String, AnnotationRewriter.Plan> plans = new LinkedHashMap<>();
        for (Target target : scanner.scan(classBytes)) {
            AnnotationRewriter.Plan plan = plan(target);
            if (plan != null) plans.put(target.ownerId(), plan);
        }
        return plans.isEmpty() ? classBytes : rewriter.rewrite(classBytes, plans);
    }

    private AnnotationRewriter.Plan plan(Target target) {
        List<ExpansionContextImpl.Produced> produced = new ArrayList<>();
        Set<String> remove = new LinkedHashSet<>();

        for (AnnotationNode node : List.copyOf(target.annotations())) {
            String name = ClassScanner.nameOf(node);
            ExpanderRegistry.Spec spec = registry.specFor(name);
            if (spec == null) continue;

            run(target, name, spec, node, produced);
            if (!spec.keepOriginal()) remove.add(name);
        }
        if (produced.isEmpty() && remove.isEmpty()) return null;

        List<AnnotationDescriptor> write = new ArrayList<>();
        for (Map.Entry<String, List<AnnotationDescriptor>> entry : instancesOf(target, produced).entrySet()) {
            String name = entry.getKey();
            List<AnnotationDescriptor> found = entry.getValue();
            String container = containerOf(name);

            remove.add(name);
            if (container != null) remove.add(container);

            if (found.size() == 1) {
                write.add(validator.validated(found.getFirst(), where(target, name)));
            } else {
                if (container == null) {
                    throw new ExpansionException(where(target, name) + ": " + found.size() + " of them were built, and @"
                            + name + " is not @Repeatable");
                }
                List<AnnotationDescriptor> all = new ArrayList<>();
                for (AnnotationDescriptor descriptor : found) all.add(validator.validated(descriptor, where(target, name)));
                write.add(validator.validated(new AnnotationDescriptor(container, Map.of("value", all)), where(target, container)));
            }
        }
        return new AnnotationRewriter.Plan(remove, write);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void run(Target target, String name, ExpanderRegistry.Spec spec, AnnotationNode node,
                     List<ExpansionContextImpl.Produced> produced) {
        Class<? extends Annotation> type = instances.annotationClass(name);
        Annotation annotation = instances.create(type, AnnotationNodes.toDescriptor(node));
        JDAEExpander expander = registry.instantiate(spec, name);
        try {
            expander.expand(new ExpansionContextImpl(target, instances, produced::add), annotation);
        } catch (ExpansionException refused) {
            throw new ExpansionException(where(target, name) + ": " + refused.getMessage(), refused);
        } catch (RuntimeException broken) {
            throw new ExpansionException(where(target, name) + ": " + spec.expander().getSimpleName() + " threw "
                    + broken.getClass().getSimpleName(), broken);
        }
    }

    /** What each annotation type ends up being on this target, existing ones included. */
    private Map<String, List<AnnotationDescriptor>> instancesOf(Target target, List<ExpansionContextImpl.Produced> produced) {
        Map<String, List<AnnotationDescriptor>> found = new LinkedHashMap<>();
        Set<String> replaced = new LinkedHashSet<>();

        for (ExpansionContextImpl.Produced one : produced) {
            List<AnnotationDescriptor> current = found.computeIfAbsent(one.annotationClassName(), name -> present(target, name));
            // Normalised before it is merged, so a lone value given for an array member is already
            // the array it means and adds to what is there instead of replacing it.
            AnnotationDescriptor descriptor = validator.normalized(one.descriptor(), where(target, one.annotationClassName()));
            if (one.merge()) {
                if (current.isEmpty()) current.add(descriptor);
                else current.set(current.size() - 1, overlay(current.getLast(), descriptor));
            } else {
                if (replaced.add(one.annotationClassName())) current.clear();
                current.add(descriptor);
            }
        }
        return found;
    }

    /** The annotations of that type the target already carries, unwrapping a repeatable container. */
    private List<AnnotationDescriptor> present(Target target, String name) {
        List<AnnotationDescriptor> found = new ArrayList<>();
        String container = containerOf(name);
        for (AnnotationNode node : target.annotations()) {
            String nodeName = ClassScanner.nameOf(node);
            if (nodeName.equals(name)) {
                found.add(AnnotationNodes.toDescriptor(node));
            } else if (nodeName.equals(container)) {
                Object value = AnnotationNodes.toDescriptor(node).values().get("value");
                if (value instanceof List<?> elements) {
                    for (Object element : elements) if (element instanceof AnnotationDescriptor one) found.add(one);
                }
            }
        }
        return found;
    }

    private static AnnotationDescriptor overlay(AnnotationDescriptor base, AnnotationDescriptor over) {
        Map<String, Object> values = new LinkedHashMap<>(base.values());
        over.values().forEach((name, value) -> values.merge(name, value, ExpansionEngine::append));
        return new AnnotationDescriptor(base.annotationClassName(), values);
    }

    /** Arrays grow, everything else is replaced: modifying a list of responses should add to it. */
    private static Object append(Object base, Object over) {
        List<Object> first = elements(base);
        List<Object> second = elements(over);
        if (first == null || second == null) return over;
        List<Object> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    /** The members of an array, however it was given; null when the value is not one. */
    private static List<Object> elements(Object value) {
        if (value instanceof List<?> list) return List.copyOf(list);
        if (value == null || !value.getClass().isArray()) return null;
        List<Object> elements = new ArrayList<>(Array.getLength(value));
        for (int i = 0; i < Array.getLength(value); i++) elements.add(Array.get(value, i));
        return elements;
    }

    private String containerOf(String annotationClassName) {
        try {
            Repeatable repeatable = Class.forName(annotationClassName, false, loader).getAnnotation(Repeatable.class);
            return repeatable == null ? null : repeatable.value().getName();
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    private static String where(Target target, String annotationClassName) {
        String simple = annotationClassName.substring(annotationClassName.lastIndexOf('.') + 1);
        return "@" + simple + " on " + switch (target.kind()) {
            case CLASS -> target.classInfo().binaryName();
            case METHOD -> target.classInfo().binaryName() + "." + target.methodInfo().name() + "()";
            case FIELD -> target.classInfo().binaryName() + "." + target.fieldInfo().name();
        };
    }
}
