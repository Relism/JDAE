package dev.relism.jdae.core.bytecode;

import dev.relism.jdae.api.AnnotationDescriptor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Iterator;
import java.util.List;

/**
 * Low-level ASM logic to remove and add annotations on class/members.
 */
public class AnnotationRewriter {

    public byte[] rewrite(byte[] original, String ownerId, String sourceAnnotationClassName,
                          boolean removeOriginal, List<AnnotationDescriptor> toInject) {
        ClassNode cn = new ClassNode(Opcodes.ASM9);
        new ClassReader(original).accept(cn, ClassReader.SKIP_FRAMES);

        if (ownerId.equals(cn.name)) {
            if (removeOriginal) {
                removeAnnotation(cn.visibleAnnotations, sourceAnnotationClassName);
                removeAnnotation(cn.invisibleAnnotations, sourceAnnotationClassName);
            }
            if (cn.visibleAnnotations == null) cn.visibleAnnotations = new java.util.ArrayList<>();
            injectAnnotations(cn.visibleAnnotations, toInject);
        } else {
            rewriteMembers(cn, ownerId, sourceAnnotationClassName, removeOriginal, toInject);
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private void rewriteMembers(ClassNode cn, String ownerId, String sourceAnno, boolean removeOriginal,
                                List<AnnotationDescriptor> toInject) {
        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                String fid = cn.name + "#" + fn.name;
                if (fid.equals(ownerId)) {
                    if (removeOriginal) {
                        removeAnnotation(fn.visibleAnnotations, sourceAnno);
                        removeAnnotation(fn.invisibleAnnotations, sourceAnno);
                    }
                    if (fn.visibleAnnotations == null) fn.visibleAnnotations = new java.util.ArrayList<>();
                    injectAnnotations(fn.visibleAnnotations, toInject);
                }
            }
        }
        if (cn.methods != null) {
            for (MethodNode mn : cn.methods) {
                String mid = cn.name + "#" + mn.name + mn.desc;
                if (mid.equals(ownerId)) {
                    if (removeOriginal) {
                        removeAnnotation(mn.visibleAnnotations, sourceAnno);
                        removeAnnotation(mn.invisibleAnnotations, sourceAnno);
                    }
                    if (mn.visibleAnnotations == null) mn.visibleAnnotations = new java.util.ArrayList<>();
                    injectAnnotations(mn.visibleAnnotations, toInject);
                }
            }
        }
    }

    private void removeAnnotation(List<AnnotationNode> list, String className) {
        if (list == null) return;
        //String desc = Type.getDescriptor(descriptorToClass(className)); ?
        for (Iterator<AnnotationNode> it = list.iterator(); it.hasNext(); ) {
            AnnotationNode an = it.next();
            String annClassName = Type.getType(an.desc).getClassName();
            if (annClassName.equals(className)) {
                it.remove();
            }
        }
    }

    private void injectAnnotations(List<AnnotationNode> list, List<AnnotationDescriptor> toInject) {
        if (toInject == null || toInject.isEmpty()) return;
        if (list == null) throw new IllegalStateException("Target annotations list is null; ASM tree requires init");
        for (AnnotationDescriptor ad : toInject) {
            String desc = Type.getObjectType(ad.getAnnotationClassName().replace('.', '/')).getDescriptor();
            AnnotationNode an = new AnnotationNode(desc);
            if (ad.getValues() != null) {
                ad.getValues().forEach((k, v) -> {
                    if (an.values == null) an.values = new java.util.ArrayList<>();
                    an.values.add(k);
                    an.values.add(v);
                });
            }
            list.add(an);
        }
    }

    // No longer loads classes reflectively; descriptors are derived from class names.
}