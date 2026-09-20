package art.arcane.volmlib.nativelib.terrain;

import java.util.Set;
import java.util.function.Consumer;

public interface NativeBiomeSourceAccess<H, S> {
    RegistryView<H> registry();
    Set<H> serializedBiomes();
    H serializedNoise(int x, int y, int z, S sampler);
    String holderKey(H holder);

    interface RegistryView<H> {
        void forEach(Consumer<? super H> consumer);
        H lookup(String key);
    }
}
