package art.arcane.volmit.packaging;

import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import proguard.classfile.Clazz;
import proguard.classfile.ProgramClass;
import proguard.classfile.attribute.Attribute;
import proguard.classfile.attribute.InnerClassesAttribute;
import proguard.classfile.attribute.InnerClassesInfo;
import proguard.classfile.attribute.visitor.AttributeVisitor;
import proguard.classfile.editor.ConstantPoolEditor;
import proguard.classfile.editor.ConstantPoolShrinker;
import proguard.classfile.editor.ConstantPoolSorter;
import proguard.classfile.io.ProgramClassReader;
import proguard.classfile.io.ProgramClassWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;

/**
 * ProGuard's renaming pass rewrites every InnerClasses simple name from the binary name, so a local class
 * {@code Outer$1Local} would report {@code getSimpleName()} as "1Local". This puts back the compiler's names.
 */
final class InnerClassNames {
    private InnerClassNames() {
    }

    static Map<String, String> read(File archive) throws IOException {
        Map<String, String> names = new HashMap<>();
        try (ZipFile zip = ZipFile.builder().setFile(archive).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitInnerClass(String name, String outerName, String innerName, int access) {
                            if (innerName != null) {
                                names.putIfAbsent(name, innerName);
                            }
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException failure) {
                    throw new IOException("Cannot read inner class names of " + entry.getName(), failure);
                }
            }
        }
        return names;
    }

    static int restore(File shrunk, Map<String, String> names) throws IOException {
        Path target = shrunk.toPath();
        Path temporary = target.resolveSibling(shrunk.getName() + ".names");
        int repaired = 0;
        try (ZipFile input = ZipFile.builder().setFile(shrunk).get();
             ZipArchiveOutputStream output = new ZipArchiveOutputStream(temporary.toFile())) {
            output.setLevel(9);
            output.setUseZip64(Zip64Mode.AsNeeded);
            Enumeration<ZipArchiveEntry> entries = input.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                byte[] repairedClass = null;
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try (InputStream stream = input.getInputStream(entry)) {
                        repairedClass = repair(stream.readAllBytes(), names);
                    }
                }
                if (repairedClass == null) {
                    try (InputStream raw = input.getRawInputStream(entry)) {
                        output.addRawArchiveEntry(entry, raw);
                    }
                    continue;
                }
                repaired++;
                ZipArchiveEntry copy = new ZipArchiveEntry(entry.getName());
                copy.setTime(entry.getTime());
                copy.setMethod(ZipEntry.DEFLATED);
                output.putArchiveEntry(copy);
                output.write(repairedClass);
                output.closeArchiveEntry();
            }
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
        if (repaired == 0) {
            Files.deleteIfExists(temporary);
        } else {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return repaired;
    }

    private static byte[] repair(byte[] original, Map<String, String> names) {
        ProgramClass type = new ProgramClass();
        type.accept(new ProgramClassReader(new DataInputStream(new ByteArrayInputStream(original))));
        boolean[] changed = {false};
        type.attributesAccept(new AttributeVisitor() {
            @Override
            public void visitAnyAttribute(Clazz owner, Attribute attribute) {
            }

            @Override
            public void visitInnerClassesAttribute(Clazz owner, InnerClassesAttribute attribute) {
                for (int index = 0; index < attribute.u2classesCount; index++) {
                    InnerClassesInfo info = attribute.classes[index];
                    if (info.u2innerClassIndex == 0 || info.u2innerNameIndex == 0) {
                        continue;
                    }
                    String expected = names.get(owner.getClassName(info.u2innerClassIndex));
                    if (expected != null && !expected.equals(owner.getString(info.u2innerNameIndex))) {
                        info.u2innerNameIndex = new ConstantPoolEditor((ProgramClass) owner).addUtf8Constant(expected);
                        changed[0] = true;
                    }
                }
            }
        });
        if (!changed[0]) {
            return null;
        }
        type.accept(new ConstantPoolShrinker());
        type.accept(new ConstantPoolSorter());
        ByteArrayOutputStream output = new ByteArrayOutputStream(original.length);
        try (DataOutputStream data = new DataOutputStream(output)) {
            type.accept(new ProgramClassWriter(data));
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        return output.toByteArray();
    }
}
