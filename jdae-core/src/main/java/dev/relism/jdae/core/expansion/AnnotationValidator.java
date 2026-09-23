package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.AnnotationDescriptor;
import dev.relism.jdae.api.ExpansionException;
import dev.relism.jdae.core.bytecode.AnnotationNodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks an annotation against the type it claims to be, before anything is written.
 *
 * <p>An annotation that does not typecheck is a class file the JVM rejects at the first
 * reflective read, so every failure here fails the build with the member that caused it. Values
 * are normalised on the way through: a single value given for an array member becomes a
 * one-element array, which is what the same annotation written by hand would mean.
 */
public final class AnnotationValidator {

    private static final Map<Class<?>, Class<?>> BOXED = Map.of(
            int.class, Integer.class, long.class, Long.class, short.class, Short.class, byte.class, Byte.class,
            char.class, Character.class, boolean.class, Boolean.class, float.class, Float.class, double.class, Double.class);

    private final ClassLoader loader;

    public AnnotationValidator(ClassLoader loader) {
        this.loader = loader;
    }

    /** Checks the members that are set, leaving the rest to whatever fills them in later. */
    public AnnotationDescriptor normalized(AnnotationDescriptor descriptor, String where) {
        return checked(descriptor, where, false);
    }

    /** Checks a finished annotation: every member set is right, and nothing required is missing. */
    public AnnotationDescriptor validated(AnnotationDescriptor descriptor, String where) {
        return checked(descriptor, where, true);
    }

    private AnnotationDescriptor checked(AnnotationDescriptor descriptor, String where, boolean complete) {
        Class<?> type = load(descriptor.annotationClassName(), where);
        if (!type.isAnnotation()) throw new ExpansionException(where + ": " + type.getName() + " is not an annotation type");

        Map<String, Object> values = new LinkedHashMap<>();
        descriptor.values().forEach((name, value) -> {
            Method member = memberOf(type, name, where);
            values.put(name, value(member.getReturnType(), value, where + ", member " + name));
        });

        if (!complete) return new AnnotationDescriptor(descriptor.annotationClassName(), values);

        for (Method member : type.getDeclaredMethods()) {
            if (member.getDefaultValue() == null && !values.containsKey(member.getName())) {
                throw new ExpansionException(where + ": @" + type.getSimpleName() + "." + member.getName()
                        + " has no default and was not set");
            }
        }
        return new AnnotationDescriptor(descriptor.annotationClassName(), values);
    }

    private Object value(Class<?> expected, Object value, String where) {
        if (value == null) throw new ExpansionException(where + " is null");

        if (expected.isArray()) {
            List<Object> elements = new ArrayList<>();
            for (Object element : elementsOf(value)) elements.add(value(expected.getComponentType(), element, where));
            return elements;
        }
        if (expected == Class.class) {
            if (value instanceof Class<?> || value instanceof Type) return value;
            throw mismatch(expected, value, where);
        }
        if (expected.isEnum()) return enumValue(expected, value, where);
        if (expected.isAnnotation()) {
            if (value instanceof AnnotationDescriptor nested && nested.annotationClassName().equals(expected.getName())) {
                return validated(nested, where);
            }
            throw mismatch(expected, value, where);
        }
        Class<?> boxed = BOXED.getOrDefault(expected, expected);
        if (!boxed.isInstance(value)) throw mismatch(expected, value, where);
        return value;
    }

    private Object enumValue(Class<?> expected, Object value, String where) {
        String constant;
        if (value instanceof Enum<?> known) {
            if (!known.getDeclaringClass().getName().equals(expected.getName())) throw mismatch(expected, value, where);
            return value;
        }
        if (value instanceof AnnotationNodes.EnumValue stored && stored.enumClassName().equals(expected.getName())) {
            constant = stored.constant();
        } else {
            throw mismatch(expected, value, where);
        }
        for (Object known : expected.getEnumConstants()) {
            if (((Enum<?>) known).name().equals(constant)) return value;
        }
        throw new ExpansionException(where + ": " + expected.getName() + " has no constant " + constant);
    }

    private static List<Object> elementsOf(Object value) {
        if (value instanceof List<?> list) return List.copyOf(list);
        if (!value.getClass().isArray()) return List.of(value);   // a lone value means an array of one
        List<Object> elements = new ArrayList<>(Array.getLength(value));
        for (int i = 0; i < Array.getLength(value); i++) elements.add(Array.get(value, i));
        return elements;
    }

    private Method memberOf(Class<?> type, String name, String where) {
        try {
            return type.getDeclaredMethod(name);
        } catch (NoSuchMethodException unknown) {
            throw new ExpansionException(where + ": @" + type.getSimpleName() + " has no member " + name);
        }
    }

    private Class<?> load(String className, String where) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException | LinkageError absent) {
            throw new ExpansionException(where + ": " + className + " is not on the compile classpath", absent);
        }
    }

    private static ExpansionException mismatch(Class<?> expected, Object value, String where) {
        String got = value instanceof Type type ? "class " + type.getClassName()
                : value instanceof AnnotationNodes.EnumValue stored ? stored.enumClassName() + "." + stored.constant()
                : value instanceof AnnotationDescriptor nested ? "@" + nested.annotationClassName()
                : value.getClass().getName();
        return new ExpansionException(where + " expects " + expected.getName() + " but got " + got);
    }
}
