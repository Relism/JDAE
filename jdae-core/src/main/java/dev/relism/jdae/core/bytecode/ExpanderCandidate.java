package dev.relism.jdae.core.bytecode;

import dev.relism.jdae.api.ClassInfo;
import dev.relism.jdae.api.FieldInfo;
import dev.relism.jdae.api.MethodInfo;
import dev.relism.jdae.api.TargetKind;
import org.objectweb.asm.tree.AnnotationNode;

public final class ExpanderCandidate {
    private final String ownerId;
    private final String annotationClassName;
    private final AnnotationNode rawAnnotation;
    private final TargetKind targetKind;
    private final ClassInfo classInfo;
    private final MethodInfo methodInfo;
    private final FieldInfo fieldInfo;

    public ExpanderCandidate(String ownerId, String annotationClassName, AnnotationNode rawAnnotation,
                             TargetKind targetKind, ClassInfo classInfo, MethodInfo methodInfo, FieldInfo fieldInfo) {
        this.ownerId = ownerId;
        this.annotationClassName = annotationClassName;
        this.rawAnnotation = rawAnnotation;
        this.targetKind = targetKind;
        this.classInfo = classInfo;
        this.methodInfo = methodInfo;
        this.fieldInfo = fieldInfo;
    }

    public String getOwnerId() { return ownerId; }
    public String getAnnotationClassName() { return annotationClassName; }
    public AnnotationNode getRawAnnotation() { return rawAnnotation; }

    public TargetKind getTargetKind() { return targetKind; }
    public ClassInfo getClassInfo() { return classInfo; }
    public MethodInfo getMethodInfo() { return methodInfo; }
    public FieldInfo getFieldInfo() { return fieldInfo; }
}