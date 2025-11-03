package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.JDAEExpander;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers and provides instances of JDAEExpander by annotation type.
 */
public class ExpanderRegistry {
    private final Map<String, Class<? extends JDAEExpander<?>>> byAnnotation = new HashMap<>();
    private final ClassLoader projectClassLoader;

    public ExpanderRegistry(ClassLoader projectClassLoader) {
        this.projectClassLoader = projectClassLoader;
        // Preload via ServiceLoader from the project's classloader: map expander -> its annotation type via generics
        if (projectClassLoader != null) {
            for (JDAEExpander<?> exp : ServiceLoader.load(JDAEExpander.class, projectClassLoader)) {
                Class<?> impl = exp.getClass();
                String annName = resolveAnnotationClassNameFromExpander(impl);
                if (annName != null) {
                    register(annName, (Class<? extends JDAEExpander<?>>) impl);
                }
            }
        }
    }

    public void register(String annotationClassName, Class<? extends JDAEExpander<?>> expanderClass) {
        byAnnotation.put(annotationClassName, expanderClass);
    }

    public JDAEExpander<?> get(String annotationClassName) {
        Class<? extends JDAEExpander<?>> cls = byAnnotation.get(annotationClassName);
        if (cls != null) {
            try {
                return cls.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to instantiate expander for " + annotationClassName, e);
            }
        }
        // Fallback: inspect the annotation type for @Expander meta-annotation
        return resolveFromAnnotation(annotationClassName);
    }

    private JDAEExpander<?> resolveFromAnnotation(String annotationClassName) {
        if (projectClassLoader == null) return null;
        try {
            Class<?> annType = Class.forName(annotationClassName, false, projectClassLoader);
            for (Annotation a : annType.getAnnotations()) {
                if (a.annotationType().getName().equals("dev.relism.jdae.api.annotations.Expander")) {
                    Method m = a.annotationType().getMethod("value");
                    Class<?> expanderClass = (Class<?>) m.invoke(a);
                    @SuppressWarnings("unchecked")
                    Class<? extends JDAEExpander<?>> typed = (Class<? extends JDAEExpander<?>>) expanderClass;
                    return typed.getDeclaredConstructor().newInstance();
                }
            }
        } catch (Exception e) {
            // ignore and fallback to null
        }
        return null;
    }

    private String resolveAnnotationClassNameFromExpander(Class<?> impl) {
        // Walk interfaces and superclasses to find JDAEExpander<T> generic
        for (Type t : impl.getGenericInterfaces()) {
            if (t instanceof ParameterizedType pt) {
                Type raw = pt.getRawType();
                if (raw instanceof Class && JDAEExpander.class.isAssignableFrom((Class<?>) raw)) {
                    Type arg = pt.getActualTypeArguments()[0];
                    if (arg instanceof Class<?> ac) {
                        return ac.getName();
                    }
                }
            }
        }
        Class<?> sup = impl.getSuperclass();
        if (sup != null && sup != Object.class) {
            return resolveAnnotationClassNameFromExpander(sup);
        }
        return null;
    }
}