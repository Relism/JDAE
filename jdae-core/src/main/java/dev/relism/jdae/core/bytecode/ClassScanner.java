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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reads a class file into the targets an expansion can act on. */
public final class ClassScanner {

    public List<Target> scan(byte[] classBytes) {
        ClassNode cn = new ClassNode(Opcodes.ASM9);
        new ClassReader(classBytes).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        ClassInfo classInfo = new ClassInfo(cn.name, cn.name.replace('/', '.'), cn.access,
                cn.superName == null ? null : cn.superName.replace('/', '.'),
                cn.interfaces == null ? List.of() : cn.interfaces.stream().map(i -> i.replace('/', '.')).toList());

        List<Target> targets = new ArrayList<>();
        targets.add(new Target(cn.name, TargetKind.CLASS, classInfo, null, null,
                both(cn.visibleAnnotations, cn.invisibleAnnotations)));

        if (cn.fields != null) {
            cn.fields.forEach(f -> targets.add(new Target(cn.name + "#" + f.name, TargetKind.FIELD, classInfo, null,
                    new FieldInfo(f.name, f.desc, f.access), both(f.visibleAnnotations, f.invisibleAnnotations))));
        }
        if (cn.methods != null) {
            cn.methods.forEach(m -> targets.add(new Target(cn.name + "#" + m.name + m.desc, TargetKind.METHOD, classInfo,
                    new MethodInfo(m.name, m.desc, m.access,
                            Arrays.stream(Type.getArgumentTypes(m.desc)).map(Type::getClassName).toList(),
                            Type.getReturnType(m.desc).getClassName()),
                    null, both(m.visibleAnnotations, m.invisibleAnnotations))));
        }
        return targets;
    }

    /** The name an ASM descriptor stands for, e.g. {@code Lcom/acme/A;} to {@code com.acme.A}. */
    public static String nameOf(AnnotationNode node) {
        return Type.getType(node.desc).getClassName();
    }

    private static List<AnnotationNode> both(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        if (visible == null && invisible == null) return List.of();
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null) all.addAll(visible);
        if (invisible != null) all.addAll(invisible);
        return all;
    }
}
