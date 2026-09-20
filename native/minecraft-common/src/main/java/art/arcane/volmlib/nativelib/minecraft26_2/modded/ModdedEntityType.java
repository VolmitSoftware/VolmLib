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

import art.arcane.volmlib.nativelib.entity.NativeEntityType;
import net.minecraft.world.entity.EntityType;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedEntityType implements NativeEntityType {
    private static final ConcurrentHashMap<String, ModdedEntityType> CACHE = new ConcurrentHashMap<>();

    private final EntityType<?> type;
    private final String key;
    private final String namespace;

    private ModdedEntityType(EntityType<?> type, String key) {
        this.type = type;
        this.key = key;
        int colon = key.indexOf(':');
        this.namespace = colon >= 0 ? key.substring(0, colon) : "minecraft";
    }

    public static ModdedEntityType of(EntityType<?> type, String key) {
        return CACHE.computeIfAbsent(key, (String k) -> new ModdedEntityType(type, k));
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
    public String spawnCategory() {
        return type.getCategory().getSerializedName().toLowerCase(Locale.ROOT);
    }

    @Override
    public Object nativeHandle() {
        return type;
    }
}
