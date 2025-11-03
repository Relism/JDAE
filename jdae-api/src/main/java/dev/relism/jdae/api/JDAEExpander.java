package dev.relism.jdae.api;

/**
 * Implemented by all expanders. The type parameter A is the source annotation type.
 */
public interface JDAEExpander<A> {
    void expand(ExpansionContext ctx, A annotationInstance);
}