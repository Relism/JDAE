package dev.relism.jdae.core.expansion;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates runtime {@link Annotation} instances backed by ASM {@link AnnotationNode} data.
 * This allows expanders to receive a non-null typed annotation proxy and access its members.
 */
public final class AnnotationInstanceFactory {
    private final ClassLoader classLoader;

    public AnnotationInstanceFactory(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Annotation create(String annotationClassName, AnnotationNode node) {
        try {
            Class<?> annType = Class.forName(annotationClassName, false, classLoader);
            if (!annType.isAnnotation()) {
                throw new IllegalArgumentException(annotationClassName + " is not an annotation type");
            }
            InvocationHandler handler = new AsmAnnotationInvocationHandler((Class<? extends Annotation>) annType, node, classLoader);
            return (Annotation) Proxy.newProxyInstance(classLoader, new Class[]{annType}, handler);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load annotation class: " + annotationClassName, e);
        }
    }

    private static final class AsmAnnotationInvocationHandler implements InvocationHandler {
        private final Class<? extends Annotation> annType;
        private final Map<String, Object> values;
        private final ClassLoader cl;

        AsmAnnotationInvocationHandler(Class<? extends Annotation> annType, AnnotationNode node, ClassLoader cl) {
            this.annType = annType;
            this.cl = cl;
            this.values = parseValues(node);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            // Standard annotation methods
            if (name.equals("annotationType") && method.getParameterCount() == 0) {
                return annType;
            }
            if (name.equals("toString") && method.getParameterCount() == 0) {
                return annType.getName() + values.toString();
            }
            if (name.equals("hashCode") && method.getParameterCount() == 0) {
                return values.hashCode() ^ annType.hashCode();
            }
            if (name.equals("equals") && method.getParameterCount() == 1) {
                Object other = args[0];
                if (other == proxy) return true;
                if (!(other instanceof Annotation a) || !a.annotationType().equals(annType)) return false;
                // naive equality on member map
                for (Method m : annType.getDeclaredMethods()) {
                    Object thisVal = getValue(m);
                    Object thatVal = m.invoke(other);
                    if (!equalsMemberValue(thisVal, thatVal)) return false;
                }
                return true;
            }
            // Annotation element access
            return getValue(method);
        }

        private Object getValue(Method method) {
            String element = method.getName();
            Object raw = values.get(element);
            if (raw == null) {
                raw = method.getDefaultValue();
            }
            if (raw == null) {
                throw new IllegalStateException("Missing value for annotation element: " + annType.getName() + "." + element);
            }
            return coerce(raw, method.getReturnType());
        }

        private Map<String, Object> parseValues(AnnotationNode node) {
            Map<String, Object> map = new HashMap<>();
            List<Object> vals = node.values;
            if (vals != null) {
                for (int i = 0; i < vals.size(); i += 2) {
                    String name = (String) vals.get(i);
                    Object value = vals.get(i + 1);
                    map.put(name, value);
                }
            }
            return map;
        }

        private Object coerce(Object raw, Class<?> expectedType) {
            if (raw == null) return null;
            // Handle enum stored as String[]{descriptor, value}
            if (raw instanceof String[] arr && arr.length == 2 && expectedType.isEnum()) {
                String desc = arr[0];
                String constName = arr[1];
                String enumClassName = Type.getType(desc).getClassName();
                Class<?> enumType = load(enumClassName);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object enumConst = Enum.valueOf((Class) enumType, constName);
                return enumConst;
            }
            // Class values stored as ASM Type
            if (raw instanceof Type t && expectedType == Class.class) {
                return load(t.getClassName());
            }
            // Nested annotation
            if (raw instanceof AnnotationNode an && expectedType.isAnnotation()) {
                String nestedName = Type.getType(an.desc).getClassName();
                return new AnnotationInstanceFactory(cl).create(nestedName, an);
            }
            // Arrays: ASM stores as List; convert to actual array type
            if (expectedType.isArray() && raw instanceof List<?> list) {
                Class<?> component = expectedType.getComponentType();
                Object array = java.lang.reflect.Array.newInstance(component, list.size());
                for (int i = 0; i < list.size(); i++) {
                    Object elem = list.get(i);
                    Object coerced = coerce(elem, component);
                    java.lang.reflect.Array.set(array, i, coerced);
                }
                return array;
            }
            // Primitives and Strings are typically already proper boxed types
            return raw;
        }

        private Class<?> load(String className) {
            try {
                return Class.forName(className, false, cl);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Failed to load class: " + className, e);
            }
        }

        private boolean equalsMemberValue(Object a, Object b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            Class<?> ca = a.getClass();
            Class<?> cb = b.getClass();
            if (ca.isArray() && cb.isArray()) {
                int len = java.lang.reflect.Array.getLength(a);
                if (len != java.lang.reflect.Array.getLength(b)) return false;
                for (int i = 0; i < len; i++) {
                    Object ea = java.lang.reflect.Array.get(a, i);
                    Object eb = java.lang.reflect.Array.get(b, i);
                    if (!equalsMemberValue(ea, eb)) return false;
                }
                return true;
            }
            return a.equals(b);
        }
    }
}