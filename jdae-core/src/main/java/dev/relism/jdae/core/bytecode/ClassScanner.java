package dev.relism.jdae.core.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        addCandidatesFromAnnotations(candidates, cn.name, cn.visibleAnnotations);
        addCandidatesFromAnnotations(candidates, cn.name, cn.invisibleAnnotations);

        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                addCandidatesFromAnnotations(candidates, cn.name + "#" + fn.name, fn.visibleAnnotations);
                addCandidatesFromAnnotations(candidates, cn.name + "#" + fn.name, fn.invisibleAnnotations);
            }
        }
        if (cn.methods != null) {
            for (MethodNode mn : cn.methods) {
                addCandidatesFromAnnotations(candidates, cn.name + "#" + mn.name + mn.desc, mn.visibleAnnotations);
                addCandidatesFromAnnotations(candidates, cn.name + "#" + mn.name + mn.desc, mn.invisibleAnnotations);
            }
        }
        return candidates;
    }

    private void addCandidatesFromAnnotations(List<ExpanderCandidate> out, String ownerId, List<AnnotationNode> anns) {
        if (anns == null) return;
        for (AnnotationNode an : anns) {
            String desc = an.desc; // e.g., Lcom/example/MyAnnotation;
            String annotationClassName = Type.getType(desc).getClassName();
            // We cannot load actual annotation type here; registry will verify @Expander presence using reflection/classpath
            out.add(new ExpanderCandidate(ownerId, annotationClassName, an));
        }
    }

    public static byte[] readClass(Path p) throws IOException {
        return Files.readAllBytes(p);
    }
}