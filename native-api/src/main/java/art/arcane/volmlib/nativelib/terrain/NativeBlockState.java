/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.volmlib.nativelib.terrain;

/**
 * Neutral handle for a resolved block state; the canonical key is the config currency and the native handle is adapter-owned.
 * <p>
 * Instances are immutable value handles, interned by the adapter, and read from generation threads in bulk -
 * every predicate here must be a cached field read or cheap computation, never a registry or world lookup.
 * Compare with {@link #matches(NativeBlockState)} rather than {@code equals}; only interned singletons such as
 * {@code air()} are safe to compare by identity.
 * <p>
 * Shared native platform value contract.
 *
 */
public interface NativeBlockState {
    /**
     * Canonical {@code namespace:path[prop=value,...]} key, the form packs and objects store. Never null.
     */
    String key();

    /**
     * Namespace half of {@link #key()}, for example {@code minecraft}. Never null.
     */
    String namespace();

    /**
     * The key with any property block stripped, memoized by implementors that intern their states.
     * Returning null means "not memoized"; callers must then derive it from {@link #key()}.
     */
    default String materialKey() {
        return null;
    }

    /**
     * Whether this is air, including cave and void air.
     */
    boolean isAir();

    /**
     * Whether the host treats this as a solid collision block.
     */
    boolean isSolid();

    /**
     * Whether this blocks light fully. Drives Iris's own light and surface reasoning.
     */
    boolean isOccluding();

    /**
     * Whether this state came from a custom-content provider rather than a vanilla registry entry. Custom
     * states carry a {@link #deferredPlacementKey()} and a {@link #placementBaseState()}.
     */
    boolean isCustom();

    /**
     * For a custom state, the provider key to hand back after the block is written, so the provider can finish
     * placement once the chunk exists. Null for ordinary states.
     */
    default String deferredPlacementKey() {
        return null;
    }

    /**
     * The vanilla state to actually write for a custom state - the placeholder the provider later replaces.
     * Returns {@code this} for ordinary states. Never null.
     */
    default NativeBlockState placementBaseState() {
        return this;
    }

    /**
     * Whether this is a fluid block, water or lava.
     */
    boolean isFluid();

    /**
     * Whether this is water specifically.
     */
    boolean isWater();

    /**
     * Whether this state carries a {@code waterlogged=true} property.
     */
    boolean isWaterLogged();

    /**
     * Whether this state carries a {@code lit=true} property.
     */
    boolean isLit();

    /**
     * Whether the host needs a block update after this is placed - stairs, fences, redstone and other states
     * that resolve their shape from neighbours.
     */
    boolean isUpdatable();

    /**
     * Whether this is plant foliage: grass, ferns, flowers and similar decoration.
     */
    boolean isFoliage();

    /**
     * Whether this is part of a tree - tagged as a log or as leaves.
     */
    boolean isTreeBlock();

    /**
     * Whether foliage can be planted on top of this block.
     */
    boolean isFoliagePlantable();

    /**
     * Whether this is a decorant: a thin block that sits on a surface and cannot support anything.
     */
    boolean isDecorant();

    /**
     * Whether this block has an inventory Iris can fill from a loot table.
     */
    boolean isStorage();

    /**
     * Whether this is specifically a chest, which needs the pairing and orientation handling chests require.
     */
    boolean isStorageChest();

    /**
     * Whether this is an ore block, and therefore a candidate for
     * {@code deepSlateOre(NativeBlockState, NativeBlockState)}.
     */
    boolean isOre();

    /**
     * Whether this is deepslate or a deepslate variant.
     */
    boolean isDeepSlate();

    /**
     * Whether this is a vine-like block that attaches to a face and hangs.
     */
    boolean isVineBlock();

    /**
     * Whether this block can be placed onto {@code onto} - the support check Iris runs before writing
     * decoration. Passing a state from a different adapter fails at runtime.
     */
    boolean canPlaceOnto(NativeBlockState onto);

    /**
     * Whether the two states are the same block with the same properties. Prefer this to {@code equals}, which
     * is identity-based on some adapters.
     */
    boolean matches(NativeBlockState state);

    /**
     * Convenience for the common "nothing solid here" test.
     */
    default boolean isAirOrFluid() {
        return isAir() || isFluid();
    }

    /**
     * Whether the host attaches a block entity to this block - signs, chests, spawners and similar. Such blocks
     * need their tile data applied after the block write.
     */
    boolean hasTileEntity();

    /**
     * This state with one property overridden, merged into {@link #key()} and re-resolved. Never mutates the
     * receiver.
     * <p>
     * Strict: a property name or value the block does not accept throws rather than being dropped. Validate
     * against {@code blockStateProperties()} first, or resolve the full key through
     * {@code blockOrNull(String)} instead.
     */
    NativeBlockState withProperty(String name, String value);

    /**
     * The adapter's backing state object - {@code org.bukkit.block.data.BlockData} on Bukkit,
     * {@code BlockState} on a mod loader. Never null. Only code inside the owning adapter may cast it.
     */
    Object nativeHandle();

    default Object placementHandle() {
        return placementBaseState().nativeHandle();
    }
}
