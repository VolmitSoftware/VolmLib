package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalVariableStripperTest {
    @Test
    void preservesInstructionsAnnotationsParametersAndSourceLocations() throws Exception {
        byte[] original = fixture(true);

        byte[] stripped = LocalVariableStripper.strip(original);

        assertTrue(stripped.length < original.length);
        assertEquals(metadata(original), metadata(stripped));
        assertEquals(1, localCount(original));
        assertEquals(0, localCount(stripped));
        assertFalse(new String(stripped, StandardCharsets.ISO_8859_1).contains("unusedDebuggerLocalName"));
        Class<?> before = new FixtureLoader().define(original);
        Class<?> after = new FixtureLoader().define(stripped);
        for (Class<?> type : List.of(before, after)) {
            Method method = type.getMethod("echo", List.class);
            assertEquals("value", method.getParameters()[0].getName());
            assertTrue(method.getParameters()[0].isNamePresent());
            assertTrue(method.isAnnotationPresent(RuntimeMarker.class));
            assertEquals("preserved", method.getParameters()[0].getAnnotation(RuntimeMarker.class).name());
            assertEquals("java.util.List<java.lang.String>", method.getGenericReturnType().getTypeName());
            List<String> input = List.of("retained");
            assertSame(input, method.invoke(null, input));
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(null, new Object[]{null}));
            assertEquals(IllegalArgumentException.class, failure.getCause().getClass());
            assertEquals("missing value", failure.getCause().getMessage());
            assertEquals("Fixture.java", failure.getCause().getStackTrace()[0].getFileName());
            assertEquals(42, failure.getCause().getStackTrace()[0].getLineNumber());
        }
        assertSame(stripped, LocalVariableStripper.strip(stripped));
    }

    @Test
    void returnsOriginalBytesForClassesWithoutLocalTables() throws Exception {
        byte[] original = fixture(false);

        assertSame(original, LocalVariableStripper.strip(original));
    }

    @Test
    void doesNotMistakeAttributeNamesInConstantsForLocalTables() throws Exception {
        ClassWriter writer = baseClass();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "NAME",
                "Ljava/lang/String;", null, "LocalVariableTable").visitEnd();
        writer.visitEnd();
        byte[] original = writer.toByteArray();

        assertSame(original, LocalVariableStripper.strip(original));
    }

    @Test
    void stripsAnIndependentLocalVariableTypeTable() throws Exception {
        ClassWriter writer = baseClass();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "empty", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitAttribute(new EmptyLocalTypeTable());
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        byte[] original = writer.toByteArray();
        assertTrue(new String(original, StandardCharsets.ISO_8859_1).contains("LocalVariableTypeTable"));

        byte[] stripped = LocalVariableStripper.strip(original);

        assertFalse(new String(stripped, StandardCharsets.ISO_8859_1).contains("LocalVariableTypeTable"));
        assertEquals(metadata(original), metadata(stripped));
    }

    @Test
    void rejectsRewritingUnknownAttributesWithoutChangingInput() throws Exception {
        for (String location : List.of("class", "field", "method", "code")) {
            byte[] original = fixture(true, location);
            byte[] unchanged = original.clone();

            IOException failure = assertThrows(IOException.class, () -> LocalVariableStripper.strip(original));

            assertTrue(failure.getMessage().contains("PrivatePayload"));
            assertArrayEquals(unchanged, original);
            byte[] noLocals = fixture(false, location);
            assertSame(noLocals, LocalVariableStripper.strip(noLocals));
        }
    }

    @Test
    void rejectsMalformedClasses() {
        assertThrows(IOException.class, () -> LocalVariableStripper.strip(new byte[]{1, 2, 3}));
    }

    static byte[] fixture(boolean locals) {
        return fixture(locals, "");
    }

    static byte[] fixture(boolean locals, String unknownLocation) {
        ClassWriter writer = baseClass();
        if (unknownLocation.equals("class")) {
            writer.visitAttribute(new PrivatePayload(false));
        }
        FieldVisitor field = writer.visitField(Opcodes.ACC_PRIVATE, "names", "Ljava/util/List;",
                "Ljava/util/List<Ljava/lang/String;>;", null);
        field.visitAnnotation(Type.getDescriptor(RuntimeMarker.class), true).visitEnd();
        if (unknownLocation.equals("field")) {
            field.visitAttribute(new PrivatePayload(false));
        }
        field.visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "echo",
                "(Ljava/util/List;)Ljava/util/List;",
                "(Ljava/util/List<Ljava/lang/String;>;)Ljava/util/List<Ljava/lang/String;>;", null);
        method.visitParameter("value", 0);
        method.visitAnnotation(Type.getDescriptor(RuntimeMarker.class), true).visitEnd();
        method.visitAnnotation("Llombok/Generated;", false).visitEnd();
        AnnotationVisitor annotation = method.visitParameterAnnotation(0, Type.getDescriptor(RuntimeMarker.class), true);
        annotation.visit("name", "preserved");
        annotation.visitEnd();
        if (unknownLocation.equals("method")) {
            method.visitAttribute(new PrivatePayload(false));
        }
        method.visitCode();
        Label start = new Label();
        Label present = new Label();
        Label end = new Label();
        method.visitLabel(start);
        method.visitLineNumber(42, start);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitJumpInsn(Opcodes.IFNONNULL, present);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException");
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("missing value");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>",
                "(Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.ATHROW);
        method.visitLabel(present);
        method.visitLineNumber(43, present);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(end);
        if (locals) {
            method.visitLocalVariable("unusedDebuggerLocalName", "Ljava/util/List;",
                    "Ljava/util/List<Ljava/lang/String;>;", start, end, 0);
        }
        if (unknownLocation.equals("code")) {
            method.visitAttribute(new PrivatePayload(true));
        }
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    static int localCount(byte[] bytes) {
        int[] count = {0};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLocalVariable(String name, String descriptor, String signature,
                                                   Label start, Label end, int index) {
                        count[0]++;
                    }
                };
            }
        }, 0);
        return count[0];
    }

    private static ClassWriter baseClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Fixture", null, "java/lang/Object", null);
        writer.visitSource("Fixture.java", null);
        return writer;
    }

    private static List<String> metadata(byte[] bytes) {
        List<String> values = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                              String[] interfaces) {
                values.add("class:" + version + ":" + access + ":" + name + ":" + signature + ":" + superName);
            }
            @Override
            public void visitSource(String source, String debug) {
                values.add("source:" + source + ":" + debug);
            }
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                values.add("field:" + access + ":" + name + ":" + descriptor + ":" + signature + ":" + value);
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        return annotation(values, descriptor, visible);
                    }
                };
            }
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                values.add("method:" + access + ":" + name + ":" + descriptor + ":" + signature);
                return new MetadataMethodVisitor(values);
            }
        }, 0);
        return values;
    }

    private static AnnotationVisitor annotation(List<String> values, String descriptor, boolean visible) {
        values.add("annotation:" + descriptor + ":" + visible);
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                values.add("annotationValue:" + name + ":" + value);
            }
        };
    }

    private static final class MetadataMethodVisitor extends MethodVisitor {
        private final List<String> values;

        private MetadataMethodVisitor(List<String> values) {
            super(Opcodes.ASM9);
            this.values = values;
        }

        @Override
        public void visitParameter(String name, int access) {
            values.add("parameter:" + name + ":" + access);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return annotation(values, descriptor, visible);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            values.add("annotatedParameter:" + parameter);
            return annotation(values, descriptor, visible);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            values.add("line:" + line);
        }

        @Override
        public void visitInsn(int opcode) {
            values.add("instruction:" + opcode);
        }

        @Override
        public void visitVarInsn(int opcode, int slot) {
            values.add("variableInstruction:" + opcode + ":" + slot);
        }

        @Override
        public void visitJumpInsn(int opcode, Label target) {
            values.add("jump:" + opcode);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            values.add("typeInstruction:" + opcode + ":" + type);
        }

        @Override
        public void visitLdcInsn(Object value) {
            values.add("constant:" + value);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            values.add("methodInstruction:" + opcode + ":" + owner + ":" + name + ":" + descriptor + ":" + isInterface);
        }
    }

    private static final class FixtureLoader extends ClassLoader {
        private FixtureLoader() {
            super(LocalVariableStripperTest.class.getClassLoader());
        }

        private Class<?> define(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface RuntimeMarker {
        String name() default "";
    }

    private static final class PrivatePayload extends Attribute {
        private final boolean code;

        private PrivatePayload(boolean code) {
            super("PrivatePayload");
            this.code = code;
        }

        @Override
        public boolean isCodeAttribute() {
            return code;
        }

        @Override
        protected ByteVector write(ClassWriter writer, byte[] code, int length, int stack, int locals) {
            return new ByteVector().putShort(writer.newUTF8("attributeConstantPoolReference"));
        }
    }

    private static final class EmptyLocalTypeTable extends Attribute {
        private EmptyLocalTypeTable() {
            super("LocalVariableTypeTable");
        }

        @Override
        public boolean isCodeAttribute() {
            return true;
        }

        @Override
        protected ByteVector write(ClassWriter writer, byte[] code, int length, int stack, int locals) {
            return new ByteVector().putShort(0);
        }
    }
}
