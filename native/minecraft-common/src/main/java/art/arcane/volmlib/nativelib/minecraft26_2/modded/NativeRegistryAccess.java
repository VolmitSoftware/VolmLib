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

import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockProperty;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.entity.NativeEntityType;
import art.arcane.volmlib.nativelib.item.NativeItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Consumer;

public final class NativeRegistryAccess {

    private final Supplier<NativeModdedServer> server;
    private final Consumer<String> warnings;

    public NativeRegistryAccess(Supplier<NativeModdedServer> server, Consumer<String> warnings) {
        this.server = server;
        this.warnings = warnings;
    }

    public NativeBlockState deepSlateOre(NativeBlockState block, NativeBlockState ore) {
        BlockState result = NativeBlockProperties.toDeepSlateOre((BlockState) block.nativeHandle(), (BlockState) ore.nativeHandle());
        return ModdedBlockState.of(result, null);
    }

    public NativeBiome biome(String key) {
        Identifier identifier = Identifier.tryParse(key);
        if (identifier == null) {
            return null;
        }
        Registry<Biome> registry = biomeRegistry();
        if (registry == null) {
            return null;
        }
        Biome biome = registry.getValue(identifier);
        return biome == null ? null : ModdedBiome.of(biome, identifier.toString());
    }

    public NativeItem item(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        Identifier identifier = Identifier.tryParse(normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        return ModdedItem.of(item, identifier.toString());
    }

    public NativeEntityType entity(String key) {
        Identifier identifier = Identifier.tryParse(key);
        if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
        return ModdedEntityType.of(type, identifier.toString());
    }

    public List<String> blockKeys() {
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : BuiltInRegistries.BLOCK.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    public List<String> biomeKeys() {
        List<String> keys = new ArrayList<>();
        Registry<Biome> registry = biomeRegistry();
        if (registry == null) {
            warnings.accept("biome");
            return keys;
        }
        for (Identifier identifier : registry.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    public List<String> structureKeys() {
        List<String> keys = new ArrayList<>();
        NativeModdedServer host = server.get();
        MinecraftServer instance = host == null ? null : host.server();
        if (instance == null) {
            warnings.accept("structure");
            return keys;
        }
        for (Identifier identifier : instance.registryAccess().lookupOrThrow(Registries.STRUCTURE).keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    public List<String> itemKeys() {
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : BuiltInRegistries.ITEM.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    public List<String> entityKeys() {
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    public List<String> enchantmentKeys() {
        List<String> keys = new ArrayList<>();
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) {
            warnings.accept("enchantment");
            return keys;
        }
        for (Identifier identifier : registry.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    public List<String> potionEffectKeys() {
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : BuiltInRegistries.MOB_EFFECT.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    public List<String> lootTableKeys() {
        List<String> keys = new ArrayList<>();
        NativeModdedServer host = server.get();
        MinecraftServer instance = host == null ? null : host.server();
        if (instance == null) {
            warnings.accept("loot table");
            return keys;
        }
        HolderLookup.RegistryLookup<LootTable> registry = instance.reloadableRegistries().lookup()
                .lookupOrThrow(Registries.LOOT_TABLE);
        registry.listElementIds().forEach(key -> keys.add(key.identifier().toString()));
        return keys;
    }

    public Map<String, List<NativeBlockProperty>> blockStateProperties() {
        Map<String, List<NativeBlockProperty>> properties = new LinkedHashMap<>();
        // One List instance per identical property group. SchemaBuilder groups consecutive entries by list
        // identity, so sharing collapses the emitted schema instead of writing a block-state object per block.
        Map<String, List<NativeBlockProperty>> shared = new LinkedHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState defaultState = block.defaultBlockState();
            List<NativeBlockProperty> converted = new ArrayList<>();
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                converted.add(convertProperty(property, defaultState));
            }
            List<NativeBlockProperty> group = shared.computeIfAbsent(groupSignature(converted), key -> List.copyOf(converted));
            properties.put(BuiltInRegistries.BLOCK.getKey(block).toString(), group);
        }
        return properties;
    }

    private static String groupSignature(List<NativeBlockProperty> group) {
        StringBuilder signature = new StringBuilder(group.size() * 24);
        for (NativeBlockProperty property : group) {
            signature.append(property.name()).append(':').append(property.jsonType()).append('=')
                    .append(property.defaultValue()).append(property.allowedValues()).append(';');
        }
        return signature.toString();
    }

    private Registry<Biome> biomeRegistry() {
        NativeModdedServer host = server.get();
        MinecraftServer instance = host == null ? null : host.server();
        if (instance == null) {
            return null;
        }
        return instance.registryAccess().lookupOrThrow(Registries.BIOME);
    }

    private Registry<Enchantment> enchantmentRegistry() {
        NativeModdedServer host = server.get();
        MinecraftServer instance = host == null ? null : host.server();
        if (instance == null) {
            return null;
        }
        return instance.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }

    private static <T extends Comparable<T>> NativeBlockProperty convertProperty(Property<T> property, BlockState defaultState) {
        T defaultValue = defaultState.getValue(property);
        Class<T> valueClass = property.getValueClass();
        List<Object> allowedValues = new ArrayList<>();
        if (valueClass == Boolean.class || valueClass == Integer.class) {
            for (T value : property.getPossibleValues()) {
                allowedValues.add(value);
            }
            String jsonType = valueClass == Boolean.class ? "boolean" : "integer";
            return new NativeBlockProperty(property.getName(), jsonType, defaultValue, List.copyOf(allowedValues), null);
        }
        for (T value : property.getPossibleValues()) {
            allowedValues.add(property.getName(value));
        }
        return new NativeBlockProperty(property.getName(), "string", property.getName(defaultValue), List.copyOf(allowedValues), null);
    }
}
