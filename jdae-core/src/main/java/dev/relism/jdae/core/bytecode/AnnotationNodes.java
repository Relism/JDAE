package dev.relism.jdae.core.bytecode;

import dev.relism.jdae.api.AnnotationDescriptor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Descriptors in and out of ASM's annotation trees.
 *
 * <p>A descriptor's member values are whatever an expander set ({@link Class}, {@link Enum},
 * arrays) or whatever was read back from bytecode (an ASM {@link Type}, an {@link EnumValue},
 * a {@link List}). Both forms are accepted everywhere, which is what lets an expander modify an
 * annotation that was written by hand.
 */
public final class AnnotationNodes {

    private AnnotationNodes() {}

    /** An enum constant as bytecode stores it: the enum's descriptor and the constant's name. */
    public record EnumValue(String descriptor, String constant) {
        public String enumClassName() { return Type.getType(descriptor).getClassName(); }
    }

    public static AnnotationDescriptor toDescriptor(AnnotationNode node) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<Object> raw = node.values;
        if (raw != null) {
            for (int i = 0; i + 1 < raw.size(); i += 2) values.put((String) raw.get(i), fromAsm(raw.get(i + 1)));
        }
        return new AnnotationDescriptor(ClassScanner.nameOf(node), values);
    }

    public static AnnotationNode toNode(AnnotationDescriptor descriptor) {
        AnnotationNode node = new AnnotationNode(descriptorOf(descriptor.annotationClassName()));
        descriptor.values().forEach((name, value) -> {
            if (node.values == null) node.values = new ArrayList<>();
            node.values.add(name);
            node.values.add(toAsm(value));
        });
        return node;
    }

    public static String descriptorOf(String className) {
        return Type.getObjectType(className.replace('.', '/')).getDescriptor();
    }

    private static Object fromAsm(Object value) {
        if (value instanceof AnnotationNode nested) return toDescriptor(nested);
        if (value instanceof String[] pair && pair.length == 2) return new EnumValue(pair[0], pair[1]);
        if (value instanceof List<?> list) return list.stream().map(AnnotationNodes::fromAsm).toList();
        return value;
    }

    private static Object toAsm(Object value) {
        return switch (value) {
            case null -> null;
            case AnnotationDescriptor descriptor -> toNode(descriptor);
            case Class<?> type -> Type.getType(type);
            case Enum<?> constant -> new String[]{descriptorOf(constant.getDeclaringClass().getName()), constant.name()};
            case EnumValue constant -> new String[]{constant.descriptor(), constant.constant()};
            case List<?> list -> list.stream().map(AnnotationNodes::toAsm).toList();
            default -> {
                if (!value.getClass().isArray()) yield value;
                List<Object> out = new ArrayList<>(Array.getLength(value));
                for (int i = 0; i < Array.getLength(value); i++) out.add(toAsm(Array.get(value, i)));
                yield out;
            }
        };
    }
}
