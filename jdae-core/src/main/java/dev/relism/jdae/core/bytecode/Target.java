package dev.relism.jdae.core.bytecode;

import dev.relism.jdae.api.ClassInfo;
import dev.relism.jdae.api.FieldInfo;
import dev.relism.jdae.api.MethodInfo;
import dev.relism.jdae.api.TargetKind;
import org.objectweb.asm.tree.AnnotationNode;

import java.util.List;

/**
 * One annotatable element of a class, with the annotations it carries.
 *
 * @param ownerId the class's internal name, or {@code <internal>#<field>} / {@code <internal>#<method><desc>}
 */
public record Target(String ownerId, TargetKind kind, ClassInfo classInfo, MethodInfo methodInfo, FieldInfo fieldInfo,
                     List<AnnotationNode> annotations) {}
