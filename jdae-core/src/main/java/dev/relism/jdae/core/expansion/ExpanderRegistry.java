package dev.relism.jdae.core.expansion;

import dev.relism.jdae.api.ExpansionException;
import dev.relism.jdae.api.JDAEExpander;
import dev.relism.jdae.api.annotations.Expander;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Which expander answers for which annotation.
 *
 * <p>An annotation type names its expander with {@link Expander}; a {@link ServiceLoader} entry
 * for {@link JDAEExpander} registers one as well, matched by its type argument. Anything found
 * and then unusable — an expander that cannot be loaded or constructed — fails the build rather
 * than quietly expanding nothing.
 */
public final class ExpanderRegistry {

    /** An annotation that expands, and what happens to it afterwards. */
    public record Spec(Class<? extends JDAEExpander<?>> expander, boolean keepOriginal) {}

    private final ClassLoader loader;
    private final Map<String, Spec> registered = new HashMap<>();
    private final Map<String, Spec> resolved = new HashMap<>();

    @SuppressWarnings("unchecked")
    public ExpanderRegistry(ClassLoader loader) {
        this.loader = loader;
        if (loader == null) return;
        for (JDAEExpander<?> expander : ServiceLoader.load(JDAEExpander.class, loader)) {
            String annotation = annotationOf(expander.getClass());
            if (annotation != null) register(annotation, (Class<? extends JDAEExpander<?>>) expander.getClass());
        }
    }

    public void register(String annotationClassName, Class<? extends JDAEExpander<?>> expander) {
        registered.put(annotationClassName, new Spec(expander, keepOriginal(annotationClassName)));
    }

    /** How this annotation expands, or null when it is an ordinary annotation. */
    public Spec specFor(String annotationClassName) {
        return resolved.computeIfAbsent(annotationClassName, name -> {
            Spec known = registered.get(name);
            return known != null ? known : fromMetaAnnotation(name);
        });
    }

    /** A new expander for each target, so one cannot carry state into the next. */
    public JDAEExpander<?> instantiate(Spec spec, String annotationClassName) {
        try {
            Constructor<? extends JDAEExpander<?>> constructor = spec.expander().getDeclaredConstructor();
            constructor.setAccessible(true);   // an expander beside its annotation needs no modifier
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new ExpansionException("The expander for @" + annotationClassName + ", "
                    + spec.expander().getName() + ", could not be constructed. It needs a no-argument constructor.", e);
        }
    }

    private Spec fromMetaAnnotation(String annotationClassName) {
        Expander expander = expanderOf(annotationClassName);
        if (expander == null) return null;
        Class<? extends JDAEExpander<?>> type;
        try {
            type = expander.value();
        } catch (TypeNotPresentException absent) {
            throw new ExpansionException("The expander @" + annotationClassName + " names is not on the compile classpath", absent);
        }
        return new Spec(type, expander.keepOriginal());
    }

    private boolean keepOriginal(String annotationClassName) {
        Expander expander = expanderOf(annotationClassName);
        return expander != null && expander.keepOriginal();
    }

    private Expander expanderOf(String annotationClassName) {
        try {
            return Class.forName(annotationClassName, false, loader).getAnnotation(Expander.class);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;   // an annotation this build cannot see cannot be one of ours
        }
    }

    private static String annotationOf(Class<?> expander) {
        for (Type implemented : expander.getGenericInterfaces()) {
            if (implemented instanceof ParameterizedType parameterized
                    && parameterized.getRawType() == JDAEExpander.class
                    && parameterized.getActualTypeArguments()[0] instanceof Class<?> annotation
                    && Annotation.class.isAssignableFrom(annotation)) {
                return annotation.getName();
            }
        }
        Class<?> parent = expander.getSuperclass();
        return parent == null || parent == Object.class ? null : annotationOf(parent);
    }
}
