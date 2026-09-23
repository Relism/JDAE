package dev.relism.jdae.api;

import java.lang.annotation.Annotation;

/**
 * Turns one annotation into the annotations it stands for.
 *
 * <p>Implementations need a no-argument constructor and are found either through the
 * {@link dev.relism.jdae.api.annotations.Expander @Expander} on the annotation type or through
 * {@link java.util.ServiceLoader}. They run at build time, once per annotated target.
 *
 * @param <A> the annotation this expands
 */
public interface JDAEExpander<A extends Annotation> {
    void expand(ExpansionContext ctx, A annotation);
}
