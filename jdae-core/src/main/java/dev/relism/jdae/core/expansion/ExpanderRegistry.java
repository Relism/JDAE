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
    private final Map<String, ExpanderMeta> metaByAnnotation = new HashMap<>();
    private final ClassLoader projectClassLoader;

    public ExpanderRegistry(ClassLoader projectClassLoader) {
        this.projectClassLoader = projectClassLoader;
        // map expander -> its annotation type via generics
        if (projectClassLoader != null) {
            for (JDAEExpander<?> exp : ServiceLoader.load(JDAEExpander.class, projectClassLoader)) {
                Class<?> impl = exp.getClass();
                String annName = resolveAnnotationClassNameFromExpander(impl);
                if (annName != null) {
                    register(annName, (Class<? extends JDAEExpander<?>>) impl);
                    // and cache the meta too
                    metaByAnnotation.computeIfAbsent(annName, this::resolveExpanderMetaFromAnnotation);
                }
            }
        }
    }

    public void register(String annotationClassName, Class<? extends JDAEExpander<?>> expanderClass) {
        byAnnotation.put(annotationClassName, expanderClass);
        metaByAnnotation.computeIfAbsent(annotationClassName, this::resolveExpanderMetaFromAnnotation);
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
        // fallback
        ExpanderMeta meta = metaByAnnotation.computeIfAbsent(annotationClassName, this::resolveExpanderMetaFromAnnotation);
        if (meta != null && meta.expanderClass != null) {
            try {
                return meta.expanderClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to instantiate expander for " + annotationClassName, e);
            }
        }
        return null;
    }

    public boolean hasExpander(String annotationClassName) {
        if (byAnnotation.containsKey(annotationClassName)) return true;
        ExpanderMeta meta = metaByAnnotation.computeIfAbsent(annotationClassName, this::resolveExpanderMetaFromAnnotation);
        return meta != null && meta.expanderClass != null;
    }

    /**
     * Resolve Expander metadata (expander class, keepOriginal, id) from the annotation type's @Expander meta-annotation.
     */
    private ExpanderMeta resolveExpanderMetaFromAnnotation(String annotationClassName) {
        if (projectClassLoader == null) return null;
        try {
            Class<?> annType = Class.forName(annotationClassName, false, projectClassLoader);
            for (Annotation a : annType.getAnnotations()) {
                if (a.annotationType().getName().equals("dev.relism.jdae.api.annotations.Expander")) {
                    Method valueMethod = a.annotationType().getMethod("value");
                    Method keepOriginalMethod = a.annotationType().getMethod("keepOriginal");
                    Method idMethod = a.annotationType().getMethod("id");
                    Object val = valueMethod.invoke(a);
                    boolean keepOriginal = (Boolean) keepOriginalMethod.invoke(a);
                    String id = (String) idMethod.invoke(a);
                    Class<? extends JDAEExpander<?>> typed = null;
                    if (val instanceof Class<?> expClass) {
                        @SuppressWarnings("unchecked")
                        Class<? extends JDAEExpander<?>> cast = (Class<? extends JDAEExpander<?>>) expClass;
                        try {
                            Class.forName(cast.getName(), false, projectClassLoader);
                            typed = cast;
                        } catch (ClassNotFoundException cnf) {
                            // expander not resolvable; leave typed as null
                        }
                    }
                    return new ExpanderMeta(typed, keepOriginal, id);
                }
            }
        } catch (Exception e) {
            // ignore and fallback to null
        }
        return null;
    }

    private String resolveAnnotationClassNameFromExpander(Class<?> impl) {
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

    /**
     * Return true if the annotation type's @Expander declares keepOriginal=false.
     * Defaults to false (do not remove) when metadata is unavailable.
     */
    public boolean shouldRemoveOriginal(String annotationClassName) {
        ExpanderMeta meta = metaByAnnotation.computeIfAbsent(annotationClassName, this::resolveExpanderMetaFromAnnotation);
        return meta != null && !meta.keepOriginal;
    }

    private static final class ExpanderMeta {
        final Class<? extends JDAEExpander<?>> expanderClass;
        final boolean keepOriginal;
        final String id;

        ExpanderMeta(Class<? extends JDAEExpander<?>> expanderClass, boolean keepOriginal, String id) {
            this.expanderClass = expanderClass;
            this.keepOriginal = keepOriginal;
            this.id = id;
        }
    }
}