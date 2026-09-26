package art.arcane.volmit.packaging;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@DisableCachingByDefault(because = "Native provider module-to-file associations are resolved dynamically.")
public abstract class PackedJar extends DefaultTask {
    static final String TEMPLATE_PACKAGE = "art/arcane/volmit/packaging/runtime/";
    static final String PAYLOAD = "META-INF/volmit/runtime.jar.xz";
    static final String METADATA = "META-INF/volmit/runtime.properties";
    private static final long MAXIMUM_RUNTIME_BYTES = 256L * 1024 * 1024;
    private static final String PAPER_LOADER_METHOD = "(Lio/papermc/paper/plugin/loader/PluginClasspathBuilder;)V";

    public PackedJar() {
        getMaximumBytes().convention(PackagingArtifact.SPIGOT_CAP_BYTES);
        getEntrypoints().convention(List.of());
        getJarAccessors().convention(List.of());
        getNativeArtifacts().convention(Map.of());
        getNativeManifestPath().convention("META-INF/volmit/native-runtime.properties");
        getNativeResourceDirectory().convention("META-INF/volmit/native/");
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();

    @Input
    public abstract Property<Long> getMaximumBytes();

    @Input
    public abstract ListProperty<String> getEntrypoints();

    @Input
    public abstract ListProperty<String> getJarAccessors();

    @Input
    public abstract Property<String> getNativeManifestPath();

    @Input
    public abstract Property<String> getNativeResourceDirectory();

    @Internal
    public abstract MapProperty<String, File> getNativeArtifacts();

    @Input
    public List<String> getNativeModules() {
        return getNativeArtifacts().get().keySet().stream().sorted().toList();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public Collection<File> getNativeProviderJars() {
        return getNativeArtifacts().get().values();
    }

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void pack() throws IOException, NoSuchAlgorithmException {
        Path source = getInputJar().get().getAsFile().toPath();
        Path output = getOutputJar().get().getAsFile().toPath();
        Map<String, byte[]> bootstrap = bootstrapClasses();
        PackOptions options = new PackOptions(getEntrypoints().get(), getJarAccessors().get(),
                new NativeOptions(getNativeArtifacts().get(), getNativeManifestPath().get(), getNativeResourceDirectory().get()),
                getMaximumBytes().get());
        PackResult result = writePackedJar(source, output, bootstrap, options);
        getLogger().lifecycle("{}: selected {} ({} -> {} bytes); {} bootstrap classes retained", output.getFileName(),
                result.compressed() ? "XZ" : "ordinary jar", result.ordinaryBytes(), Files.size(output),
                result.compressed() ? result.bootstrapClasses() : 0);
    }

    static Map<String, byte[]> bootstrapClasses() throws IOException {
        Map<String, byte[]> bootstrap = new TreeMap<>();
        try (InputStream resource = PackedJar.class.getResourceAsStream("/META-INF/volmit/xz-template.jar.bin")) {
            if (resource == null) {
                throw new IOException("Packed runtime template resource is missing");
            }
            try (ZipInputStream archive = new ZipInputStream(resource)) {
                for (ZipEntry entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                    if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                        bootstrap.put(entry.getName(), archive.readAllBytes());
                    }
                }
            }
        }
        return bootstrap;
    }

    static PackResult writePackedJar(Path source, Path output, Map<String, byte[]> bootstrap, PackOptions options)
            throws IOException, NoSuchAlgorithmException {
        Map<String, byte[]> contents = readJar(source);
        if (contents.containsKey(PAYLOAD)) {
            throw new IOException("Archive already contains an XZ runtime: " + source);
        }
        if (!options.natives().artifacts().isEmpty()) {
            embedNativeArtifacts(contents, options.natives());
        }
        byte[] ordinary = options.natives().artifacts().isEmpty() ? Files.readAllBytes(source) : writeJar(contents, false);
        Descriptor descriptor = descriptor(contents, options.entrypoints());
        String main = descriptor.entrypoints().get(0);
        String namespace = main + "/xz";
        String runtime = namespace + "/PackedRuntime";
        Set<String> hooks = initializationRoots(contents, descriptor.entrypoints());
        Set<String> accessors = new LinkedHashSet<>();
        for (String accessor : options.jarAccessors()) {
            int separator = accessor.lastIndexOf('#');
            if (separator <= 0 || separator == accessor.length() - 1) {
                throw new IOException("Invalid packed jar accessor: " + accessor);
            }
            accessors.add(accessor.substring(0, separator).replace('.', '/') + accessor.substring(separator));
        }
        for (String accessor : accessors) {
            validateAccessor(contents, accessor);
        }
        for (String name : hooks) {
            contents.put(name + ".class", instrument(contents.get(name + ".class"), runtime, true, accessors));
        }
        Set<String> accessorOwners = new LinkedHashSet<>();
        for (String accessor : accessors) {
            accessorOwners.add(accessor.substring(0, accessor.indexOf('#')));
        }
        for (String name : accessorOwners) {
            byte[] bytes = contents.get(name + ".class");
            if (bytes == null) {
                throw new IOException("Packed jar accessor owner is missing: " + name);
            }
            if (!hooks.contains(name)) {
                contents.put(name + ".class", instrument(bytes, runtime, false, accessors));
            }
        }
        Set<String> retained = bootstrapClosure(contents, hooks);
        Map<String, byte[]> relocated = relocateBootstrap(reachableBootstrap(bootstrap), namespace);
        if (!relocated.containsKey(runtime + ".class")) {
            throw new IOException("Packed runtime template is missing");
        }
        contents.putAll(relocated);
        byte[] payload = writeJar(contents, true);
        if (payload.length > MAXIMUM_RUNTIME_BYTES) {
            throw new IOException("Embedded runtime exceeds " + MAXIMUM_RUNTIME_BYTES + " byte limit: " + payload.length);
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        LZMA2Options compression = new LZMA2Options(9);
        compression.setDictSize(Math.min(compression.getDictSize(), Math.max(4096, payload.length)));
        try (XZOutputStream xz = new XZOutputStream(compressed, compression)) {
            xz.write(payload);
        }
        Map<String, byte[]> outer = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
            if (!options.natives().artifacts().isEmpty()
                    && entry.getKey().startsWith(options.natives().resourceDirectory())) {
                continue;
            }
            if (!entry.getKey().endsWith(".class") || retained.contains(entry.getKey())) {
                outer.put(entry.getKey(), entry.getValue());
            }
        }
        outer.putAll(relocated);
        outer.put(PAYLOAD, compressed.toByteArray());
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        String metadata = "pluginName=" + descriptor.pluginName() + "\nsha256=" + hash + "\n";
        outer.put(METADATA, metadata.getBytes(StandardCharsets.UTF_8));
        byte[] packed = writeJar(outer, false);
        boolean compressedRuntime = packed.length < ordinary.length;
        byte[] selected = compressedRuntime ? packed : ordinary;
        if (selected.length > options.maximumBytes()) {
            throw new IOException("Selected jar exceeds " + options.maximumBytes() + " byte budget: " + selected.length);
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, selected);
        return new PackResult(retained.size() + relocated.size(), descriptor.entrypoints(), compressedRuntime,
                ordinary.length);
    }

    static Descriptor descriptor(Map<String, byte[]> contents, List<String> configured) throws IOException {
        Set<String> entrypoints = new LinkedHashSet<>();
        String pluginName = null;
        for (String resource : List.of("plugin.yml", "paper-plugin.yml", "bungee.yml")) {
            byte[] bytes = contents.get(resource);
            if (bytes == null) {
                continue;
            }
            String yaml = new String(bytes, StandardCharsets.UTF_8);
            String name = yamlScalar(yaml, "name");
            if (pluginName == null && name != null) {
                pluginName = name;
            }
            for (String key : List.of("main", "loader", "bootstrapper")) {
                String value = yamlScalar(yaml, key);
                if (value != null) {
                    entrypoints.add(value.replace('.', '/'));
                }
            }
        }
        byte[] velocity = contents.get("velocity-plugin.json");
        if (velocity != null) {
            JsonObject descriptor = JsonParser.parseString(new String(velocity, StandardCharsets.UTF_8)).getAsJsonObject();
            if (descriptor.has("main")) {
                entrypoints.add(descriptor.get("main").getAsString().replace('.', '/'));
            }
            if (pluginName == null) {
                pluginName = descriptor.get(descriptor.has("name") ? "name" : "id").getAsString();
            }
        }
        for (String entrypoint : configured) {
            entrypoints.add(entrypoint.replace('.', '/'));
        }
        if (entrypoints.isEmpty() || pluginName == null || !pluginName.matches("[A-Za-z0-9][A-Za-z0-9 _.-]*")) {
            throw new IOException("Archive must declare plugin entrypoints and a safe plugin name");
        }
        for (String entrypoint : entrypoints) {
            if (!contents.containsKey(entrypoint + ".class")) {
                throw new IOException("Plugin entrypoint is missing: " + entrypoint);
            }
        }
        return new Descriptor(pluginName, List.copyOf(entrypoints));
    }

    private static String yamlScalar(String yaml, String key) {
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(key) + ":[ \\t]*([^\\r\\n]+)").matcher(yaml);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).strip();
        if (value.length() > 1 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        int comment = value.indexOf(" #");
        return comment < 0 ? value : value.substring(0, comment).stripTrailing();
    }

