package dev.relism.jdae.core.bytecode;

import dev.relism.jdae.api.ClassInfo;
import dev.relism.jdae.api.FieldInfo;
import dev.relism.jdae.api.MethodInfo;
import dev.relism.jdae.api.TargetKind;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans bytecode to find members annotated with annotations marked by @Expander.
 */
public class ClassScanner {

    public List<ExpanderCandidate> scan(byte[] classBytes) {
        ClassNode cn = new ClassNode(Opcodes.ASM9);
        new ClassReader(classBytes).accept(cn, ClassReader.SKIP_FRAMES);

        List<ExpanderCandidate> candidates = new ArrayList<>();

        ClassInfo classInfo = new ClassInfo(cn.name, cn.name.replace('/', '.'), cn.access,
                cn.superName != null ? cn.superName.replace('/', '.') : null,
                cn.interfaces != null ? cn.interfaces.stream().map(i -> i.replace('/', '.')).toList() : List.of());

        addCandidatesFromAnnotations(candidates, classInfo, null, null, TargetKind.CLASS, cn.visibleAnnotations);
        addCandidatesFromAnnotations(candidates, classInfo, null, null, TargetKind.CLASS, cn.invisibleAnnotations);

        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                FieldInfo fieldInfo = new FieldInfo(fn.name, fn.desc, fn.access);
                String ownerId = cn.name + "#" + fn.name;
                addCandidatesFromAnnotations(candidates, classInfo, null, fieldInfo, TargetKind.FIELD, fn.visibleAnnotations);
                addCandidatesFromAnnotations(candidates, classInfo, null, fieldInfo, TargetKind.FIELD, fn.invisibleAnnotations);
            }
        }
        if (cn.methods != null) {
            for (MethodNode mn : cn.methods) {
                MethodInfo methodInfo = new MethodInfo(
                        mn.name,
                        mn.desc,
                        mn.access,
                        Type.getArgumentTypes(mn.desc) != null ?
                                java.util.Arrays.stream(Type.getArgumentTypes(mn.desc))
                                        .map(t -> t.getClassName())
                                        .toList() : List.of(),
                        Type.getReturnType(mn.desc) != null ? Type.getReturnType(mn.desc).getClassName() : "void"
                );
                String ownerId = cn.name + "#" + mn.name + mn.desc;
                addCandidatesFromAnnotations(candidates, classInfo, methodInfo, null, TargetKind.METHOD, mn.visibleAnnotations);
                addCandidatesFromAnnotations(candidates, classInfo, methodInfo, null, TargetKind.METHOD, mn.invisibleAnnotations);
            }
        }
        return candidates;
    }

    private void addCandidatesFromAnnotations(List<ExpanderCandidate> out,
                                              ClassInfo classInfo,
                                              MethodInfo methodInfo,
                                              FieldInfo fieldInfo,
                                              TargetKind kind,
                                              List<AnnotationNode> anns) {
        if (anns == null) return;
        for (AnnotationNode an : anns) {
            String desc = an.desc; // e.g., Lcom/example/MyAnnotation;
            String annotationClassName = Type.getType(desc).getClassName();
            String ownerId = switch (kind) {
                case CLASS -> classInfo.getInternalName();
                case METHOD -> classInfo.getInternalName() + "#" + methodInfo.getName() + methodInfo.getDescriptor();
                case FIELD -> classInfo.getInternalName() + "#" + fieldInfo.getName();
            };
            out.add(new ExpanderCandidate(ownerId, annotationClassName, an, kind, classInfo, methodInfo, fieldInfo));
        }
    }

    /*
    public static byte[] readClass(Path p) throws IOException {
        return Files.readAllBytes(p);
    }
     */
}