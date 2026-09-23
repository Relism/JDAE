package dev.relism.jdae.core.bytecode;

import dev.relism.jdae.api.AnnotationDescriptor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a plan into a class file: for each target, the annotation types to drop and the
 * annotations to put in their place.
 *
 * <p>Frames and code are copied through untouched — nothing here recomputes them, so a class is
 * only ever changed where an annotation sits.
 */
public final class AnnotationRewriter {

    /** What happens to one target: {@code remove} by annotation class name, then {@code write}. */
    public record Plan(Set<String> remove, List<AnnotationDescriptor> write) {}

    private final ClassLoader loader;

    public AnnotationRewriter(ClassLoader loader) {
        this.loader = loader;
    }

    public byte[] rewrite(byte[] original, Map<String, Plan> plans) {
        if (plans.isEmpty()) return original;

        ClassReader reader = new ClassReader(original);
        ClassNode cn = new ClassNode(Opcodes.ASM9);
        reader.accept(cn, 0);

        apply(plans.get(cn.name), cn.visibleAnnotations, cn.invisibleAnnotations,
                (visible, annotations) -> { if (visible) cn.visibleAnnotations = annotations; else cn.invisibleAnnotations = annotations; });

        if (cn.fields != null) {
            for (FieldNode f : cn.fields) {
                apply(plans.get(cn.name + "#" + f.name), f.visibleAnnotations, f.invisibleAnnotations,
                        (visible, annotations) -> { if (visible) f.visibleAnnotations = annotations; else f.invisibleAnnotations = annotations; });
            }
        }
        if (cn.methods != null) {
            for (MethodNode m : cn.methods) {
                apply(plans.get(cn.name + "#" + m.name + m.desc), m.visibleAnnotations, m.invisibleAnnotations,
                        (visible, annotations) -> { if (visible) m.visibleAnnotations = annotations; else m.invisibleAnnotations = annotations; });
            }
        }

        ClassWriter writer = new ClassWriter(reader, 0);
        cn.accept(writer);
        return writer.toByteArray();
    }

    private interface Lists { void set(boolean visible, List<AnnotationNode> annotations); }

    private void apply(Plan plan, List<AnnotationNode> visible, List<AnnotationNode> invisible, Lists lists) {
        if (plan == null) return;

        List<AnnotationNode> keptVisible = without(visible, plan.remove());
        List<AnnotationNode> keptInvisible = without(invisible, plan.remove());

        for (AnnotationDescriptor descriptor : plan.write()) {
            if (runtimeVisible(descriptor.annotationClassName())) keptVisible.add(AnnotationNodes.toNode(descriptor));
            else keptInvisible.add(AnnotationNodes.toNode(descriptor));
        }

        lists.set(true, keptVisible.isEmpty() ? null : keptVisible);
        lists.set(false, keptInvisible.isEmpty() ? null : keptInvisible);
    }

    private static List<AnnotationNode> without(List<AnnotationNode> annotations, Set<String> removed) {
        List<AnnotationNode> kept = new ArrayList<>();
        if (annotations != null) {
            for (AnnotationNode node : annotations) {
                if (!removed.contains(ClassScanner.nameOf(node))) kept.add(node);
            }
        }
        return kept;
    }

    /** Where an annotation belongs, read from its own {@code @Retention}; runtime when unknown. */
    private boolean runtimeVisible(String annotationClassName) {
        try {
            Retention retention = Class.forName(annotationClassName, false, loader).getAnnotation(Retention.class);
            return retention == null || retention.value() == RetentionPolicy.RUNTIME;
        } catch (ClassNotFoundException | LinkageError absent) {
            return true;
        }
    }
}