    private static Set<String> initializationRoots(Map<String, byte[]> contents, List<String> entrypoints) {
        Set<String> roots = new LinkedHashSet<>();
        for (String entrypoint : entrypoints) {
            String name = entrypoint;
            while (name != null && contents.containsKey(name + ".class") && roots.add(name)) {
                name = new ClassReader(contents.get(name + ".class")).getSuperName();
            }
        }
        return roots;
    }

    static Set<String> bootstrapClosure(Map<String, byte[]> contents, Set<String> roots) {
        ArrayDeque<String> pending = new ArrayDeque<>(roots);
        for (String root : roots) {
            pending.addAll(references(contents.get(root + ".class")));
        }
        Set<String> retained = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            byte[] bytes = contents.get(name + ".class");
            if (bytes == null || !retained.add(name + ".class")) {
                continue;
            }
            ClassReader reader = new ClassReader(bytes);
            if (reader.getSuperName() != null) {
                pending.add(reader.getSuperName());
            }
            pending.addAll(List.of(reader.getInterfaces()));
        }
        return retained;
    }

    private static Set<String> references(byte[] bytes) {
        Set<String> references = new LinkedHashSet<>();
        ClassReader reader = new ClassReader(bytes);
        Remapper collector = new Remapper(Opcodes.ASM9) {
            @Override
            public String map(String internalName) {
                references.add(internalName);
                return internalName;
            }
        };
        reader.accept(new ClassRemapper(new ClassWriter(0), collector), ClassReader.SKIP_DEBUG);
        char[] buffer = new char[reader.getMaxStringLength()];
        for (int index = 1; index < reader.getItemCount(); index++) {
            int offset = reader.getItem(index);
            if (offset != 0 && reader.readByte(offset - 1) == 7) {
                collector.mapValue(reader.readConst(index, buffer));
            }
        }
        return references;
    }

    private static Map<String, byte[]> reachableBootstrap(Map<String, byte[]> bootstrap) {
        Map<String, byte[]> retained = new TreeMap<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(TEMPLATE_PACKAGE + "PackedRuntime");
        while (!pending.isEmpty()) {
            String name = pending.removeFirst() + ".class";
            byte[] bytes = bootstrap.get(name);
            if (bytes != null && retained.putIfAbsent(name, bytes) == null) {
                pending.addAll(references(bytes));
            }
        }
        return retained;
    }

    private static Map<String, byte[]> relocateBootstrap(Map<String, byte[]> bootstrap, String namespace) {
        Map<String, byte[]> relocated = new TreeMap<>();
        Remapper remapper = new Remapper(Opcodes.ASM9) {
            @Override
            public String map(String name) {
                if (name.startsWith(TEMPLATE_PACKAGE)) {
                    return namespace + '/' + name.substring(TEMPLATE_PACKAGE.length());
                }
                if (name.startsWith("org/tukaani/xz/")) {
                    return namespace + "/decoder/" + name.substring("org/tukaani/xz/".length());
                }
                return name;
            }
        };
        for (Map.Entry<String, byte[]> entry : bootstrap.entrySet()) {
            ClassReader reader = new ClassReader(entry.getValue());
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassRemapper(writer, remapper), 0);
            relocated.put(remapper.map(reader.getClassName()) + ".class", writer.toByteArray());
        }
        return relocated;
    }

    private static void validateAccessor(Map<String, byte[]> contents, String accessor) throws IOException {
        int separator = accessor.indexOf('#');
        byte[] bytes = contents.get(accessor.substring(0, separator) + ".class");
        if (bytes == null) {
            throw new IOException("Packed jar accessor owner is missing: " + accessor);
        }
        Set<String> methods = new LinkedHashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                if (descriptor.equals("()Ljava/io/File;")
                        && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                    methods.add(name);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!methods.contains(accessor.substring(separator + 1))) {
            throw new IOException("Packed jar accessor must name a concrete no-argument File method: " + accessor);
        }
    }

    static byte[] instrument(byte[] original, String runtime, boolean initialize, Set<String> accessors) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            private boolean hasInitializer;

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("classloader") && descriptor.equals(PAPER_LOADER_METHOD)) {
                    return releaseTemporaryLoader(method, runtime);
                }
                if (accessors.contains(reader.getClassName() + '#' + name)
                        && descriptor.equals("()Ljava/io/File;")) {
                    method.visitCode();
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, runtime, "runtimeJar", "()Ljava/io/File;", false);
                    method.visitInsn(Opcodes.ARETURN);
                    method.visitMaxs(1, (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0);
                    method.visitEnd();
                    return null;
                }
                if (!initialize || !name.equals("<clinit>")) {
                    return method;
                }
                hasInitializer = true;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        visitMethodInsn(Opcodes.INVOKESTATIC, runtime, "install", "()V", false);
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (initialize && !hasInitializer) {
                    MethodVisitor initializer = super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                    initializer.visitCode();
                    initializer.visitMethodInsn(Opcodes.INVOKESTATIC, runtime, "install", "()V", false);
                    initializer.visitInsn(Opcodes.RETURN);
                    initializer.visitMaxs(0, 0);
                    initializer.visitEnd();
                }
                super.visitEnd();
            }
        }, 0);
        return writer.toByteArray();
    }

    private static MethodVisitor releaseTemporaryLoader(MethodVisitor method, String runtime) {
        return new MethodVisitor(Opcodes.ASM9, method) {
            private final Label start = new Label();
            private final Label end = new Label();
            private final Label handler = new Label();
            private final Label completed = new Label();

            @Override
            public void visitCode() {
                super.visitCode();
                super.visitLabel(start);
            }

            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.RETURN) {
                    super.visitJumpInsn(Opcodes.GOTO, completed);
                } else {
                    super.visitInsn(opcode);
                }
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                super.visitLabel(end);
                super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
                super.visitLabel(handler);
                super.visitFrame(Opcodes.F_FULL, 0, null, 1, new Object[]{"java/lang/Throwable"});
                super.visitInsn(Opcodes.DUP);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, runtime, "releaseTemporary", "(Ljava/lang/Throwable;)V", false);
                super.visitInsn(Opcodes.ATHROW);
                super.visitLabel(completed);
                super.visitFrame(Opcodes.F_FULL, 0, null, 0, null);
                super.visitInsn(Opcodes.ACONST_NULL);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, runtime, "releaseTemporary", "(Ljava/lang/Throwable;)V", false);
                super.visitInsn(Opcodes.RETURN);
                super.visitMaxs(Math.max(maxStack, 2), maxLocals);
            }
        };
    }

    public static Map<String, File> resolveNativeArtifacts(Map<ComponentIdentifier, File> resolved) {
        Map<String, File> artifacts = new TreeMap<>();
        for (Map.Entry<ComponentIdentifier, File> entry : resolved.entrySet()) {
            String module;
            if (entry.getKey() instanceof ModuleComponentIdentifier component
                    && component.getGroup().equals("com.github.VolmitSoftware.VolmLib")) {
                module = component.getModule();
            } else if (entry.getKey() instanceof ProjectComponentIdentifier component) {
                module = component.getProjectPath().substring(1).replace(':', '-');
            } else {
                throw new GradleException("Unexpected native provider component: " + entry.getKey().getDisplayName());
            }
            if (!module.matches("native-(common|v[0-9_]+R[0-9]+)")) {
                throw new GradleException("Unexpected native provider module: " + module);
            }
            if (artifacts.put(module, entry.getValue()) != null) {
                throw new GradleException("Duplicate native provider module: " + module);
            }
        }
        return artifacts;
    }

    private static void embedNativeArtifacts(Map<String, byte[]> contents, NativeOptions options)
            throws IOException, NoSuchAlgorithmException {
        Map<String, File> artifacts = options.artifacts();
        byte[] original = contents.get(options.manifestPath());
        if (original == null) {
            throw new IOException("Missing native runtime dependency manifest");
        }
        Properties manifest = new Properties();
        manifest.load(new ByteArrayInputStream(original));
        Set<String> modules = new TreeSet<>(List.of(manifest.getProperty("modules", "").split(",")));
        if (!modules.equals(artifacts.keySet()) || !modules.contains("native-common")) {
            throw new IOException("Embedded native providers differ: " + artifacts.keySet() + "; expected " + modules);
        }
        List<String> embedded = new ArrayList<>();
        embedded.add("modules=" + String.join(",", modules));
        embedded.add("storage=embedded");
        for (String module : modules) {
            if (!module.matches("native-(common|v[0-9_]+R[0-9]+)")) {
                throw new IOException("Invalid embedded native provider module: " + module);
            }
            byte[] bytes = Files.readAllBytes(artifacts.get(module).toPath());
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            embedded.add(module + ".coordinate=com.github.VolmitSoftware.VolmLib:" + module + ":embedded-" + digest);
            embedded.add(module + ".sha256=" + digest);
            contents.put(options.resourceDirectory() + module + ".jar", bytes);
        }
        contents.put(options.manifestPath(), (String.join("\n", embedded) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, byte[]> readJar(Path path) throws IOException {
        Map<String, byte[]> contents = new TreeMap<>();
        try (ZipFile jar = new ZipFile(path.toFile())) {
            for (ZipEntry entry : jar.stream().filter(entry -> !entry.isDirectory()).toList()) {
                try (InputStream input = jar.getInputStream(entry)) {
                    if (contents.put(entry.getName(), input.readAllBytes()) != null) {
                        throw new IOException("Duplicate archive entry: " + entry.getName());
                    }
                }
            }
        }
        return contents;
    }

    static byte[] writeJar(Map<String, byte[]> contents, boolean stored) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            jar.setLevel(9);
            for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
                JarEntry zipEntry = new JarEntry(entry.getKey());
                zipEntry.setTime(0L);
                if (stored || entry.getKey().equals(PAYLOAD)) {
                    CRC32 crc = new CRC32();
                    crc.update(entry.getValue());
                    zipEntry.setMethod(ZipEntry.STORED);
                    zipEntry.setSize(entry.getValue().length);
                    zipEntry.setCompressedSize(entry.getValue().length);
                    zipEntry.setCrc(crc.getValue());
                }
                jar.putNextEntry(zipEntry);
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    record Descriptor(String pluginName, List<String> entrypoints) {
    }

    record PackOptions(List<String> entrypoints, List<String> jarAccessors, NativeOptions natives, long maximumBytes) {
    }

    record NativeOptions(Map<String, File> artifacts, String manifestPath, String resourceDirectory) {
        static NativeOptions empty() {
            return new NativeOptions(Map.of(), "META-INF/volmit/native-runtime.properties", "META-INF/volmit/native/");
        }
    }

    record PackResult(int bootstrapClasses, List<String> entrypoints, boolean compressed, long ordinaryBytes) {
    }
}
