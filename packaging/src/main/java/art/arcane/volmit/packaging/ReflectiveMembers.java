package art.arcane.volmit.packaging;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Set;

/**
 * Finds members resolved by name through {@code MethodHandles.Lookup} or {@code Class} reflection. ProGuard does not
 * treat a VarHandle or MethodHandle lookup string as a use, so a field whose only direct users were shrunk away
 * disappears while the static initializer that resolves it survives and fails at class init.
 */
final class ReflectiveMembers {
    private static final String LOOKUP = "java/lang/invoke/MethodHandles$Lookup";
    private static final String CLASS = "java/lang/Class";
    private static final Set<String> LOOKUP_FIELDS = Set.of("findVarHandle", "findStaticVarHandle", "findGetter",
            "findSetter", "findStaticGetter", "findStaticSetter");
    private static final Set<String> LOOKUP_METHODS = Set.of("findVirtual", "findStatic", "findSpecial");
    private static final Set<String> CLASS_FIELDS = Set.of("getField", "getDeclaredField");
    private static final Set<String> CLASS_METHODS = Set.of("getMethod", "getDeclaredMethod");
    private static final int MAX_LOCAL_HOPS = 4;

    private ReflectiveMembers() {
    }

    static void collect(ClassReader reader, Set<Member> members) {
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        for (MethodNode method : node.methods) {
            if (!hasLookup(method.instructions)) {
                continue;
            }
            Frame<SourceValue>[] frames;
            try {
                frames = new Analyzer<>(new SourceInterpreter()).analyze(node.name, method);
            } catch (AnalyzerException ignored) {
                continue;
            }
            InsnList instructions = method.instructions;
            for (int index = 0; index < instructions.size(); index++) {
                if (instructions.get(index) instanceof MethodInsnNode call && frames[index] != null) {
                    Member member = member(call, frames[index], instructions, frames);
                    if (member != null) {
                        members.add(member);
                    }
                }
            }
        }
    }

    static boolean identifier(String value) {
        if (value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasLookup(InsnList instructions) {
        for (AbstractInsnNode instruction : instructions) {
            if (instruction instanceof MethodInsnNode call && kind(call) != null) {
                return true;
            }
        }
        return false;
    }

    private static Kind kind(MethodInsnNode call) {
        Set<String> fields = call.owner.equals(LOOKUP) ? LOOKUP_FIELDS : call.owner.equals(CLASS) ? CLASS_FIELDS : Set.of();
        Set<String> methods = call.owner.equals(LOOKUP) ? LOOKUP_METHODS : call.owner.equals(CLASS) ? CLASS_METHODS : Set.of();
        return fields.contains(call.name) ? Kind.FIELD : methods.contains(call.name) ? Kind.METHOD : null;
    }

    private static Member member(MethodInsnNode call, Frame<SourceValue> frame, InsnList instructions,
                                 Frame<SourceValue>[] frames) {
        Kind kind = kind(call);
        if (kind == null) {
            return null;
        }
        int receiver = frame.getStackSize() - Type.getArgumentCount(call.desc) - 1;
        int ownerSlot = call.owner.equals(LOOKUP) ? receiver + 1 : receiver;
        String owner = owner(frame.getStack(ownerSlot), instructions, frames, MAX_LOCAL_HOPS);
        String name = string(frame.getStack(ownerSlot + 1), instructions, frames, MAX_LOCAL_HOPS);
        if (owner == null || name == null || !identifier(name)) {
            return null;
        }
        return new Member(owner.replace('/', '.'), name, kind == Kind.FIELD);
    }

    private static String owner(SourceValue value, InsnList instructions, Frame<SourceValue>[] frames, int hops) {
        AbstractInsnNode source = single(value);
        if (source instanceof LdcInsnNode ldc && ldc.cst instanceof Type type && type.getSort() == Type.OBJECT) {
            return type.getInternalName();
        }
        if (source instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals(CLASS) && call.name.equals("forName")) {
            Frame<SourceValue> frame = frames[instructions.indexOf(call)];
            if (frame == null) {
                return null;
            }
            String name = string(frame.getStack(frame.getStackSize() - Type.getArgumentCount(call.desc)),
                    instructions, frames, hops);
            return name == null ? null : name.replace('.', '/');
        }
        SourceValue stored = stored(source, instructions, frames, hops);
        return stored == null ? null : owner(stored, instructions, frames, hops - 1);
    }

    private static String string(SourceValue value, InsnList instructions, Frame<SourceValue>[] frames, int hops) {
        AbstractInsnNode source = single(value);
        if (source instanceof LdcInsnNode ldc && ldc.cst instanceof String text) {
            return text;
        }
        SourceValue stored = stored(source, instructions, frames, hops);
        return stored == null ? null : string(stored, instructions, frames, hops - 1);
    }

    private static SourceValue stored(AbstractInsnNode source, InsnList instructions, Frame<SourceValue>[] frames,
                                      int hops) {
        if (hops <= 0 || !(source instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD) {
            return null;
        }
        Frame<SourceValue> frame = frames[instructions.indexOf(load)];
        if (frame == null) {
            return null;
        }
        AbstractInsnNode store = single(frame.getLocal(load.var));
        if (!(store instanceof VarInsnNode write) || write.getOpcode() != Opcodes.ASTORE) {
            return null;
        }
        Frame<SourceValue> before = frames[instructions.indexOf(write)];
        return before == null ? null : before.getStack(before.getStackSize() - 1);
    }

    private static AbstractInsnNode single(SourceValue value) {
        return value != null && value.insns.size() == 1 ? value.insns.iterator().next() : null;
    }

    private enum Kind {
        FIELD,
        METHOD
    }

    record Member(String owner, String name, boolean field) {
        String rule() {
            return "-keepclassmembers class " + owner + " { *** " + name + (field ? "; }" : "(...); }");
        }
    }
}
