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

package art.arcane.volmlib.nativelib.minecraft26_2.forge;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockDropHooks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

public final class NativeForgeBlockLootModifier extends LootModifier {
    public static final MapCodec<NativeForgeBlockLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, NativeForgeBlockLootModifier::new));

    public NativeForgeBlockLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(LootTable table, ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Entity breaker = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        BlockState state = context.getOptionalParameter(LootContextParams.BLOCK_STATE);
        Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
        if (!(breaker instanceof Player) || state == null || origin == null) {
            return generatedLoot;
        }
        ServerLevel level = context.getLevel();
        BlockPos position = BlockPos.containing(origin);
        NativeBlockDropHooks.Result result = NativeBlockDropHooks.completePrepared(level, position);
        if (result == null) {
            return generatedLoot;
        }
        if (result.routeCombinedDrops(generatedLoot)) {
            generatedLoot.clear();
            return generatedLoot;
        }
        if (result.replaceVanillaDrops()) {
            generatedLoot.clear();
        }
        generatedLoot.addAll(result.drops());
        return generatedLoot;
    }
}
