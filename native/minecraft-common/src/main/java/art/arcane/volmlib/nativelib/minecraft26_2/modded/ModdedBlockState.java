/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.BlockStateKey;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ModdedBlockState implements NativeBlockState {
    private static final ConcurrentHashMap<String, ModdedBlockState> CACHE = new ConcurrentHashMap<>();

    private final BlockState state;
    private final Map<Property<?>, Comparable<?>> parsedProperties;
    private final String key;
    private final String namespace;
    private final String deferredPlacementKey;
    private volatile String materialKey;
    private volatile Boolean air;
    private volatile Boolean solid;
    private volatile Boolean occluding;
    private volatile Boolean fluid;
    private volatile Boolean water;
    private volatile Boolean foliage;
    private volatile Boolean treeBlock;
    private volatile Boolean decorant;
    private volatile Boolean updatable;
    private volatile Boolean storage;

    private ModdedBlockState(BlockState state, Map<Property<?>, Comparable<?>> parsedProperties, String key, String deferredPlacementKey) {
        this.state = state;
        this.parsedProperties = parsedProperties;
        this.key = key;
        this.namespace = BlockStateKey.namespace(key);
        this.deferredPlacementKey = deferredPlacementKey;
    }

    public static ModdedBlockState of(BlockState state, Map<Property<?>, Comparable<?>> parsedProperties) {
        String key = serialize(state);
        return CACHE.computeIfAbsent(key, (String k) -> new ModdedBlockState(state, parsedProperties, k, null));
    }

    public static ModdedBlockState deferred(BlockState state, Map<Property<?>, Comparable<?>> parsedProperties, String placementKey) {
        return new ModdedBlockState(state, parsedProperties, serialize(state), Objects.requireNonNull(placementKey));
    }

    public ModdedBlockState withDeferredPlacement(String placementKey) {
        return new ModdedBlockState(state, parsedProperties, key, Objects.requireNonNull(placementKey));
    }

    public static String serialize(BlockState state) {
        StringBuilder result = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (!state.isSingletonState()) {
            result.append('[');
            result.append(state.getValues()
                    .map((Property.Value<?> value) -> value.property().getName() + "=" + value.valueName())
                    .collect(Collectors.joining(",")));
            result.append(']');
        }
        return result.toString();
    }

    public BlockState handle() {
        return state;
    }

    public Map<Property<?>, Comparable<?>> parsedProperties() {
        return parsedProperties;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModdedBlockState blockState)) {
            return false;
        }
        return state.equals(blockState.state) && Objects.equals(deferredPlacementKey, blockState.deferredPlacementKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, deferredPlacementKey);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public String materialKey() {
        String cached = materialKey;
        if (cached == null) {
            int bracket = key.indexOf('[');
            cached = bracket < 0 ? key : key.substring(0, bracket);
            materialKey = cached;
        }
        return cached;
    }

    @Override
    public boolean isAir() {
        Boolean cached = air;
        if (cached == null) {
            cached = NativeBlockProperties.isAir(state);
            air = cached;
        }
        return cached;
    }

    @Override
    public boolean isSolid() {
        Boolean cached = solid;
        if (cached == null) {
            cached = NativeBlockProperties.isSolid(state);
            solid = cached;
        }
        return cached;
    }

    @Override
    public boolean isOccluding() {
        Boolean cached = occluding;
        if (cached == null) {
            cached = NativeBlockProperties.isOccluding(state);
            occluding = cached;
        }
        return cached;
    }

    @Override
    public boolean isCustom() {
        return deferredPlacementKey != null;
    }

    @Override
    public String deferredPlacementKey() {
        return deferredPlacementKey;
    }

    @Override
    public NativeBlockState placementBaseState() {
        return deferredPlacementKey == null ? this : of(state, parsedProperties);
    }

    @Override
    public boolean isFluid() {
        Boolean cached = fluid;
        if (cached == null) {
            cached = NativeBlockProperties.isFluid(state);
            fluid = cached;
        }
        return cached;
    }

    @Override
    public boolean isWater() {
        Boolean cached = water;
        if (cached == null) {
            cached = NativeBlockProperties.isWater(state);
            water = cached;
        }
        return cached;
    }

    @Override
    public boolean isWaterLogged() {
        return NativeBlockProperties.isWaterLogged(state);
    }

    @Override
    public boolean isLit() {
        return NativeBlockProperties.isLit(state);
    }

    @Override
    public boolean isUpdatable() {
        Boolean cached = updatable;
        if (cached == null) {
            cached = NativeBlockProperties.isUpdatable(state);
            updatable = cached;
        }
        return cached;
    }

    @Override
    public boolean isFoliage() {
        Boolean cached = foliage;
        if (cached == null) {
            cached = NativeBlockProperties.isFoliage(state);
            foliage = cached;
        }
        return cached;
    }

    @Override
    public boolean isTreeBlock() {
        Boolean cached = treeBlock;
        if (cached == null) {
            cached = state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
            treeBlock = cached;
        }
        return cached;
    }

    @Override
    public boolean isFoliagePlantable() {
        return NativeBlockProperties.isFoliagePlantable(state);
    }

    @Override
    public boolean isDecorant() {
        Boolean cached = decorant;
        if (cached == null) {
            cached = NativeBlockProperties.isDecorant(state);
            decorant = cached;
        }
        return cached;
    }

    @Override
    public boolean isStorage() {
        Boolean cached = storage;
        if (cached == null) {
            cached = NativeBlockProperties.isStorage(state);
            storage = cached;
        }
        return cached;
    }

    @Override
    public boolean isStorageChest() {
        return NativeBlockProperties.isStorageChest(state);
    }

    @Override
    public boolean isOre() {
        return NativeBlockProperties.isOre(state);
    }

    @Override
    public boolean isDeepSlate() {
        return NativeBlockProperties.isDeepSlate(state);
    }

    @Override
    public boolean isVineBlock() {
        return NativeBlockProperties.isVineBlock(state);
    }

    @Override
    public boolean canPlaceOnto(NativeBlockState onto) {
        return NativeBlockProperties.canPlaceOnto(state.getBlock(), ((BlockState) onto.nativeHandle()).getBlock());
    }

    @Override
    public boolean matches(NativeBlockState other) {
        if (!(other instanceof ModdedBlockState blockState)) {
            return false;
        }
        if (state.getBlock() != blockState.state.getBlock()) {
            return false;
        }
        if (state.equals(blockState.state)) {
            return true;
        }
        if (blockState.parsedProperties == null) {
            return false;
        }
        BlockState merged = state;
        for (Map.Entry<Property<?>, Comparable<?>> entry : blockState.parsedProperties.entrySet()) {
            merged = applyProperty(merged, entry.getKey(), entry.getValue());
        }
        return merged.equals(state);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<?> property, Comparable<?> value) {
        return state.setValue((Property<T>) property, (T) value);
    }

    @Override
    public boolean hasTileEntity() {
        return NativeBlockProperties.hasTileEntity(state);
    }

    @Override
    public NativeBlockState withProperty(String name, String value) {
        String merged = BlockStateKey.withProperty(key, name, value);
        ModdedBlockState resolved = NativeBlockResolver.strictParse(merged);
        return withHandle(resolved.handle(), resolved.parsedProperties());
    }

    public ModdedBlockState withHandle(BlockState updated, Map<Property<?>, Comparable<?>> properties) {
        if (updated == state && properties == parsedProperties) {
            return this;
        }
        return deferredPlacementKey == null
                ? of(updated, properties)
                : deferred(updated, properties, deferredPlacementKey);
    }

    @Override
    public Object nativeHandle() {
        return state;
    }
}
