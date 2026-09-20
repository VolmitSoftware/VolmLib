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

package art.arcane.volmlib.nativelib.modded;

/**
 * Plain flag holder every Iris mixin marks from its injected body. It is deliberately free of any
 * server, client or loader reference so the client-dist mixins in art.arcane.iris.client.mixin can
 * write to it without dragging client types into modded-common.
 *
 * <p>These flags say "the injected code ran at least once", which is weaker than "the mixin was applied":
 * a hook only fires when its target path executes. The loader boot audit is what proves application at
 * boot; these are the runtime confirmation reported alongside it.
 */
public final class NativeMixinFlags {
    private static volatile boolean entityPersistenceRan;
    private static volatile boolean livingEntityLootRan;
    private static volatile boolean mobAwarenessRan;
    private static volatile boolean structureTemplatePaletteRan;
    private static volatile boolean worldOpenFlowsRan;
    private static volatile boolean worldTypeEntryRan;

    private NativeMixinFlags() {
    }

    // Read before write on every marker: the entity hooks run per entity save and per mob tick, and a
    // volatile store is a cache-line invalidation on every core that reads the flag. The flags are one-way,
    // so the guarded write costs one plain-ish read once set and races only ever re-store the same value.
    public static void markEntityPersistence() {
        if (!entityPersistenceRan) {
            entityPersistenceRan = true;
        }
    }

    public static void markLivingEntityLoot() {
        if (!livingEntityLootRan) {
            livingEntityLootRan = true;
        }
    }

    public static void markMobAwareness() {
        if (!mobAwarenessRan) {
            mobAwarenessRan = true;
        }
    }

    public static void markStructureTemplatePalette() {
        if (!structureTemplatePaletteRan) {
            structureTemplatePaletteRan = true;
        }
    }

    public static void markWorldOpenFlows() {
        if (!worldOpenFlowsRan) {
            worldOpenFlowsRan = true;
        }
    }

    public static void markWorldTypeEntry() {
        if (!worldTypeEntryRan) {
            worldTypeEntryRan = true;
        }
    }

    public static boolean entityPersistenceRan() {
        return entityPersistenceRan;
    }

    public static boolean livingEntityLootRan() {
        return livingEntityLootRan;
    }

    public static boolean mobAwarenessRan() {
        return mobAwarenessRan;
    }

    public static boolean structureTemplatePaletteRan() {
        return structureTemplatePaletteRan;
    }

    public static boolean worldOpenFlowsRan() {
        return worldOpenFlowsRan;
    }

    public static boolean worldTypeEntryRan() {
        return worldTypeEntryRan;
    }

    public static void reset() {
        entityPersistenceRan = false;
        livingEntityLootRan = false;
        mobAwarenessRan = false;
        structureTemplatePaletteRan = false;
        worldOpenFlowsRan = false;
        worldTypeEntryRan = false;
    }
}
