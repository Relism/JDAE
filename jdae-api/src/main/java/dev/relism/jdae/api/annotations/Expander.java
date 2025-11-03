package dev.relism.jdae.api.annotations;

import dev.relism.jdae.api.JDAEExpander;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation applied to annotation types that should be expanded by JDAE.
 * Example:
 * <pre>
 * @Expander(value = MyExpander.class, keepOriginal = false)
 * public @interface ListResponse { Class<?> value(); }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Expander {
    Class<? extends JDAEExpander<?>> value();
    boolean keepOriginal() default true;
    String id() default "";
}