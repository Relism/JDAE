package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.JDAEExpander;
import dev.relism.jdae.api.annotations.ExpanderMarker;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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
        // Preload via ServiceLoader from the project's classloader if available
        if (projectClassLoader != null) {
            for (JDAEExpander<?> exp : ServiceLoader.load(JDAEExpander.class, projectClassLoader)) {
                Class<?> impl = exp.getClass();
                if (impl.isAnnotationPresent(ExpanderMarker.class)) {
                    ExpanderMarker marker = impl.getAnnotation(ExpanderMarker.class);
                    if (!marker.name().isEmpty()) {
                        register(marker.name(), (Class<? extends JDAEExpander<?>>) impl);
                    }
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
        JDAEExpander<?> resolved = resolveFromAnnotation(annotationClassName);
        if (resolved != null) {
            return resolved;
        }
        return null;
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
}