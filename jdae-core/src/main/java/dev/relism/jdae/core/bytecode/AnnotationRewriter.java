// language: java
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

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

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

        ClassWriter cw = new ContextClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    public byte[] rewrite(byte[] original, String ownerId, List<String> sourceAnnotationClassNames,
                          boolean removeOriginal, List<AnnotationDescriptor> toInject) {
        boolean anyRemoval = removeOriginal && sourceAnnotationClassNames != null && !sourceAnnotationClassNames.isEmpty();
        boolean anyInjection = toInject != null && !toInject.isEmpty();
        if (!anyRemoval && !anyInjection) {
            return original; // no-op, avoid unnecessary writes
        }

        ClassNode cn = new ClassNode(Opcodes.ASM9);
        new ClassReader(original).accept(cn, ClassReader.SKIP_FRAMES);

        if (ownerId.equals(cn.name)) {
            if (removeOriginal) {
                for (String s : sourceAnnotationClassNames) {
                    removeAnnotation(cn.visibleAnnotations, s);
                    removeAnnotation(cn.invisibleAnnotations, s);
                }
            }
            if (cn.visibleAnnotations == null) cn.visibleAnnotations = new java.util.ArrayList<>();
            injectAnnotations(cn.visibleAnnotations, toInject);
        } else {
            rewriteMembers(cn, ownerId, sourceAnnotationClassNames, removeOriginal, toInject);
        }

        ClassWriter cw = new ContextClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
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

    private void rewriteMembers(ClassNode cn, String ownerId, List<String> sourceAnnos, boolean removeOriginal,
                                List<AnnotationDescriptor> toInject) {
        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                String fid = cn.name + "#" + fn.name;
                if (fid.equals(ownerId)) {
                    if (removeOriginal) {
                        for (String s : sourceAnnos) {
                            removeAnnotation(fn.visibleAnnotations, s);
                            removeAnnotation(fn.invisibleAnnotations, s);
                        }
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
                        for (String s : sourceAnnos) {
                            removeAnnotation(mn.visibleAnnotations, s);
                            removeAnnotation(mn.invisibleAnnotations, s);
                        }
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
            AnnotationNode an = toAnnotationNode(ad);
            list.add(an);
        }
    }


    private AnnotationNode toAnnotationNode(AnnotationDescriptor ad) {
        String desc = Type.getObjectType(ad.getAnnotationClassName().replace('.', '/')).getDescriptor();
        AnnotationNode an = new AnnotationNode(desc);
        ad.getValues().forEach((k, v) -> {
            if (an.values == null) an.values = new ArrayList<>();
            an.values.add(k);
            an.values.add(toAsmValue(v));
        });
        return an;
    }

    private Object toAsmValue(Object v) {
        switch (v) {
            case null -> {
                return null;
            }
            case AnnotationDescriptor annotationDescriptor -> {
                return toAnnotationNode(annotationDescriptor);
            }
            case List<?> in -> {
                List<Object> out = new ArrayList<>(in.size());
                for (Object o : in) {
                    out.add(toAsmValue(o));
                }
                return out;
            }
            default -> {
            }
        }
        if (v.getClass().isArray()) {
            int len = Array.getLength(v);
            List<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                Object elem = Array.get(v, i);
                out.add(toAsmValue(elem));
            }
            return out;
        }
        switch (v) {
            case Class<?> aClass -> {
                return Type.getType(aClass);
            }
            case Type type -> {
                return v; // Already an ASM Type
            }
            case Enum<?> e -> {
                String enumDesc = Type.getObjectType(e.getDeclaringClass().getName().replace('.', '/')).getDescriptor();
                return new String[]{enumDesc, e.name()};
            }
            default -> {
            }
        }
        return v;
    }

    /**
     * ClassWriter that resolves classes using the current thread context classloader.
     * This avoids ClassNotFoundException when ASM attempts to compute common superclasses
     * while running inside the plugin classloader.
     */
    private static final class ContextClassWriter extends ClassWriter {
        ContextClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                String name1 = type1.replace('/', '.');
                String name2 = type2.replace('/', '.');
                Class<?> c1 = Class.forName(name1, false, cl);
                Class<?> c2 = Class.forName(name2, false, cl);
                if (c1.isAssignableFrom(c2)) {
                    return type1;
                }
                if (c2.isAssignableFrom(c1)) {
                    return type2;
                }
                if (c1.isInterface() || c2.isInterface()) {
                    return "java/lang/Object";
                }
                Class<?> superClass = c1;
                while (!superClass.isAssignableFrom(c2)) {
                    superClass = superClass.getSuperclass();
                }
                return superClass.getName().replace('.', '/');
            } catch (Throwable t) {
                // Fallback to Object if any class cannot be loaded
                return "java/lang/Object";
            }
        }
    }
}