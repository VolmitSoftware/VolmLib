/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.util.nbt.mca;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.Tag;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class NBTWorldSupport {
    private NBTWorldSupport() {
    }

    @FunctionalInterface
    public interface KeyFunction {
        long key(int x, int z);
    }

    @FunctionalInterface
    public interface KeyPartFunction {
        int decode(long key);
    }

    public static MCAWorldStoreSupport.KeyCodec keyCodec(
            KeyFunction keyFunction,
            KeyPartFunction keyX,
            KeyPartFunction keyZ
    ) {
        return new MCAWorldStoreSupport.KeyCodec() {
            @Override
            public long key(int x, int z) {
                return keyFunction.key(x, z);
            }

            @Override
            public int keyX(long key) {
                return keyX.decode(key);
            }

            @Override
            public int keyZ(long key) {
                return keyZ.decode(key);
            }
        };
    }

    public static MCAWorldStoreSupport.RegionFileResolver regionFileResolver(File worldFolder, String regionDirectory) {
        String dir = regionDirectory.endsWith("/") ? regionDirectory : regionDirectory + "/";
        return (x, z) -> new File(worldFolder, dir + "r." + x + "." + z + ".mca");
    }

    public static MCAWorldStoreSupport.Logger logger(
            Consumer<String> info,
            Consumer<String> debug,
            BiConsumer<String, Throwable> error
    ) {
        return new MCAWorldStoreSupport.Logger() {
            @Override
            public void info(String message) {
                info.accept(message);
            }

            @Override
            public void debug(String message) {
                debug.accept(message);
            }

            @Override
            public void error(String message, Throwable throwable) {
                error.accept(message, throwable);
            }
        };
    }

    public static <B> BlockStateCodec<B> blockStateCodec(BlockStateCodecOptions<B> options) {
        return new BlockStateCodec<>(options);
    }

    public record BlockStateCodecOptions<B>(
            Function<String, B> resolver,
            Supplier<B> fallback,
            Function<B, String> blockDataString,
            Function<B, String> namespacedMaterialKey,
            MCABlockStateCodecSupport.Format format
    ) {
        public BlockStateCodecOptions {
            Objects.requireNonNull(resolver);
            Objects.requireNonNull(fallback);
            Objects.requireNonNull(blockDataString);
            Objects.requireNonNull(namespacedMaterialKey);
            Objects.requireNonNull(format);
        }
    }

    public static final class BlockStateCodec<B> {
        private final Map<B, CompoundTag> cache = new ConcurrentHashMap<>();
        private final Function<String, B> resolver;
        private final Supplier<B> fallback;
        private final Function<B, String> blockDataString;
        private final Function<B, String> namespacedMaterialKey;
        private final MCABlockStateCodecSupport.Format format;

        private BlockStateCodec(BlockStateCodecOptions<B> options) {
            this.resolver = options.resolver();
            this.fallback = options.fallback();
            this.blockDataString = options.blockDataString();
            this.namespacedMaterialKey = options.namespacedMaterialKey();
            this.format = options.format();
        }

        public B decode(Tag<?> tag) {
            if (tag == null) {
                return fallback.get();
            }

            B resolved = resolver.apply(MCABlockStateCodecSupport.decodeBlockStateString(tag, format));
            return resolved == null ? fallback.get() : resolved;
        }

        public CompoundTag encode(B blockData) {
            return cache.computeIfAbsent(blockData, data ->
                    MCABlockStateCodecSupport.encodeBlockState(
                            blockDataString.apply(data),
                            namespacedMaterialKey.apply(data),
                            format
                    )
            );
        }
    }
}
