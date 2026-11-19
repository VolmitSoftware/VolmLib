package art.arcane.volmit.packaging;

import org.objectweb.asm.ClassReader;
import proguard.classfile.Clazz;
import proguard.classfile.Member;
import proguard.classfile.Method;
import proguard.classfile.ProgramClass;
import proguard.classfile.ProgramMethod;
import proguard.classfile.attribute.Attribute;
import proguard.classfile.attribute.CodeAttribute;
import proguard.classfile.attribute.UnknownAttribute;
import proguard.classfile.attribute.visitor.AllAttributeVisitor;
import proguard.classfile.attribute.visitor.AttributeVisitor;
import proguard.classfile.editor.AttributesEditor;
import proguard.classfile.editor.ConstantPoolShrinker;
import proguard.classfile.editor.ConstantPoolSorter;
import proguard.classfile.io.ProgramClassReader;
import proguard.classfile.io.ProgramClassWriter;
import proguard.classfile.visitor.MemberVisitor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public final class LocalVariableStripper {
    private static final String LOCAL_VARIABLES = "LocalVariableTable";
    private static final String LOCAL_VARIABLE_TYPES = "LocalVariableTypeTable";
    private static final String PARAMETERS = "MethodParameters";

    private LocalVariableStripper() {
    }

    public static byte[] strip(byte[] original, boolean localVariables, boolean parameterNames) throws IOException {
        try {
            ClassReader reader = new ClassReader(original);
            if (!carriesTables(reader, original.length, localVariables, parameterNames)) {
                return original;
            }
            ProgramClass type = new ProgramClass();
            type.accept(new ProgramClassReader(new DataInputStream(new ByteArrayInputStream(original))));
            Set<String> unknownAttributes = unknownAttributes(type);
            if (!unknownAttributes.isEmpty()) {
                throw new IOException("Cannot rewrite debug metadata in " + reader.getClassName()
                        + " with unknown attributes: " + unknownAttributes);
            }
            type.methodsAccept(new TableRemover(localVariables, parameterNames));
            // The ProGuard shrink leaves pools in this order; it compresses better than javac's or ASM's.
            type.accept(new ConstantPoolShrinker());
            type.accept(new ConstantPoolSorter());
            ByteArrayOutputStream output = new ByteArrayOutputStream(original.length);
            try (DataOutputStream data = new DataOutputStream(output)) {
                type.accept(new ProgramClassWriter(data));
            }
            return output.toByteArray();
        } catch (RuntimeException failure) {
            throw new IOException("Invalid class while stripping debug metadata.", failure);
        }
    }

    private static Set<String> unknownAttributes(ProgramClass type) {
        Set<String> names = new LinkedHashSet<>();
        type.accept(new AllAttributeVisitor(true, new AttributeVisitor() {
            @Override
            public void visitAnyAttribute(Clazz owner, Attribute attribute) {
            }

            @Override
            public void visitUnknownAttribute(Clazz owner, UnknownAttribute attribute) {
                names.add(attribute.getAttributeName(owner));
            }
        }));
        return names;
    }

    private static boolean carriesTables(ClassReader reader, int length, boolean localVariables, boolean parameterNames)
            throws IOException {
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
                String name = reader.readUTF8(position, characters);
                if (parameterNames && PARAMETERS.equals(name)
                        || localVariables && "Code".equals(name)
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
            if (LOCAL_VARIABLES.equals(name) || LOCAL_VARIABLE_TYPES.equals(name)) {
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

    private static void deleteAll(AttributesEditor editor, String name) {
        while (editor.findAttribute(name) != null) {
            editor.deleteAttribute(name);
        }
    }

    private record TableRemover(boolean localVariables, boolean parameterNames) implements MemberVisitor, AttributeVisitor {
        @Override
        public void visitAnyMember(Clazz owner, Member member) {
        }

        @Override
        public void visitProgramMethod(ProgramClass owner, ProgramMethod method) {
            if (parameterNames) {
                deleteAll(new AttributesEditor(owner, method, false), PARAMETERS);
            }
            if (localVariables) {
                method.attributesAccept(owner, this);
            }
        }

        @Override
        public void visitAnyAttribute(Clazz owner, Attribute attribute) {
        }

        @Override
        public void visitCodeAttribute(Clazz owner, Method method, CodeAttribute code) {
            AttributesEditor editor = new AttributesEditor((ProgramClass) owner, (ProgramMethod) method, code, false);
            deleteAll(editor, LOCAL_VARIABLES);
            deleteAll(editor, LOCAL_VARIABLE_TYPES);
        }
    }
}
