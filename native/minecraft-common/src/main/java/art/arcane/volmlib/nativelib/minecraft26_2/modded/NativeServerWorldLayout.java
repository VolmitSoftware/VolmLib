package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class NativeServerWorldLayout {
    public static final String SERVER_PROPERTIES = "server.properties";
    public static final String LEVEL_NAME = "level-name";
    public static final String LEVEL_TYPE = "level-type";
    public static final String LEVEL_SEED = "level-seed";
    private static final List<String> METADATA = List.of("level.dat", "level.dat_old");
    private static final List<String> PRIMARY_FOLDERS = List.of("region", "entities", "poi");
    private static final List<String> DIMENSION_FOLDERS = List.of("dimensions/minecraft/overworld",
            "dimensions/minecraft/the_nether", "dimensions/minecraft/the_end", "DIM-1", "DIM1");

    private NativeServerWorldLayout() {
    }

    public static Path instanceRoot() {
        return Path.of("").toAbsolutePath().normalize();
    }

    public static List<String> metadataFiles() {
        return METADATA;
    }

    public static List<String> primaryFolders() {
        return PRIMARY_FOLDERS;
    }

    public static List<String> dimensionFolders() {
        return DIMENSION_FOLDERS;
    }

    public static Path configuredWorldRoot(Path instanceRoot, String configuredName, List<String> arguments) throws IOException {
        Path universe = universeRoot(instanceRoot, commandLineOption(arguments, "universe"));
        String argument = commandLineOption(arguments, "world");
        String name = argument == null || argument.isBlank() ? configuredName : argument;
        return resolveWorldRoot(universe, name == null || name.isBlank() ? "world" : name);
    }

    public static Path universeRoot(Path instanceRoot, String universeOption) {
        return (universeOption == null || universeOption.isBlank()
                ? instanceRoot
                : instanceRoot.resolve(universeOption)).toAbsolutePath().normalize();
    }

    public static Path resolveWorldRoot(Path universe, String levelName) throws IOException {
        if (!Files.isDirectory(universe)) {
            throw new MissingWorldRootException("Server universe directory does not exist: " + universe, universe);
        }
        Path worldRoot = universe.resolve(levelName).toAbsolutePath().normalize();
        if (worldRoot.equals(universe) || !worldRoot.startsWith(universe)) {
            throw new IOException("Unsafe world name outside the server universe " + universe + ": " + levelName);
        }
        if (!Files.isDirectory(worldRoot)) {
            throw new MissingWorldRootException("Server world directory does not exist: " + worldRoot, worldRoot);
        }
        return worldRoot;
    }

    /**
     * A universe or world directory that is simply absent. Separated from every other IO failure so
     * reconciliation can treat it as nothing-to-quarantine instead of refusing startup; an unsafe world name
     * stays a plain IOException and still refuses.
     */
    public static final class MissingWorldRootException extends IOException {
        private static final long serialVersionUID = 1L;

        private final Path path;

        MissingWorldRootException(String message, Path path) {
            super(message);
            this.path = path;
        }

        public Path path() {
            return path;
        }
    }

    /**
     * Best effort read of a dedicated-server launch option. The parsed OptionSet is not reachable from mod
     * bootstrap, so read the process arguments; when they are unavailable we fall back to the vanilla defaults,
     * which is what the unpatched code assumed unconditionally.
     */
    public static String commandLineOption(List<String> arguments, String name) {
        String flag = "--" + name;
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            if (argument.equals(flag)) {
                return index + 1 < arguments.size() ? arguments.get(index + 1) : null;
            }
            if (argument.startsWith(flag + "=")) {
                return argument.substring(flag.length() + 1);
            }
        }
        return null;
    }

    public static List<String> processArguments(Consumer<RuntimeException> unavailableHandler) {
        try {
            Optional<String[]> arguments = ProcessHandle.current().info().arguments();
            if (arguments.isPresent() && arguments.get().length > 0) {
                return List.of(arguments.get());
            }
        } catch (RuntimeException unavailable) {
            unavailableHandler.accept(unavailable);
        }
        // Whitespace split only: sun.java.command is a flattened string with no quoting information, so a
        // --universe or --world value containing spaces cannot be recovered from it. Deliberately not parsed
        // further - a half-correct quote parser would hand world resolution a wrong directory, and the missing
        // world root path already degrades to the vanilla defaults instead of failing the boot.
        String command = System.getProperty("sun.java.command");
        if (command == null || command.isBlank()) {
            return List.of();
        }
        return List.of(command.trim().split("\\s+"));
    }
}
