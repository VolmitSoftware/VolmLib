package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class NativeDatapackSource implements RepositorySource {
    private final Supplier<PackHandle> pack;
    private final Runnable loaded;

    public NativeDatapackSource(Supplier<PackHandle> pack, Runnable loaded) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.loaded = Objects.requireNonNull(loaded, "loaded");
    }

    public static PackHandle read(Options options) {
        PackLocationInfo location = new PackLocationInfo(options.id(), Component.literal(options.title()),
                PackSource.BUILT_IN, Optional.empty());
        PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.TOP, true);
        PathPackResources.PathResourcesSupplier supplier = new PathPackResources.PathResourcesSupplier(options.directory());
        Pack pack = Pack.readMetaAndCreate(location, supplier, PackType.SERVER_DATA, selection);
        if (pack == null) {
            throw new IllegalStateException("Datapack " + options.id() + " at " + options.directory()
                    + " produced no readable pack metadata");
        }
        return new PackHandle(pack);
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        consumer.accept(pack.get().pack);
        loaded.run();
    }

    public record Options(String id, String title, Path directory) {
        public Options {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(directory, "directory");
        }
    }

    public static final class PackHandle {
        private final Pack pack;

        private PackHandle(Pack pack) {
            this.pack = pack;
        }
    }
}
