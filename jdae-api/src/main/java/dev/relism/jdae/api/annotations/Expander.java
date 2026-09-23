package dev.relism.jdae.api.annotations;

import dev.relism.jdae.api.JDAEExpander;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an annotation type as one JDAE expands, and names the expander that does it.
 *
 * <pre>
 * &#64;Expander(ListResponseExpander.class)
 * public &#64;interface ListResponse { Class&lt;?&gt; value(); }
 * </pre>
 *
 * <p>The annotation is removed from the bytecode once it has been expanded, which is what makes a
 * build repeatable without a {@code clean}. Keep it with {@code keepOriginal = true} when something
 * reads it at runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Expander {

    Class<? extends JDAEExpander<?>> value();

    boolean keepOriginal() default false;
}
