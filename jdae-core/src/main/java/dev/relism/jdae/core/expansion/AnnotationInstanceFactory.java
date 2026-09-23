package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.AnnotationDescriptor;
import dev.relism.jdae.api.ExpansionException;
import dev.relism.jdae.core.bytecode.AnnotationNodes;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/**
 * Hands an expander the annotation it is expanding, as the annotation type itself.
 *
 * <p>The instance is a proxy over what the bytecode holds, so an unset member answers the
 * annotation type's own default, exactly as a runtime read would.
 */
public final class AnnotationInstanceFactory {

    private final ClassLoader loader;

    public AnnotationInstanceFactory(ClassLoader loader) {
        this.loader = loader;
    }

    @SuppressWarnings("unchecked")
    public <A extends Annotation> A create(Class<A> type, AnnotationDescriptor descriptor) {
        Map<String, Object> values = descriptor.values();
        return (A) Proxy.newProxyInstance(loader, new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "annotationType" -> type;
            case "toString" -> "@" + type.getName() + values;
            case "hashCode" -> values.hashCode();
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> {
                Object value = values.get(method.getName());
                yield value == null ? method.getDefaultValue() : coerce(method.getReturnType(), value);
            }
        });
    }

    public Class<? extends Annotation> annotationClass(String className) {
        try {
            return Class.forName(className, false, loader).asSubclass(Annotation.class);
        } catch (ClassNotFoundException | LinkageError | ClassCastException absent) {
            throw new ExpansionException(className + " is not a loadable annotation type", absent);
        }
    }

    private Object coerce(Class<?> expected, Object value) {
        if (expected.isArray()) {
            List<?> elements = value instanceof List<?> list ? list : List.of(value);
            Object array = Array.newInstance(expected.getComponentType(), elements.size());
            for (int i = 0; i < elements.size(); i++) Array.set(array, i, coerce(expected.getComponentType(), elements.get(i)));
            return array;
        }
        if (value instanceof Type type) return load(type.getClassName());
        if (value instanceof AnnotationNodes.EnumValue constant) {
            for (Object known : expected.getEnumConstants()) {
                if (((Enum<?>) known).name().equals(constant.constant())) return known;
            }
            throw new ExpansionException(expected.getName() + " has no constant " + constant.constant());
        }
        if (value instanceof AnnotationDescriptor nested) return create(annotationClass(nested.annotationClassName()), nested);
        return value;
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException | LinkageError absent) {
            throw new ExpansionException(className + " is not on the compile classpath", absent);
        }
    }
}
