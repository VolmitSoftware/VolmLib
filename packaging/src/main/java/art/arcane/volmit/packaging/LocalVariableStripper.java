package art.arcane.volmit.packaging;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public final class LocalVariableStripper {
    private LocalVariableStripper() {
    }

    public static byte[] strip(byte[] original) throws IOException {
        try {
            ClassReader reader = new ClassReader(original);
            if (!hasLocalTables(reader, original.length)) {
                return original;
            }
            ClassWriter writer = new ClassWriter(0);
            StrippingVisitor visitor = new StrippingVisitor(writer);
            reader.accept(visitor, 0);
            if (!visitor.unknownAttributes.isEmpty()) {
                throw new IOException("Cannot rewrite local-variable metadata in " + reader.getClassName()
                        + " with unknown attributes: " + visitor.unknownAttributes);
            }
            return writer.toByteArray();
        } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
            throw new IOException("Invalid class while stripping local-variable metadata.", failure);
        }
    }

    private static boolean hasLocalTables(ClassReader reader, int length) throws IOException {
        char[] characters = new char[reader.getMaxStringLength()];
        int position = reader.header + 6;
        position += 2 + reader.readUnsignedShort(position) * 2;
        int fields = reader.readUnsignedShort(position);
        position += 2;
        for (int field = 0; field < fields; field++) {
            position = skipMember(reader, position, length);
        }
        int methods = reader.readUnsignedShort(position);
        position += 2;
        for (int method = 0; method < methods; method++) {
            int attributes = reader.readUnsignedShort(position + 6);
            position += 8;
            for (int attribute = 0; attribute < attributes; attribute++) {
                int end = attributeEnd(reader, position, length);
                if ("Code".equals(reader.readUTF8(position, characters))
                        && codeHasLocalTables(reader, position + 6, end, characters)) {
                    return true;
                }
                position = end;
            }
        }
        return false;
    }

    private static int skipMember(ClassReader reader, int position, int length) throws IOException {
        int attributes = reader.readUnsignedShort(position + 6);
        position += 8;
        for (int attribute = 0; attribute < attributes; attribute++) {
            position = attributeEnd(reader, position, length);
        }
        return position;
    }

    private static boolean codeHasLocalTables(ClassReader reader, int position, int end, char[] characters)
            throws IOException {
        int codeLength = reader.readInt(position + 4);
        if (codeLength < 0 || codeLength > end - position - 8) {
            throw new IOException("Invalid class code length.");
        }
        position += 8 + codeLength;
        position += 2 + reader.readUnsignedShort(position) * 8;
        int attributes = reader.readUnsignedShort(position);
        position += 2;
        for (int attribute = 0; attribute < attributes; attribute++) {
            int next = attributeEnd(reader, position, end);
            String name = reader.readUTF8(position, characters);
            if ("LocalVariableTable".equals(name) || "LocalVariableTypeTable".equals(name)) {
                return true;
            }
            position = next;
        }
        return false;
    }

    private static int attributeEnd(ClassReader reader, int position, int limit) throws IOException {
        if (position < 0 || position > limit - 6) {
            throw new IOException("Invalid class attribute offset.");
        }
        int size = reader.readInt(position + 2);
        if (size < 0 || size > limit - position - 6) {
            throw new IOException("Invalid class attribute length.");
        }
        return position + 6 + size;
    }

    private static final class StrippingVisitor extends ClassVisitor {
        private final Set<String> unknownAttributes = new LinkedHashSet<>();

        private StrippingVisitor(ClassWriter writer) {
            super(Opcodes.ASM9, writer);
        }

        @Override
        public void visitAttribute(Attribute attribute) {
            unknownAttributes.add(attribute.type);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(Opcodes.ASM9, super.visitField(access, name, descriptor, signature, value)) {
                @Override
                public void visitAttribute(Attribute attribute) {
                    unknownAttributes.add(attribute.type);
                }
            };
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            return new RecordComponentVisitor(Opcodes.ASM9, super.visitRecordComponent(name, descriptor, signature)) {
                @Override
                public void visitAttribute(Attribute attribute) {
                    unknownAttributes.add(attribute.type);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                @Override
                public void visitLocalVariable(String localName, String localDescriptor, String localSignature,
                                               Label start, Label end, int index) {
                }

                @Override
                public void visitAttribute(Attribute attribute) {
                    unknownAttributes.add(attribute.type);
                }
            };
        }
    }
}
