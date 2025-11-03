package dev.relism.jdae.processor;

import dev.relism.jdae.api.annotations.Expander;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class JDAEValidationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Simple validation: warn if annotation types lack @Expander mapping
        for (Element e : roundEnv.getRootElements()) {
            // This is a placeholder; a full implementation would scan all annotation types and ensure @Expander is used correctly
        }
        return false;
    }
}