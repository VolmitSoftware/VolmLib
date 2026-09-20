package art.arcane.volmlib.nativelib.minecraft26_2.fabric;

import net.minecraft.server.packs.repository.PackRepository;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeDatapackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class NativeFabricPackSources {
    private static volatile Supplier<NativeDatapackSource> source;

    private NativeFabricPackSources() {
    }

    public static void bind(Supplier<NativeDatapackSource> repositorySource) {
        source = Objects.requireNonNull(repositorySource, "repositorySource");
    }

    public static void attach(PackRepository repository) {
        PackRepository activeRepository = Objects.requireNonNull(repository, "repository");
        Set<RepositorySource> merged = new LinkedHashSet<>(activeRepository.sources);
        merged.add(Objects.requireNonNull(source, "Server datapack source has not been bound").get());
        activeRepository.sources = merged;
    }
}
